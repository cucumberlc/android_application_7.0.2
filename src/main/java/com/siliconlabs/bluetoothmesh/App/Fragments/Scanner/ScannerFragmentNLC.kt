/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scanner

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.SimpleItemAnimator
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Dialogs.DisconnectionDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.Adapters.ScannedDevicesAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigNLCPresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigView

import com.siliconlabs.bluetoothmesh.App.Fragments.NLC.NLCScanProvisioViewModel
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningHelper
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Models.DeviceConfig
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.collectMessages
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.launchAndRepeatWhenResumed
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.showErrorDialog
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.showOnce
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogLoadingBinding
import com.siliconlabs.bluetoothmesh.databinding.FragmentScannerNlcBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import javax.inject.Inject

@AndroidEntryPoint
class ScannerFragmentNLC constructor(


):Fragment(R.layout.fragment_scanner_nlc), DeviceConfigView {

    private val layout by viewBinding(FragmentScannerNlcBinding::bind)

    private val viewModel: NLCScanProvisioViewModel by viewModels()

    private val adapter = ScannedDevicesAdapter()
    private var scanMenu: MenuItem? = null
    @Inject
    lateinit var networkConnectionLogic: NetworkConnectionLogic
    var  nlcDeviceconfigpresenter: DeviceConfigNLCPresenter? =null

    private lateinit var loadingDialogLayout: DialogLoadingBinding
    private var loadingDialog: AlertDialog? = null

    private val disconnectionDialog by lazy {
        DisconnectionDialog { requireActivity().supportFragmentManager.popBackStack() }
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_scan_screen_toolbar, menu)
            scanMenu = menu.findItem(R.id.scan_menu)
            scanMenu?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.scan_menu) {
                if (!viewModel.isScanning()) viewModel.startScan()
                else viewModel.stopScan()
                return true
            }
            return false
        }

        override fun onPrepareMenu(menu: Menu) {
            setCurrentMenuState(viewModel.scanState.value)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadingDialogLayout = DialogLoadingBinding.inflate(layoutInflater)
        clearPreviousProvisionableDevices()
        collectMessages(viewModel, getString(R.string.error_message_title_scan_interrupted))
        setUpActionBarTitle()
        setUpScanMenu()
        setUpRecyclerView()
        setUpNoDevicesDisplay()
        setUpInvalidStateDialog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanMenu = null
    }

    private fun clearPreviousProvisionableDevices() {
        AppState.deviceToProvision = null
    }

    private fun setUpActionBarTitle() {
        if (viewModel.isRemoteScan) {
            requireMainActivity().setActionBar(getString(R.string.remote_scanning_appbar_title))
        }
    }

    private fun setUpScanMenu() {
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.scanState.collect(::setCurrentMenuState)
        }
    }

    private fun setCurrentMenuState(scannerState: DeviceScanner.ScannerState) {
        scanMenu?.setTitle(
            when (scannerState) {
                DeviceScanner.ScannerState.SCANNING -> R.string.device_scanner_turn_off_scan
                else -> R.string.device_scanner_turn_on_scan
            }
        )?.isEnabled = scannerState !is DeviceScanner.ScannerState.InvalidState
    }

    private fun setUpRecyclerView() {
        layout.recyclerViewScannedDevices.apply {
            adapter = this@ScannerFragmentNLC.adapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
        adapter.onScannedDeviceClick = ::onScannedDeviceClick

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.scanResults
                .onCompletion { adapter.submitList(null) }
                .collectLatest {
                    adapter.submitList(it)
                }
        }
    }

    private fun setUpNoDevicesDisplay() {
        layout.placeholder.isVisible = true

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            combine(viewModel.scanState, viewModel.scanResults) { state, results ->
                val showEmptyView = if (results.isEmpty()) {
                    layout.tvEmptyScanActionHint.setText(
                        when (state) {
                            DeviceScanner.ScannerState.SCANNING -> R.string.scanner_adapter_empty_list_message_scanning
                            else -> R.string.scanner_adapter_empty_list_message_idle
                        }
                    )
                    true
                } else false
                layout.placeholder.isVisible = showEmptyView
            }.onCompletion {
                layout.apply {
                    tvEmptyScanActionHint.setText(R.string.scanner_adapter_empty_list_message_idle)
                    placeholder.isVisible = true
                }
            }.collect()
        }
    }

    private fun setUpInvalidStateDialog() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            var isScanning = false
            viewModel.scanState.collect { state ->
                when (state) {
                    // maybe update this dialog and add reconnection button/hints?
                    // remote scanner does recover and work in that case
                    DeviceScanner.ScannerState.NO_BLUETOOTH, DeviceScanner.ScannerState.NO_NETWORK -> {
                        if (isScanning || viewModel.isRemoteScan)
                            disconnectionDialog.showOnce(childFragmentManager)
                    }
                    else -> if (disconnectionDialog.isAdded) disconnectionDialog.dismiss()
                }
                isScanning = state == DeviceScanner.ScannerState.SCANNING
            }
        }
    }

    private fun onScannedDeviceClick(unprovisionedDevice: UnprovisionedDevice) {
        if (viewModel.defaultSubnet() == null) {
            // cannot provision device into nothing
            showErrorDialog(Message.error(R.string.scanner_adapter_no_subnet_on_provisioning))
            return
        }
        navigateToDeviceProvisioning(viewModel.selectDevice(unprovisionedDevice))
    }

    private val handler = Handler(Looper.getMainLooper())

    private fun createLoadingDialog(): AlertDialog {
        val builder = CustomAlertDialogBuilder(requireContext()).apply {
            setView(loadingDialogLayout.root)
            setCancelable(false)
            setPositiveButton(this@ScannerFragmentNLC.getString(R.string.dialog_positive_ok)) { dialog, _ ->
                dialog.dismiss()
            }
        }
        loadingDialogLayout.root.apply {
            (parent as? ViewGroup)?.removeView(this)
        }
        return builder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
        }
    }
    private fun navigateToDeviceProvisioning(deviceToProvision: DeviceToProvision) {
        // app state still exists here because deviceToProvision cannot be parcelized
        AppState.deviceToProvision = deviceToProvision
        //requireMainActivity().showFragment(ProvisioningFragment())
      //  loadingDialog=createLoadingDialog()

        viewModel.provisionDevice(deviceToProvision)

        viewModel.provisionResultLiveData.observe(viewLifecycleOwner) { result ->
            // Do something with the result in your Fragment
            if (result is ProvisioningHelper.Success) {
                MeshToast.show(requireContext(), getString(R.string.provisioning_completed))
                val meshNode = result.meshNode
                println("Mesh Node= "+meshNode.functionality.getAllModels())
                // Now you can use `meshNode` as needed
                println("Size= "+meshNode.node.subnets.size)
                val appKeysInSubnet = meshNode.node.subnets.first().appKeys.sortedBy { it.index }
                nlcDeviceconfigpresenter= DeviceConfigNLCPresenter(networkConnectionLogic,meshNode)

                nlcDeviceconfigpresenter!!.processChangeAppKey(appKeysInSubnet[0])

                handler.postDelayed({
                    // Your code to execute after the delay
                    println("Delayed execution after 5 seconds")
                    val supportedFunctionalities =
                        DeviceFunctionality.getFunctionalitiesNamed(meshNode.node).toMutableList()
                            .sortedBy { it.functionalityName }
                    val functionalitiesNames = supportedFunctionalities.map { it.functionalityName }
                    val functionalitiesAdapter =
                        ArrayAdapter(requireContext(), R.layout.spinner_item_dark, functionalitiesNames)
                    val initialFunctionalityName =
                        if (meshNode.functionality != DeviceFunctionality.FUNCTIONALITY.Unknown) {
                            supportedFunctionalities.indexOfFirst { it.functionality == meshNode.functionality }
                                .takeUnless { it == -1 }
                                ?.let { index -> functionalitiesNames[index] } ?: functionalitiesNames.first()
                        } else {
                            functionalitiesNames.first()
                        }
                    nlcDeviceconfigpresenter!!.processChangeFunctionality(supportedFunctionalities[1].functionality)
                }, 10000)

            }
        }

    }

    override fun setDeviceConfig(
        meshNode: MeshNode,
        deviceConfig: DeviceConfig,
        appKeysInSubnet: List<AppKey>,
        nodes: List<Node>,
    ) {
        TODO("Not yet implemented")
    }

    override fun showToast(message: DeviceConfigView.ToastMessage) {
        activity?.runOnUiThread {
            val stringResource = when (message) {
                DeviceConfigView.ToastMessage.ERROR_MISSING_APPKEY -> getString(R.string.device_config_select_appkey_first)
                DeviceConfigView.ToastMessage.POLL_TIMEOUT_UPDATED -> getString(R.string.device_config_poll_timeout_updated)
                DeviceConfigView.ToastMessage.POLL_TIMEOUT_NOT_FRIEND -> getString(R.string.device_config_poll_timeout_not_friend,
                    "Mesh Node Name"
                )
                DeviceConfigView.ToastMessage.LPN_TIMEOUT_WRONG_RANGE -> getString(R.string.device_config_lpn_timeout_wrong_range)
            }
            MeshToast.show(requireContext(), stringResource)
        }
    }

    override fun showLoadingDialog() {
        activity?.runOnUiThread {
            if (loadingDialog?.isShowing == true) {
                return@runOnUiThread
            }

            val builder = CustomAlertDialogBuilder(requireContext()).apply {
                setView(loadingDialogLayout.root)
                setCancelable(false)
                setPositiveButton(this@ScannerFragmentNLC.getString(R.string.dialog_positive_ok)) { dialog, _ ->
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
    }

    override fun setLoadingDialogMessage(message: String, showCloseButton: Boolean) {
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
                nlcDeviceconfigpresenter!!.abandonTasks()
                dismiss()
            }
        }
    }

    override fun setLoadingDialogMessage(error: NodeControlError, showCloseButton: Boolean) {
        activity?.let {
            setLoadingDialogMessage(error.toString(), showCloseButton)
        }
    }

    override fun setLoadingDialogMessage(
        loadingMessage: DeviceConfigView.LoadingDialogMessage,
        message: String,
    ) {
        setLoadingDialogMessage(getMessageResource(loadingMessage, message))
    }

    override fun setLoadingDialogMessage(
        loadingMessage: DeviceConfigView.LoadingDialogMessage,
        message: String,
        leftTasksCount: Int,
        allTasksCount: Int,
    ) {
        val doneTasksCount = allTasksCount - leftTasksCount
        val stepsCount = "$doneTasksCount/$allTasksCount\n"
        setLoadingDialogMessage(stepsCount.plus(getMessageResource(loadingMessage, message)))
    }

    override fun showRetryButton() {
        activity?.runOnUiThread {
            loadingDialog?.takeIf { it.isShowing }?.apply {
                getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                    visibility = View.VISIBLE
                    text = context.getString(R.string.dialog_retry)
                    setOnClickListener {
                        nlcDeviceconfigpresenter!!.retryTask()
                    }
                }
            }
        }
    }

    override fun dismissLoadingDialog() {
        activity?.runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
        }
    }

    override fun showDisableProxyAttentionDialog(onClickListener: DialogInterface.OnClickListener) {
        val builder = CustomAlertDialogBuilder(requireContext())
        builder.apply {
            setTitle(getString(R.string.device_config_proxy_disable_attention_title))
            setMessage(getString(R.string.device_config_proxy_disable_attention_message))
            setPositiveButton(getString(R.string.dialog_positive_ok)) { dialog, _ ->
                onClickListener.onClick(dialog, AlertDialog.BUTTON_POSITIVE)
            }
            setCancelable(false)
            setNegativeButton(getString(R.string.dialog_negative_cancel)) { dialog, _ ->
                onClickListener.onClick(dialog, AlertDialog.BUTTON_NEGATIVE)
            }
        }

        val dialog = builder.create()
        dialog.apply {
            show()
        }
    }

    override fun promptGlobalTimeout(timeout: Int) {
       // layout.editTextGlobalTimeoutSecs.setText(timeout.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModelStore.clear()
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    private fun getMessageResource(
        loadingMessage: DeviceConfigView.LoadingDialogMessage,
        message: String,
    ): String {
        return activity?.let {
            when (loadingMessage) {
                DeviceConfigView.LoadingDialogMessage.CONFIG_ADDING_APPKEY_TO_NODE -> it.getString(R.string.device_config_adding_appkey)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_REMOVING_APPKEY_FROM_NODE -> it.getString(R.string.device_config_removing_appkey)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_PROXY_ENABLING -> it.getString(R.string.device_config_proxy_enabling)
                DeviceConfigView.LoadingDialogMessage.CONFIG_PROXY_DISABLING -> it.getString(R.string.device_config_proxy_disabling)
                DeviceConfigView.LoadingDialogMessage.CONFIG_PROXY_GETTING -> it.getString(R.string.device_config_proxy_getting)

                DeviceConfigView.LoadingDialogMessage.CONFIG_MODEL_ADDING -> it.getString(R.string.device_config_model_adding)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_MODEL_REMOVING -> it.getString(R.string.device_config_model_removing)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_SUBSCRIPTION_ADDING -> it.getString(R.string.device_config_subscription_adding)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_SUBSCRIPTION_REMOVING -> it.getString(R.string.device_config_subscription_removing)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_PUBLICATION_SETTING -> it.getString(R.string.device_config_publication_setting)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_PUBLICATION_CLEARING -> it.getString(R.string.device_config_publication_clearing)
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_FUNCTIONALITY_CHANGING -> it.getString(
                    R.string.device_config_functionality_changing
                )

                DeviceConfigView.LoadingDialogMessage.CONFIG_FRIEND_ENABLING -> it.getString(R.string.device_config_friend_enabling)
                DeviceConfigView.LoadingDialogMessage.CONFIG_FRIEND_DISABLING -> it.getString(R.string.device_config_friend_disabling)
                DeviceConfigView.LoadingDialogMessage.CONFIG_FRIEND_GETTING -> it.getString(R.string.device_config_friend_getting)

                DeviceConfigView.LoadingDialogMessage.CONFIG_RETRANSMISSION_ENABLING -> it.getString(
                    R.string.device_config_retransmission_enabling
                )
                DeviceConfigView.LoadingDialogMessage.CONFIG_RETRANSMISSION_DISABLING -> it.getString(
                    R.string.device_config_retransmission_disabling
                )
                DeviceConfigView.LoadingDialogMessage.CONFIG_RETRANSMISSION_GETTING -> it.getString(
                    R.string.device_config_retransmission_getting
                )

                DeviceConfigView.LoadingDialogMessage.CONFIG_RELAY_ENABLING -> it.getString(R.string.device_config_relay_enabling)
                DeviceConfigView.LoadingDialogMessage.CONFIG_RELAY_DISABLING -> it.getString(R.string.device_config_relay_disabling)
                DeviceConfigView.LoadingDialogMessage.CONFIG_RELAY_GETTING -> it.getString(R.string.device_config_relay_getting)

                DeviceConfigView.LoadingDialogMessage.CONFIG_POLL_TIMEOUT_GETTING -> it.getString(R.string.device_config_poll_timeout_getting)

                DeviceConfigView.LoadingDialogMessage.CONFIG_LPN_TIMEOUT_SETTING -> it.getString(R.string.device_config_lpn_timeout_setting)
                DeviceConfigView.LoadingDialogMessage.CONFIG_LPN_TIMEOUT_GETTING -> it.getString(R.string.device_config_lpn_timeout_getting)

                DeviceConfigView.LoadingDialogMessage.CONFIG_DCD_GETTING -> getString(R.string.device_config_dcd_getting)

                DeviceConfigView.LoadingDialogMessage.CONFIG_AE_SETTING_CONFIGURATION -> it.getString(
                    R.string.device_config_AE_setting_configuration
                )
                DeviceConfigView.LoadingDialogMessage.CONFIG_AE_SETTING_PDU -> it.getString(R.string.device_config_AE_setting_pdu)
                DeviceConfigView.LoadingDialogMessage.CONFIG_AE_ENABLING_ON_BLOB_CLIENT_MODEL -> it.getString(
                    R.string.device_config_AE_enabling
                )
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_AE_DISABLING_ON_BLOB_CLIENT_MODEL -> it.getString(
                    R.string.device_config_AE_disabling
                )
                    .format(message)
                DeviceConfigView.LoadingDialogMessage.CONFIG_AE_GETTING_ON_BLOB_CLIENT_MODEL -> it.getString(
                    R.string.device_config_AE_getting
                )
                    .format(message)
            }
        } ?: ""
    }
}
