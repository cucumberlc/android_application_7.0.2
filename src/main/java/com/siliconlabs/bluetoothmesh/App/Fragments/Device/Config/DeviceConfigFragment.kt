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
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment.Companion.KEY_IS_AUTO_SELECT_FUNCTIONALITY
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
import org.tinylog.kotlin.Logger

@AndroidEntryPoint
class DeviceConfigFragment
@Deprecated(
    "use newInstance(MeshNode)",
    replaceWith = ReplaceWith(".newInstance(meshNode)")
)
constructor() : Fragment(R.layout.fragment_device_config), DeviceConfigView {
    companion object {
        const val KEY_IS_DISPLAY_CONFIG_CONTROL = "KEY_DISPLAY_CONFIG_CONTROL"
        const val KEY_IS_NLC_CONTROL = "KEY_IS_NLC_CONTROL"

        fun newInstance(meshNode: MeshNode, flag: Boolean, autoConnect: Boolean, isNLC: Boolean) =
            @Suppress("DEPRECATION")
            DeviceConfigFragment().withMeshNavArg(meshNode.node.toNavArg()).apply {
                arguments!!.putBoolean(KEY_IS_DISPLAY_CONFIG_CONTROL, flag)
                arguments!!.putBoolean(KEY_IS_AUTO_SELECT_FUNCTIONALITY, autoConnect)
                arguments!!.putBoolean(KEY_IS_NLC_CONTROL, isNLC)
            }
        const val SILICON_LAB_COMPANY_IDENTITY = 767

        const val BTMESH_NCP_EMPTY = 0
        const val BTMESH_SOC_EMPTY = 1
        const val BTMESH_SOC_HSL_LIGHT = 2
        const val BTMESH_SOC_LIGHT = 3
        const val BTMESH_SOC_SENSOR_CLIENT = 4
        const val BTMESH_SOC_SENSOR_SERVER = 5
        const val BTMESH_SOC_SWITCH = 6
        const val BTMESH_SOC_SWITCH_LOW_POWER = 7
        const val BTMESH_SOC_DFU_DISTRIBUTOR = 12
        const val BTMESH_SOC_EMPTY_WITH_CERTIFICATE_BASED_PROVISIONING = 13
        const val BASIC_LIGHTNESS_CONTROLLER =
            14    //MARK: Basic Lightness Controller - 0x000e - 14
        const val DIMMING_CONTROL = 15
        const val DIMMING_CONTROL_LOW_POWER = 16
        const val AMBIENT_LIGHT_SENSOR = 17
        const val OCCUPANCY_SENSOR = 18
        const val BASIC_SCENE_SELECTOR = 19
        const val SCENE_LOW_POWER = 20
        const val SWITCH_CTL = 21
        const val SWITCH_CTL_LOW_POWER = 22
        const val LIGHT_SERVER_DEVICE = 23
        const val HSL_SERVER_DEVICE = 24
        const val SENSOR_THERMOMETER = 25
        const val BTMESH_SOS_SENSOR_THERMOMETER = 26
        const val LIGHT_LC_SERVER = "Light LC Server"
        const val LIGHT_CTL_SERVER = "Light CTL Server"
        const val LIGHT_LIGHTNESS_SERVER = "Light Lightness Server"
        const val LIGHT_LIGHTNESS_CLIENT = "Light Lightness Client"
        const val LIGHT_CTL_CLIENT = "Light CTL Client"
        const val GENERIC_LEVEL_CLIENT = "Generic Level Client"
        const val SENSOR_SERVER = "Sensor Server"
        const val SENSOR_CLIENT = "Sensor Client"
        const val SCENE_CLIENT = "Scene Client"
        const val FIRMWARE_DISTRIBUTION_SERVER = "Firmware Distribution Server"
    }

    private val TAG = this::deviceConfigPresenter.name.toString()
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
        val isDisplay = requireArguments().getBoolean(KEY_IS_DISPLAY_CONFIG_CONTROL)
        if (isDisplay) {
            layout.configParent.visibility = View.VISIBLE
        } else {
            layout.configParent.visibility = View.GONE
        }
        val isDisplayFunction = requireArguments().getBoolean(KEY_IS_NLC_CONTROL)
        println("NonNLC isDisplayFunction:status=>$isDisplayFunction")
        if (isDisplayFunction) {
            layout.functionalityParentLayout.visibility = View.GONE
        } else {
            layout.functionalityParentLayout.visibility = View.VISIBLE
        }
        setupFirmwareUpdateSection()
        setupDcdWarningSection()
        setLoadingDialogIntializeNonNLC()
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

    private var appKeysInSubnetNonNLC: List<AppKey> = emptyList()
    private var meshNode: MeshNode? = null

    override fun setDeviceConfig(
        meshNode: MeshNode,
        deviceConfig: DeviceConfig,
        appKeysInSubnet: List<AppKey>,
        nodes: List<Node>,
    ) {
        activity?.runOnUiThread {
            this.appKeysInSubnetNonNLC = appKeysInSubnet
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
        return appKeysInSubnetNonNLC
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

    private fun setNonNLCAppKeySection(node: Node, appKeysInSubnet: List<AppKey>) {
        layout.appkeyParentLayout.visibility = View.GONE
        deviceConfigPresenter.processChangeAppKey(appKeysInSubnetNonNLC[0])
        //Toast.makeText(requireContext(), "AppKey assigned Successfully", Toast.LENGTH_SHORT).show()
    }

    private fun setupAppKeySection(node: Node, appKeysInSubnet: List<AppKey>) {
        val appKeyNames = listOf("") + appKeysInSubnet.map { it.index.toString() }
        val appKeysAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_dark, appKeyNames)
        val initialAppKeyName = if (node.boundAppKeys.isNotEmpty()) {
            node.boundAppKeys.first().index.toString()
        } else appKeyNames.first()
        // if (DeviceFunctionalityDb.getTab()) {
        layout.appkeyParentLayout.visibility = View.GONE
        //        } else {
        //            layout.appkeyParentLayout.visibility = View.VISIBLE
        //        }
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
        DeviceFunctionalityDb.saveTab(false)
        // println("DeviceConfigPresenter NLC executed "+getAppKeysInSubnet().size)
        dismissLoadingDialog()
        showLoadingDialog_nlc("Please wait", false)
        Handler(Looper.getMainLooper()).postDelayed({
            // Code to be executed after the delay
            dismissLoadingDialougeNonNLC()
            deviceConfigPresenter.processChangeAppKey(appKeysInSubnetNonNLC[0])
            appKeyBindingstate = true
        }, delayMills_appKey.toLong())
    }

    private fun bindFunctionality() {

        val supportedFunctionalities =
            DeviceFunctionality.getFunctionalitiesNamed(getMeshNode()!!.node).toMutableList()
                .sortedBy { it.functionalityName }
        val functionalitiesNames = supportedFunctionalities.map { it.functionalityName }
        println("Non-NLC functionalitiesNames:  $functionalitiesNames")
        val cid = meshNode!!.node.deviceCompositionData!!.cid
        println("Non-NLC CID $cid")
        val nonNLCPID = meshNode!!.node.deviceCompositionData!!.pid
        println("Non-NLC PID $nonNLCPID")
        var pos = 1
        if (cid == SILICON_LAB_COMPANY_IDENTITY && !functionalitiesNames.isEmpty()) {
            when (nonNLCPID) {
               // BTMESH_NCP_EMPTY -> pos = functionalitiesNames.indexOf(SCENE_CLIENT)

                BTMESH_SOC_HSL_LIGHT -> pos =
                    functionalitiesNames.indexOf(LIGHT_LIGHTNESS_SERVER)

                BTMESH_SOC_LIGHT -> pos =
                    functionalitiesNames.indexOf(LIGHT_LIGHTNESS_SERVER)

                BTMESH_SOC_SENSOR_CLIENT -> pos = functionalitiesNames.indexOf(SENSOR_CLIENT)
                BTMESH_SOC_SENSOR_SERVER -> pos = functionalitiesNames.indexOf(SENSOR_SERVER)

                BTMESH_SOC_SWITCH -> pos = functionalitiesNames.indexOf(LIGHT_LIGHTNESS_CLIENT)
                BTMESH_SOC_SWITCH_LOW_POWER -> pos =
                    functionalitiesNames.indexOf(LIGHT_LIGHTNESS_CLIENT)

                BTMESH_SOC_DFU_DISTRIBUTOR -> pos =
                    functionalitiesNames.indexOf(FIRMWARE_DISTRIBUTION_SERVER)

                //            BTMESH_SOC_EMPTY_WITH_CERTIFICATE_BASED_PROVISIONING -> pos =
                //                functionalitiesNames.indexOf(
                //                    SCENE_CLIENT
                //                )

                BASIC_LIGHTNESS_CONTROLLER -> pos = functionalitiesNames.indexOf(LIGHT_LC_SERVER)
                DIMMING_CONTROL -> pos = functionalitiesNames.indexOf(GENERIC_LEVEL_CLIENT)
                DIMMING_CONTROL_LOW_POWER -> pos =
                    functionalitiesNames.indexOf(GENERIC_LEVEL_CLIENT)

                AMBIENT_LIGHT_SENSOR -> pos = functionalitiesNames.indexOf(
                    SENSOR_SERVER
                )

                OCCUPANCY_SENSOR -> pos = functionalitiesNames.indexOf(
                    SENSOR_SERVER
                )

                BASIC_SCENE_SELECTOR -> pos =
                    functionalitiesNames.indexOf(SCENE_CLIENT)

                SCENE_LOW_POWER -> pos =
                    functionalitiesNames.indexOf(SCENE_CLIENT)

                SWITCH_CTL -> pos = functionalitiesNames.indexOf(LIGHT_CTL_CLIENT)
                SWITCH_CTL_LOW_POWER -> pos =
                    functionalitiesNames.indexOf(LIGHT_CTL_CLIENT)

                LIGHT_SERVER_DEVICE -> pos = functionalitiesNames.indexOf(
                    LIGHT_CTL_SERVER
                )

                HSL_SERVER_DEVICE -> pos = functionalitiesNames.indexOf(LIGHT_LIGHTNESS_SERVER)

                SENSOR_THERMOMETER -> pos =
                    functionalitiesNames.indexOf(SENSOR_SERVER)

                //BTMESH_SOS_SENSOR_THERMOMETER -> pos = 0
                else -> {
                    MeshToast.show(requireContext(), R.string.no_functionality_found)
                    deviceConfigPresenter.processNonAvailableFunctionality(supportedFunctionalities[0].functionality)
                    checkFunctionalityCompleted = true
                    dismissLoadingDialog()
                    return
                }
            }
            Logger.debug(TAG, "NonNLC Pos:$pos")
            deviceConfigPresenter.processChangeFunctionality(supportedFunctionalities[pos].functionality)
            checkFunctionalityCompleted = true
        } else {
            MeshToast.show(requireContext(), R.string.no_functionality_found)
            dismissLoadingDialog()
            deviceConfigPresenter.processNonAvailableFunctionality(supportedFunctionalities[0].functionality)
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

        val isNLC = requireArguments().getBoolean(KEY_IS_NLC_CONTROL)
        println("Non-NLC: isNLC:$isNLC")
        println("Non-NLC: functionalitiesNames.size->${functionalitiesNames.size}")
        if (isNLC) {
            layout.functionalityParentLayout.visibility = View.GONE
        } else {
            if(functionalitiesNames.size == 1){
                layout.functionalityParentLayout.visibility = View.GONE
                return
            }
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
                setPositiveButton(this@DeviceConfigFragment.getString(R.string.dialog_positive_ok)) { dialog, _ ->
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
        println("Non-NLC dismissLoadingDialog")
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
        println("Non-NLC AutoConnect:${requireArguments().getBoolean(KEY_IS_AUTO_SELECT_FUNCTIONALITY)}")
        if (arguments != null && requireArguments().getBoolean(KEY_IS_AUTO_SELECT_FUNCTIONALITY)) {
            extracted(allTasksCount)
        }
    }

    private fun extracted(allTasksCount: Int) {
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
            setLoadingDialogIntializeNonNLC()
            dismissLoadingDialog()
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
                dismissLoadingDialougeNonNLC()
                requireArguments().putBoolean(KEY_IS_AUTO_SELECT_FUNCTIONALITY, false)
                requireMainActivity().returnTOMainScreenFromDeviceConfigNonNLCFragment()
            }, delayMillis_functionality.toLong())
        }
    }

    private fun getMessageResource(
        loadingMessage: DeviceConfigView.LoadingDialogMessage,
        message: String,
    ): String {
        println("Non-NLC getMessageResource ")
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

    private lateinit var loadingDialogLayoutNonNLC: DialogLoadingBinding
    private var loadingDialogNonNLC: AlertDialog? = null
    private fun showLoadingDialog_nlc(message: String, showCloseButton: Boolean) {
        activity?.runOnUiThread {
            if (loadingDialogNonNLC?.isShowing == true) {
                loadingDialogLayoutNonNLC.apply {
                    loadingText.text = message
                    if (showCloseButton) {
                        loadingIcon.visibility = View.GONE
                        loadingDialogNonNLC!!.setupCloseButton_nlc()
                    } else {
                        loadingIcon.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun dismissLoadingDialougeNonNLC() {
        activity?.runOnUiThread {
            loadingDialogNonNLC?.dismiss()
            loadingDialogNonNLC = null
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

    private fun setLoadingDialogIntializeNonNLC() {
        loadingDialogLayoutNonNLC = DialogLoadingBinding.inflate(layoutInflater)
        val builder = CustomAlertDialogBuilder(requireContext()).apply {
            setView(loadingDialogLayoutNonNLC.root)
            setCancelable(false)
            setPositiveButton(this@DeviceConfigFragment.getString(R.string.dialog_positive_ok)) { dialog, _ ->
                dialog.dismiss()
            }
        }
        loadingDialogLayoutNonNLC.root.apply {
            (parent as? ViewGroup)?.removeView(this)
        }

        loadingDialogNonNLC = builder.create().apply {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            show()
            getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
        }
    }
}
