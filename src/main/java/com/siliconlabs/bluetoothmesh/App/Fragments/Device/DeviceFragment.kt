/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigFragment.Companion.KEY_IS_NLC_CONTROL
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Info.DeviceInfoFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DeviceScreenBinding
import com.siliconlabs.bluetoothmesh.databinding.DialogLoadingBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeviceFragment
@Deprecated(
    "use newInstance(meshNode, subnet) or firstConfigurationInstance(MeshNode)"
)
constructor() : Fragment(R.layout.device_screen), DeviceView {
    companion object {
        const val KEY_IS_FIRST_CONFIG = "KEY_IS_FIRST_CONFIG"
        const val KEY_IS_LAUNCHED_FROM_PROVISION_NON_NLC = "KEY_IS_PROV_NON_NLC"
        const val KEY_IS_LAUNCHED_FROM_SUBNET_NON_NLC = "KEY_IS_SUBNET_NON_NLC"
        const val KEY_IS_AUTO_SELECT_FUNCTIONALITY = "KEY_IS_AUTO_SELECT_FUNCTIONALITY"

        fun firstConfigurationInstance(meshNode: MeshNode) =
            @Suppress("DEPRECATION")
            DeviceFragment().withMeshNavArg(meshNode.node.toNavArg()).apply {
                arguments!!.putBoolean(KEY_IS_FIRST_CONFIG, true)
                arguments!!.putBoolean(KEY_IS_LAUNCHED_FROM_PROVISION_NON_NLC, true)
                arguments!!.putBoolean(KEY_IS_AUTO_SELECT_FUNCTIONALITY, true)
            }

        fun newInstance(meshNode: MeshNode, subnet: Subnet, flag: Boolean, isNLC:Boolean) =
            @Suppress("DEPRECATION")
            DeviceFragment().withMeshNavArg(meshNode.node.toNavArg(subnet)).apply {
                arguments!!.putBoolean(KEY_IS_FIRST_CONFIG, false)
                arguments!!.putBoolean(KEY_IS_LAUNCHED_FROM_SUBNET_NON_NLC, flag)
                arguments!!.putBoolean(KEY_IS_NLC_CONTROL, isNLC)
            }
    }

    private val devicePresenter: DevicePresenter by presenters()

    private lateinit var dialogLoadingBinding: DialogLoadingBinding
    private var loadingDialog: AlertDialog? = null

    private val layout by viewBinding(DeviceScreenBinding::bind)
    private var backPressed = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceFunctionalityDb.saveTab(false)
        setUpBackPress()
        setUpToolbar()
        setUpLoadingDialog()
    }

    fun setUpBackPress() {
        requireMainActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            devicePresenter.disconnectFromSubnet()
            backPressed = true
            if (shouldClearBackStack()) {
                requireMainActivity().popWholeBackStack()
            } else if (shouldOmitProvisioningFragment()) {
                returnToMainFragment()
            } else {
                requireActivity().supportFragmentManager.popBackStack()
            }
        }
    }

    fun setUpBackPressWithoutLifeCycleAware() {

        devicePresenter.disconnectFromSubnet()
        backPressed = true
        if (shouldClearBackStack()) {
            requireMainActivity().popWholeBackStack()
        } else if (shouldOmitProvisioningFragment()) {
            returnToMainFragment()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun setUpLoadingDialog() {
        dialogLoadingBinding = DialogLoadingBinding.inflate(layoutInflater)
        layout.apply {
            if (requireArguments().getBoolean(KEY_IS_LAUNCHED_FROM_PROVISION_NON_NLC)) {
                layout.tabLayout.visibility = View.GONE
            } else {
                layout.tabLayout.visibility = View.VISIBLE
            }

            viewPager.adapter = DevicePageAdapter()
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.device_dialog_config_page_title)
                    else -> getString(R.string.device_dialog_info_page_title)
                }
            }.attach()
        }
    }

    private fun setUpToolbar() {
        //requireMainActivity().setActionBar(devicePresenter.meshNode.node.name)
    }

    private fun shouldClearBackStack() =
        devicePresenter.isFirstConfiguration && AppState.isProcessingDeviceDirectly()

    private fun shouldOmitProvisioningFragment() =
        devicePresenter.isFirstConfiguration && AppState.isProcessingDeviceRemotely()

    override fun onDestroy() {
        loadingDialog?.takeIf { it.isShowing }?.dismiss()
        super.onDestroy()
    }

    override fun showLoadingDialog() {
        activity?.runOnUiThread {
            if (loadingDialog?.isShowing == true || backPressed) {
                return@runOnUiThread
            }

            val builder = CustomAlertDialogBuilder(requireContext())
            builder.apply {
                setView(dialogLoadingBinding.root)
                setCancelable(false)
                setPositiveButton(this@DeviceFragment.getString(R.string.dialog_positive_ok)) { _, _ ->
                    if (shouldClearBackStack()) {
                        requireMainActivity().popWholeBackStack()
                    } else if (shouldOmitProvisioningFragment()) {
                        returnToMainFragment()
                    } else {
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                }
            }

            dialogLoadingBinding.root.apply {
                (parent as? ViewGroup)?.removeView(this)
            }

            loadingDialog = builder.create()
            loadingDialog?.apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
            }
        }
    }

    private fun returnToMainFragment() {
        requireActivity().supportFragmentManager.apply {
            popBackStack()
            popBackStack()
        }
    }

    private fun setLoadingDialogMessage(
        message: String,
        showCloseButton: Boolean = false,
        closeFragmentOnClick: Boolean = false,
    ) {
        activity?.runOnUiThread {
            loadingDialog?.apply {
                if (!isShowing) {
                    return@runOnUiThread
                }
                dialogLoadingBinding.loadingText.text = message

                if (showCloseButton) {
                    dialogLoadingBinding.loadingIcon.visibility = View.GONE
                    getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.VISIBLE
                }
                if (closeFragmentOnClick) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        dismiss()
                        activity?.supportFragmentManager?.popBackStack()
                    }
                }
            }
        }
    }

    override fun setLoadingDialogMessage(connectionError: ConnectionError) {
        setLoadingDialogMessage(connectionError.toString(), true)
    }

    override fun setLoadingDialogMessage(loadingMessage: DeviceView.LOADING_DIALOG_MESSAGE) {
        when (loadingMessage) {
            DeviceView.LOADING_DIALOG_MESSAGE.CONFIG_CONNECTING -> setLoadingDialogMessage(
                getString(
                    R.string.device_config_connecting
                ), false
            )

            DeviceView.LOADING_DIALOG_MESSAGE.CONFIG_DISCONNECTED -> setLoadingDialogMessage(
                getString(R.string.device_config_disconnected),
                true
            )
        }
    }

    override fun dismissLoadingDialog() {
        activity?.runOnUiThread {
            loadingDialog?.apply {
                dismiss()
                loadingDialog = null
            }
        }
    }

    private inner class DevicePageAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 2

        override fun createFragment(position: Int): Fragment {
            val flag = arguments?.getBoolean(KEY_IS_LAUNCHED_FROM_SUBNET_NON_NLC)
            val autoConnect = arguments?.getBoolean(KEY_IS_AUTO_SELECT_FUNCTIONALITY)
            val isNLC = arguments!!.getBoolean(KEY_IS_NLC_CONTROL)
            println("Non-NLC")
            return when (position) {
                0 -> DeviceConfigFragment.newInstance(
                    devicePresenter.meshNode, flag!!,
                    autoConnect!!, isNLC
                )

                else -> DeviceInfoFragment()
            }
        }
    }
}