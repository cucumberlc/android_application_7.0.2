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
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.textfield.TextInputLayout
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ProvisioningFragmentBase
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbp.CertificatesSelectionFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbpRecords.ProvisioningRecordsFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary.ProvisioningViewModel.ProvisioningState
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary.Provisioning_nlc.Companion.EMPTY_STRING
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogLoadingBinding
import com.siliconlabs.bluetoothmesh.databinding.FragmentProvisioningBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProvisioningFragment : ProvisioningFragmentBase(R.layout.fragment_provisioning) {
    private val viewModel: ProvisioningViewModel by viewModels()
    private var subnetPosition = 1
    private var cbpSupported = false

    private val layout by viewBinding(FragmentProvisioningBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (deviceToProvisionIsValid()) {
            setLoadingDialogInitialize()
            DeviceFunctionalityDb.saveTab(false)
            collectMessages(viewModel.messageFlow)
            setupBackPressedBehaviour()
            setupDeviceName()
            setupSubnetSelection()
            setupCertificateBasedProvisioningLayout()
            setupProvisionButton()
            setupProvisioningState()
            println("Non-NLC status: cbpSupported$cbpSupported")
            if (cbpSupported) {
                loadingDialog?.closeAlertDialog()
                return
            } else {

            }
        }
    }

    override fun isProvisioningActive() =
        viewModel.provisioningState.value == ProvisioningState.ACTIVE

    private fun setupDeviceName() {
        layout.apply {
            requireMainActivity().setActionBar(EMPTY_STRING)
            requireMainActivity().supportActionBar!!.setDisplayHomeAsUpEnabled(false)
            requireMainActivity().supportActionBar!!.setDisplayShowHomeEnabled(false)
            // requireMainActivity().hideBackImagePressOnFragments()
            textViewDeviceName.setText(viewModel.deviceName.value)

            if (viewModel.isNameChangeSupported) {
                textViewDeviceName.doAfterTextChanged {
                    viewModel.setDeviceName(it?.toString() ?: "")
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
                val filteredSubnetIndices = viewModel.availableSubnets
                val subnetsAdapter = ArrayAdapter(
                    requireContext(),
                    R.layout.spinner_item_dark,
                    filteredSubnetIndices.drop(1).map { it.netKey.index }
                )
                subnets.setAdapter(subnetsAdapter)
                viewModel.selectSubnetByIndex(subnetPosition)
                subnets.setOnItemClickListener { _, _, position, _ ->
                    subnetPosition = position + 1
                    viewModel.selectSubnetByIndex(subnetPosition)
                    layout.subnets.setText(subnetPosition.toString(), false)
                }
            }
        }

        viewLifecycleScope.launch {
            viewModel.selectedSubnet.collectLatest {
                layout.subnets.setText(subnetPosition.toString(), false)
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
                    cbpSupported = isCbpEnabled
                    provisionButton.visibility = View.VISIBLE
                    progressIndicator.visibility = View.GONE
                    textViewDeviceName.visibility = View.VISIBLE
                    textInputLayoutSubnets.visibility = View.VISIBLE
                    subnets.visibility = View.VISIBLE
                    deviceNameLayout.visibility = View.VISIBLE
                    layoutCbp.visibility = View.VISIBLE
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
            setupProvisioningState()
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
                    progressIndicator.isInvisible = isReady
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

    private fun onProvisioningSuccess() {
        println("Non-NLC Pro Success")
        MeshToast.show(requireContext(), getString(R.string.provisioning_completed))
        val deviceFragment = DeviceFragment.firstConfigurationInstance(viewModel.provisionedDevice)
        requireMainActivity().showFragment(deviceFragment)
    }

    private fun navigateToProvisioningRecordsFragment() {
        viewModel.updateDeviceToProvision()
        requireMainActivity().showFragment(ProvisioningRecordsFragment())
    }

    private fun navigateToCertificatesSelectionFragment() {
        viewModel.updateDeviceToProvision()
        requireMainActivity().showFragment(CertificatesSelectionFragment())
    }

    private fun setLoadingDialogInitialize() {
        loadingDialogLayout = DialogLoadingBinding.inflate(layoutInflater)
        val builder = CustomAlertDialogBuilder(requireContext()).apply {
            setView(loadingDialogLayout.root)
            setCancelable(false)
            setPositiveButton(this@ProvisioningFragment.getString(R.string.dialog_positive_ok)) { dialog, _ ->
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

    private val delayMillis = 1500 // 2 second
    override fun onResume() {
        super.onResume()
        viewModel.selectSubnetByIndex(1)
        if (cbpSupported) {
            loadingDialog?.closeAlertDialog()
            return
        } else {
            viewModel.provisionDevice()
            showLoadingDialog("Provisioning \nPlease wait", false)
            Handler(Looper.getMainLooper()).postDelayed({
                loadingDialog?.closeAlertDialog()
            }, delayMillis.toLong())
        }
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

    private fun AlertDialog.setupCloseButton() {
        getButton(AlertDialog.BUTTON_POSITIVE).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                // deviceConfigPresenter.abandonTasks()
                dismiss()
            }
        }
    }

    private fun AlertDialog.closeAlertDialog() {
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadingDialog?.closeAlertDialog()
    }
}
