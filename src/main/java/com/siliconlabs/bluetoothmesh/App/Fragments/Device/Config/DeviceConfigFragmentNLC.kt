/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigFragment.Companion.BASIC_LIGHTNESS_CONTROLLER
import com.siliconlabs.bluetoothmesh.App.Fragments.Standalone.StandaloneUpdaterFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper.supportsAdvertisementExtension
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceConfig
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.setOnItemSelectedListenerOnViewCreated
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogLoadingBinding
import com.siliconlabs.bluetoothmesh.databinding.FragmentDeviceConfigBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope

@AndroidEntryPoint
class DeviceConfigFragmentNLC
@Deprecated(
    "use newInstance(MeshNode)",
    replaceWith = ReplaceWith(".newInstance(meshNode)")
)
constructor() : Fragment(R.layout.fragment_device_config_nlc), DeviceConfigView {
    companion object {
        fun newInstance(meshNode: MeshNode) =
            @Suppress("DEPRECATION")
            DeviceConfigFragmentNLC().withMeshNavArg(meshNode.node.toNavArg())
    }

    private val layout by viewBinding(FragmentDeviceConfigBinding::bind)
    private val deviceConfigPresenter: DeviceConfigPresenter by presenters()

    private lateinit var loadingDialogLayout: DialogLoadingBinding
    private var loadingDialog: AlertDialog? = null
    private var chosenFriendNode: Node? = null

    private val nodeNameTextWatcher = object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            deviceConfigPresenter.changeName(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadingDialogLayout = DialogLoadingBinding.inflate(layoutInflater)

        setupFirmwareUpdateSection()
        setupDcdWarningSection()
        setLoadingDialogIntialize_nlc()
    }

    private fun setupFirmwareUpdateSection() {
        layout.wrapperFirmwareUpdate.isVisible =
            deviceConfigPresenter.meshNode.supportsFirmwareUpdate()
        layout.wrapperFirmwareUpdate.setOnClickListener {
            navigateToStandaloneUpdaterFragment()
        }
    }

    private fun setupDcdWarningSection() {
        layout.buttonShowDcdInfo.setOnClickListener { showDcdInfoDialog() }
        layout.buttonRefreshDcd.setOnClickListener { deviceConfigPresenter.updateCompositionData() }
    }

    private fun showDcdInfoDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.device_config_dcd_info_title))
            .setMessage(getString(R.string.device_config_dcd_info_content))
            .setPositiveButton(R.string.dialog_positive_ok) { dialog, _ ->
                dialog.dismiss()
            }.create()
            .show()
    }

    private fun navigateToStandaloneUpdaterFragment() {
        val updaterFragment =
            StandaloneUpdaterFragment.newInstance(deviceConfigPresenter.meshNode.node)
        requireMainActivity().showFragment(updaterFragment)
    }

    private var appKeysInSubnet: List<AppKey> = emptyList()
    private var meshNode: MeshNode? = null

    override fun setDeviceConfig(
        meshNode: MeshNode,
        deviceConfig: DeviceConfig,
        appKeysInSubnet: List<AppKey>,
        nodes: List<Node>,
    ) {
        activity?.runOnUiThread {
            this.appKeysInSubnet = appKeysInSubnet
            this.meshNode = meshNode
            changeSectionsVisibility(meshNode)
            refreshNameSection(meshNode.node)
            setupFeaturesSections(deviceConfig)
            setupAESection(deviceConfig)
            setupLpnSections(nodes, deviceConfig)
            setupAppKeySection(meshNode.node, appKeysInSubnet)
            setupFunctionalitiesSection(meshNode)
            refreshFirmwareUpdateSection(meshNode)
        }
    }

    fun getAppKeysInSubnet(): List<AppKey> {
        return appKeysInSubnet
    }

    fun getMeshNode(): MeshNode? {
        return meshNode
    }

    private fun changeSectionsVisibility(meshNode: MeshNode) {
        layout.apply {
            wrapperDcdWarning.isVisible = meshNode.node.deviceCompositionData == null
            wrapperLowPower.isVisible =
                meshNode.node.deviceCompositionData?.supportsLowPower ?: false
            wrapperProxy.isVisible = meshNode.node.deviceCompositionData?.supportsProxy ?: false
            wrapperRelay.isVisible = meshNode.node.deviceCompositionData?.supportsRelay ?: false
            wrapperFriend.isVisible = meshNode.node.deviceCompositionData?.supportsFriend ?: false
            wrapperRetransmission.isVisible = meshNode.node.deviceCompositionData != null
            val hasDistributorRole =
                meshNode.functionality == DeviceFunctionality.FUNCTIONALITY.DistributorServer
            layout.wrapperAdvertisementExtension.isVisible =
                meshNode.node.supportsAdvertisementExtension() && hasDistributorRole // as a Demo Application, we support AE only for Distributor role
        }
    }

    private fun refreshNameSection(node: Node) {
        layout.editTextDeviceName.apply {
            removeTextChangedListener(nodeNameTextWatcher)
            setText(node.name)
            addTextChangedListener(nodeNameTextWatcher)
        }
    }

    private fun setupFeaturesSections(deviceConfig: DeviceConfig) {
        setFeaturesOnClickListeners()
        disableFeaturesOnCheckedChangeListeners()
        fillFeaturesData(deviceConfig)
        setFeaturesOnCheckedChangeListeners()
    }

    private fun setFeaturesOnClickListeners() {
        layout.apply {
            buttonGetProxy.setOnClickListener { deviceConfigPresenter.updateProxy() }
            buttonGetRelay.setOnClickListener { deviceConfigPresenter.updateRelay() }
            buttonGetFriend.setOnClickListener { deviceConfigPresenter.updateFriend() }
            buttonGetRetransmission.setOnClickListener { deviceConfigPresenter.updateRetransmission() }
        }
    }

    private fun disableFeaturesOnCheckedChangeListeners() {
        layout.apply {
            switchProxy.setOnCheckedChangeListener(null)
            switchRelay.setOnCheckedChangeListener(null)
            switchFriend.setOnCheckedChangeListener(null)
            switchRetransmission.setOnCheckedChangeListener(null)
        }
    }

    private fun fillFeaturesData(deviceConfig: DeviceConfig) {
        deviceConfig.proxy?.let {
            layout.switchProxy.isEnabled = true
            layout.switchProxy.isChecked = it
        }
        deviceConfig.relay?.let {
            layout.switchRelay.isEnabled = true
            layout.switchRelay.isChecked = it
        }
        deviceConfig.friend?.let {
            layout.switchFriend.isEnabled = true
            layout.switchFriend.isChecked = it
        }
        deviceConfig.retransmission?.let {
            layout.switchRetransmission.isEnabled = true
            layout.switchRetransmission.isChecked = it
        }
    }

    private fun setFeaturesOnCheckedChangeListeners() {
        layout.apply {
            switchProxy.setOnCheckedChangeListener { _, isChecked ->
                deviceConfigPresenter.processChangeProxy(isChecked)
            }
            switchRelay.setOnCheckedChangeListener { _, isChecked ->
                deviceConfigPresenter.changeRelay(isChecked)
            }
            switchFriend.setOnCheckedChangeListener { _, isChecked ->
                deviceConfigPresenter.changeFriend(isChecked)
            }
            switchRetransmission.setOnCheckedChangeListener { _, isChecked ->
                deviceConfigPresenter.changeRetransmission(isChecked)
            }
        }
    }

    private fun setupAESection(deviceConfig: DeviceConfig) {
        setupAdvertisementExtensionButton()
        setupAdvertisementExtensionSwitch(deviceConfig)
    }

    private fun setupAdvertisementExtensionButton() {
        layout.btnGetAdvertisementExtensionState.setOnClickListener {
            deviceConfigPresenter.updateAdvertisementExtensionForDistributorRole()
        }
    }

    private fun setupAdvertisementExtensionSwitch(deviceConfig: DeviceConfig) {
        layout.switchAdvertisementExtension.apply {
            deviceConfig.advertisementExtensionOnBlobClientEnabled?.let {
                isEnabled = true
                isChecked = it
            } ?: run {
                isEnabled = false
            }
            setOnCheckedChangeListener { _, isChecked ->
                deviceConfigPresenter.changeAdvertisementExtensionForDistributorRole(isChecked)
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    private fun setupLpnSections(nodes: List<Node>, deviceConfig: DeviceConfig) {
        layout.apply {
            spinnerFriendNodes.adapter = NodesAdapter(requireContext(), nodes)
            buttonGetPollTimeout.setOnClickListener {
                spinnerFriendNodes.selectedItem?.let { deviceConfigPresenter.updatePollTimeout(it as Node) }
                    ?: MeshToast.show(
                        requireContext(),
                        getString(R.string.device_dialog_friend_null)
                    )
            }
            deviceConfig.pollTimeout?.let {
                fillPollTimeout(deviceConfig)
                spinnerFriendNodes.setSelection(nodes.indexOf(chosenFriendNode).coerceAtLeast(0))
            }
            spinnerFriendNodes.setOnItemSelectedListenerOnViewCreated { position ->
                chosenFriendNode = nodes[position]
                fillPollTimeout(deviceConfig)
            }

            buttonSetGlobalTimeout.setOnClickListener {
                deviceConfigPresenter.changeLpnGlobalTimeout(editTextGlobalTimeoutSecs.text.toString())
            }
            deviceConfig.lpnGlobalTimeout?.let {
                textViewLpnGlobalTimeout.text =
                    String.format(getString(R.string.device_dialog_lpn_value_label), it)
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    private fun fillPollTimeout(deviceConfig: DeviceConfig) {
        layout.textViewPollTimeout.text =
            if (chosenFriendNode == deviceConfig.pollTimeoutFriend) {
                String.format(
                    getString(R.string.device_dialog_poll_timeout_value),
                    deviceConfig.pollTimeout
                )
            } else {
                getString(R.string.device_dialog_poll_timeout_value_unknown)
            }
    }

    private fun setupAppKeySection(node: Node, appKeysInSubnet: List<AppKey>) {
        val appKeyNames = listOf("") + appKeysInSubnet.map { it.index.toString() }
        val appKeysAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_dark, appKeyNames)
        val initialAppKeyName = if (node.boundAppKeys.isNotEmpty()) {
            node.boundAppKeys.first().index.toString()
        } else appKeyNames.first()
        if (DeviceFunctionalityDb.getTab()) {
            layout.appkeyParentLayout.visibility = View.GONE
        } else {
            layout.appkeyParentLayout.visibility = View.VISIBLE
        }

        layout.dropdownMenuAppKeys.apply {
            setAdapter(appKeysAdapter)
            setOnItemClickListener { _, _, position, _ ->
                if (position == 0) {
                    deviceConfigPresenter.processChangeAppKey(null)
                } else {
                    deviceConfigPresenter.processChangeAppKey(appKeysInSubnet[position - 1])
                }
            }
            setText(initialAppKeyName, false)
        }
    }

    val delayMills_appKey = 2000 // 2 seconds
    val delayMillis_functionality = 2000 // 2 seconds
    override fun onResume() {
        super.onResume()
        DeviceFunctionalityDb.saveTab(true)
        // println("DeviceConfigPresenter NLC executed "+getAppKeysInSubnet().size)
        showLoadingDialog_nlc("Please wait", false)
        Handler(Looper.getMainLooper()).postDelayed({
            // Code to be executed after the delay
            deviceConfigPresenter.processChangeAppKey(getAppKeysInSubnet().get(0))
            appKeyBindingstate = true
            dismissLoadingDialouge_nlc()
        }, delayMills_appKey.toLong())
    }

    private fun bindFunctionality() {

        val supportedFunctionalities =
            DeviceFunctionality.getFunctionalitiesNamed(getMeshNode()!!.node).toMutableList()
                .sortedBy { it.functionalityName }
        val functionalitiesNames = supportedFunctionalities.map { it.functionalityName }
        val functionalitiesAdapter =
            ArrayAdapter(requireContext(), R.layout.spinner_item_dark, functionalitiesNames)
        val initialFunctionalityName =
            if (getMeshNode()!!.functionality != DeviceFunctionality.FUNCTIONALITY.Unknown) {
                supportedFunctionalities.indexOfFirst { it.functionality == getMeshNode()!!.functionality }
                    .takeUnless { it == -1 }
                    ?.let { index -> functionalitiesNames[index] } ?: functionalitiesNames.first()
            } else {
                functionalitiesNames.first()
            }
        println("NLC Device PID:${meshNode!!.node.deviceCompositionData!!.pid}")
        if (meshNode!!.node.deviceCompositionData!!.pid == BASIC_LIGHTNESS_CONTROLLER) {
            deviceConfigPresenter.processChangeFunctionality(supportedFunctionalities[3].functionality)
            checkFunctionalityCompleted = true
        } else {
            deviceConfigPresenter.processChangeFunctionality(supportedFunctionalities[1].functionality)
            checkFunctionalityCompleted = true
        }
    }

    private fun setupFunctionalitiesSection(meshNode: MeshNode) {
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

        if (DeviceFunctionalityDb.getTab()) {
            layout.functionalityParentLayout.visibility = View.GONE
        } else {
            layout.functionalityParentLayout.visibility = View.VISIBLE
        }

        layout.dropdownMenuFunctionalities.apply {
            setAdapter(functionalitiesAdapter)
            setOnItemClickListener { _, _, position, _ ->
                deviceConfigPresenter.processChangeFunctionality(supportedFunctionalities[position].functionality)
            }
            setText(initialFunctionalityName, false)
        }
    }

    private fun refreshFirmwareUpdateSection(meshNode: MeshNode) {
        layout.wrapperFirmwareUpdate.isVisible = meshNode.supportsFirmwareUpdate()
    }

    override fun showToast(message: DeviceConfigView.ToastMessage) {
        activity?.runOnUiThread {
            val stringResource = when (message) {
                DeviceConfigView.ToastMessage.ERROR_MISSING_APPKEY -> getString(R.string.device_config_select_appkey_first)
                DeviceConfigView.ToastMessage.POLL_TIMEOUT_UPDATED -> getString(R.string.device_config_poll_timeout_updated)
                DeviceConfigView.ToastMessage.POLL_TIMEOUT_NOT_FRIEND -> getString(
                    R.string.device_config_poll_timeout_not_friend,
                    (layout.spinnerFriendNodes.selectedItem as Node).name
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
                setPositiveButton(this@DeviceConfigFragmentNLC.getString(R.string.dialog_positive_ok)) { dialog, _ ->
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

    override fun dismissLoadingDialog() {
        println("NLC dismissLoadingDialog")
        activity?.runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
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
                deviceConfigPresenter.abandonTasks()
                dismiss()
            }
        }
    }

    override fun showRetryButton() {
        activity?.runOnUiThread {
            loadingDialog?.takeIf { it.isShowing }?.apply {
                getButton(AlertDialog.BUTTON_NEUTRAL).apply {
                    visibility = View.VISIBLE
                    text = context.getString(R.string.dialog_retry)
                    setOnClickListener {
                        deviceConfigPresenter.retryTask()
                    }
                }
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
        executeFunctionality(allTasksCount)
    }

    private var allTasksCountOngoing = 1
    private var appKeyBindingstate = false

    private var checkFunctionalityCompleted = false
    private var allTaskFunctionality = 1;

    private fun executeFunctionality(allTasksCount: Int) {
        if (appKeyBindingstate) {
            allTasksCountOngoing++
        }
        if (allTasksCountOngoing == allTasksCount) {
            setLoadingDialogIntialize_nlc()
            showLoadingDialog_nlc("Please wait", false)
            appKeyBindingstate = false
            allTasksCountOngoing = 1
            Handler(Looper.getMainLooper()).postDelayed({
                bindFunctionality()
            }, delayMillis_functionality.toLong())
        }
        if (checkFunctionalityCompleted == true) {
            allTaskFunctionality++
        }

        if (allTasksCount == allTaskFunctionality) {
            Handler(Looper.getMainLooper()).postDelayed({
                allTaskFunctionality = 1
                dismissLoadingDialouge_nlc()
                requireMainActivity().returnToMainScreenFromDeviceConfigNLCFragment()
            }, delayMillis_functionality.toLong())
        }
    }

    private fun getMessageResource(
        loadingMessage: DeviceConfigView.LoadingDialogMessage,
        message: String,
    ): String {
        println("NLC getMessageResource ")
        return activity?.let {
            when (loadingMessage) {

                DeviceConfigView.LoadingDialogMessage.CONFIG_ADDING_APPKEY_TO_NODE -> it.getString(R.string.device_config_adding_appkey)
                    .format(message)

                DeviceConfigView.LoadingDialogMessage.CONFIG_REMOVING_APPKEY_FROM_NODE -> it.getString(
                    R.string.device_config_removing_appkey
                )
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
        layout.editTextGlobalTimeoutSecs.setText(timeout.toString())
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    private lateinit var loadingDialogLayout_nlc: DialogLoadingBinding
    private var loadingDialog_nlc: AlertDialog? = null
    fun showLoadingDialog_nlc(message: String, showCloseButton: Boolean) {
        activity?.runOnUiThread {
            if (loadingDialog_nlc?.isShowing == true) {
                loadingDialogLayout_nlc.apply {
                    loadingText.text = message
                    if (showCloseButton) {
                        loadingIcon.visibility = View.GONE
                        loadingDialog_nlc!!.setupCloseButton_nlc()
                    } else {
                        loadingIcon.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun dismissLoadingDialouge_nlc() {
        activity?.runOnUiThread {
            loadingDialog_nlc?.dismiss()
            loadingDialog_nlc = null
        }
    }

    private fun AlertDialog.setupCloseButton_nlc() {
        getButton(AlertDialog.BUTTON_POSITIVE).apply {
            visibility = View.VISIBLE
            setOnClickListener {
                // deviceConfigPresenter.abandonTasks()
                dismiss()
            }
        }
    }

    private fun setLoadingDialogIntialize_nlc() {
        loadingDialogLayout_nlc = DialogLoadingBinding.inflate(layoutInflater)
        val builder = CustomAlertDialogBuilder(requireContext()).apply {
            setView(loadingDialogLayout_nlc.root)
            setCancelable(false)
            setPositiveButton(this@DeviceConfigFragmentNLC.getString(R.string.dialog_positive_ok)) { dialog, _ ->
                dialog.dismiss()
            }
        }
        loadingDialogLayout_nlc.root.apply {
            (parent as? ViewGroup)?.removeView(this)
        }

        loadingDialog_nlc = builder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
        }
    }
}
