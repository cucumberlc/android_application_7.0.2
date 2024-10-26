/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */
package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.textfield.TextInputLayout
import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.FactoryResetCallback
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragmentNLC
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ProvisioningFragmentBase
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbp.CertificatesSelectionFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbpRecords.ProvisioningRecordsFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary.ProvisioningViewModel.ProvisioningState
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogLoadingBinding
import com.siliconlabs.bluetoothmesh.databinding.FragmentProvisioningNlcBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Provisioning_nlc : ProvisioningFragmentBase(R.layout.fragment_provisioning_nlc) {
    private val viewModel: ProvisioningViewModel by viewModels()

    //  private val layout by viewBinding(FragmentProvisioningBinding::bind)
    private val layout by viewBinding(FragmentProvisioningNlcBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (deviceToProvisionIsValid()) {
            collectMessages(viewModel.messageFlow)
            setupBackPressedBehaviour()
            setupDeviceName()
            setupSubnetSelection()
            setupCertificateBasedProvisioningLayout()
            setupProvisionButton()
            setupProvisioningState()
            setLoadingDialogIntialize()
        }
    }

    override fun isProvisioningActive() =
        viewModel.provisioningState.value == ProvisioningState.ACTIVE

    private fun setupDeviceName() {
        layout.apply {
            requireMainActivity().setActionBar(EMPTY_STRING)
            textViewDeviceName.setText(viewModel.deviceName.value)

            if (viewModel.isNameChangeSupported) {
                textViewDeviceName.doAfterTextChanged {
                    viewModel.setDeviceName(it?.toString() ?: EMPTY_STRING)
                }
            } else {
                textViewDeviceName.isEnabled = false
            }
        }

        viewLifecycleScope.launch {
            viewModel.deviceName.collectLatest {
                if (it.isBlank()) {
                    layout.deviceNameLayout.error = getString(
                        R.string.error_message_device_name_cannot_be_blank
                    )
                } else {
                    layout.deviceNameLayout.error = null
                }
            }
        }
    }

    private fun setupSubnetSelection() {
        layout.apply {
            if (!viewModel.isSubnetSelectionSupported) {
                subnets.isEnabled = false
                textInputLayoutSubnets.isEnabled = false
                textInputLayoutSubnets.endIconMode = TextInputLayout.END_ICON_NONE
            } else {
                val subnetsAdapter = ArrayAdapter(
                    requireContext(),
                    R.layout.spinner_item_dark,
                    viewModel.availableSubnets.map { it.netKey.index }
                )
                subnets.setAdapter(subnetsAdapter)
                subnets.setOnItemClickListener { _, _, position, _ ->
                    viewModel.selectSubnetByIndex(position)
                }
            }
        }

        viewLifecycleScope.launch {
            viewModel.selectedSubnet.collectLatest {
                layout.subnets.setText(it.netKey.index.toString(), false)
            }
        }
    }

    private fun setupCertificateBasedProvisioningLayout() {
        layout.apply {
            switchCertificateBasedProvisioning.setOnCheckedChangeListener { _, checked ->
                viewModel.setCbpEnabled(checked)
            }
            wrapperObtainCertificateFromFile.setOnClickListener {
                navigateToCertificatesSelectionFragment()
            }
            wrapperObtainCertificateFromDevice.setOnClickListener {
                navigateToProvisioningRecordsFragment()
            }
        }

        viewLifecycleScope.launch {
            viewModel.cbpState.collect {
                layout.apply {
                    val cbpNotSupported = it == ProvisioningViewModel.CBPState.NOT_SUPPORTED
                    layoutCbp.isGone = cbpNotSupported
                    if (cbpNotSupported) return@collect

                    val isCbpEnabled = it == ProvisioningViewModel.CBPState.ENABLED
                    switchCertificateBasedProvisioning.isChecked = isCbpEnabled
                    wrapperObtainCertificateFromDevice.isVisible = isCbpEnabled
                    wrapperObtainCertificateFromFile.isVisible = isCbpEnabled
                }
            }
        }
    }

    private fun setupProvisionButton() {
        layout.provisionButton.setOnClickListener {
            viewModel.provisionDevice()
        }

        viewLifecycleScope.launch {
            viewModel.provisioningButtonIsEnabled.collectLatest {
                layout.provisionButton.isEnabled = it
            }
        }
    }

    private fun setupProvisioningState() {
        viewLifecycleScope.launch {
            viewModel.provisioningState.collectLatest {
                if (it == ProvisioningState.SUCCESS) {
                    onProvisioningSuccess()
                    return@collectLatest
                }
                val isReady = it == ProvisioningState.READY
                layout.apply {
                    //     progressIndicatorNlc.isInvisible = isReady

                    textViewDeviceName.isEnabled = isReady
                    subnets.isEnabled = isReady
                    switchCertificateBasedProvisioning.isEnabled = isReady
                    textInputLayoutSubnets.endIconMode =
                        if (isReady) TextInputLayout.END_ICON_DROPDOWN_MENU
                        else TextInputLayout.END_ICON_NONE
                }
            }
        }
    }

    private fun removeMeshNode(node: Node) {
        ConfigurationControl(node).factoryReset(object : FactoryResetCallback {
            override fun success() {
                MeshNodeManager.removeMeshNode(node)
//                  deviceListView?.hideProgressBar()
//                  refreshList()
            }

            override fun error(error: NodeControlError) {
                //  deviceListView?.hideProgressBar()
                //  deviceListView?.showDeleteDeviceLocallyDialog(error.toString(), node)
            }
        })
    }

    val array = (14..20).toList().toTypedArray()
    private fun isValueAvailable(arr: Array<Int>, value: Int): Boolean {
        return value in arr
    }

    private fun onProvisioningSuccess() {
        println("Non-NLC onProvisioningSuccess")
        val nlc_pid = viewModel.provisionedDevice.node.deviceCompositionData!!.pid!!
        if (isValueAvailable(array, nlc_pid)) {
            dismissLoadingDialouge()
            val deviceFragment =
                DeviceFragmentNLC.firstConfigurationInstance(viewModel.provisionedDevice)
            requireMainActivity().showFragment(deviceFragment)
        } else {
            println("Non-NLC onProvisioningSuccess Ready to Remove")
            dismissLoadingDialouge()
            removeMeshNode(viewModel.provisionedDevice.node)
            MeshToast.show(requireContext(), "This Node does not support NLC. Please provision only NLC supported Device.")
            requireMainActivity().onBackPressed()
        }
    }

    private fun navigateToProvisioningRecordsFragment() {
        viewModel.updateDeviceToProvision()
        requireMainActivity().showFragment(ProvisioningRecordsFragment())
    }

    private fun navigateToCertificatesSelectionFragment() {
        viewModel.updateDeviceToProvision()
        requireMainActivity().showFragment(CertificatesSelectionFragment())
    }

    private val delayMillis = 1500 // 2 seconds
    override fun onResume() {
        super.onResume()
        viewModel.selectSubnetByIndex(0)
        viewModel.provisionDevice()
        showLoadingDialog("Provisioning \nPlease wait", false)
        Handler(Looper.getMainLooper()).postDelayed({
            // Code to be executed after the delay
            //            viewModel.provisionDevice()
            //            showLoadingDialog("Provisioning \nPlease wait",false)
        }, delayMillis.toLong())
    }

    private lateinit var loadingDialogLayout: DialogLoadingBinding
    private var loadingDialog: AlertDialog? = null
    fun showLoadingDialog(message: String, showCloseButton: Boolean) {
        activity?.runOnUiThread {
            if (loadingDialog?.isShowing == true) {
                loadingDialogLayout.apply {
                    loadingText.text = message
                    if (showCloseButton) {
                        loadingIcon.visibility = View.GONE
                        loadingDialog!!.setupCloseButton()
                    } else {
                        loadingIcon.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun dismissLoadingDialouge() {
        activity?.runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
        }
    }

    private fun AlertDialog.setupCloseButton() {
        getButton(AlertDialog.BUTTON_POSITIVE).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                // deviceConfigPresenter.abandonTasks()
                dismiss()
            }
        }
    }

    private fun setLoadingDialogIntialize() {
        loadingDialogLayout = DialogLoadingBinding.inflate(layoutInflater)
        val builder = CustomAlertDialogBuilder(requireContext()).apply {
            setView(loadingDialogLayout.root)
            setCancelable(false)
            setPositiveButton(this@Provisioning_nlc.getString(R.string.dialog_positive_ok)) { dialog, _ ->
                dialog.dismiss()
            }
        }
        loadingDialogLayout.root.apply {
            (parent as? ViewGroup)?.removeView(this)
        }

        loadingDialog = builder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
        }
    }

    companion object {
        const val EMPTY_STRING = ""
    }
}



