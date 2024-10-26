/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config

/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */
import android.app.AlertDialog
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.configuration_control.CheckNodeBehaviourCallback
import com.siliconlab.bluetoothmesh.adk.configuration_control.CheckRelayCallback
import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControlSettings
import com.siliconlab.bluetoothmesh.adk.configuration_control.GetDeviceCompositionDataCallback
import com.siliconlab.bluetoothmesh.adk.configuration_control.LpnPollTimeoutCallback
import com.siliconlab.bluetoothmesh.adk.configuration_control.NodeRetransmissionConfigurationCallback
import com.siliconlab.bluetoothmesh.adk.configuration_control.SetNodeBehaviourCallback
import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.functionality_binder.FunctionalityBinder
import com.siliconlab.bluetoothmesh.adk.functionality_binder.FunctionalityBinderCallback
import com.siliconlab.bluetoothmesh.adk.functionality_control.advertisement_extension.AdvertisementExtension.Companion.findSilabsConfigurationServerModel
import com.siliconlab.bluetoothmesh.adk.functionality_control.advertisement_extension.ConfigurationStatus
import com.siliconlab.bluetoothmesh.adk.functionality_control.publication.Credentials
import com.siliconlab.bluetoothmesh.adk.functionality_control.publication.Publication
import com.siliconlab.bluetoothmesh.adk.functionality_control.subscription.Subscription
import com.siliconlab.bluetoothmesh.adk.isSuccess
import com.siliconlab.bluetoothmesh.adk.node_control.NodeControl
import com.siliconlab.bluetoothmesh.adk.node_control.NodeControlCallback
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.onSuccess
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigView.LoadingDialogMessage
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper.fetchRemoteModelState
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper.isAdvertisementExtensionOnNodeSetCorrectly
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper.isNetworkPDUSetCorrectly
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceConfig
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Utils.encodeHex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.inject.Inject


class DeviceConfigNLCPresenter (
    val networkConnectionLogic: NetworkConnectionLogic,
    val meshNode : MeshNode
) : BasePresenter<DeviceConfigView>(), NetworkConnectionListener {
    private val deviceConfigView
        get() = view
    private val configurationControl: ConfigurationControl = ConfigurationControl(meshNode.node)
    private val nodeControl: NodeControl = NodeControl(meshNode.node)

    private val taskList = mutableListOf<Runnable>()
    private var allTasksCount = 0
    private var currentTask = Runnable { }

    private var isDeviceConfigurationFetched = false

    private var isProxy: Boolean? = meshNode.takeProxyIsEnabledHint()
    private var isRelay: Boolean? = null
    private var isFriend: Boolean? = null
    private var isRetransmission: Boolean? = null
    private var supportFriend: Boolean? = null
    private var supportLowPower: Boolean? = null
    private var pollTimeoutFriend: Node? = null
    private var pollTimeout: Int? = null
    private var lpnGlobalTimeout: Int? = null
    private var isAdvertisementExtensionOnBlobClientModelEnabled: Boolean? = null

    private val relayRetransmitCount = 2
    private val relayRetransmitIntervalSteps = 1

    override fun onResume() {
        networkConnectionLogic.addListener(this)
        refreshView()
    }

    override fun onPause() {
        networkConnectionLogic.removeListener(this)
    }

    private fun getDeviceConfig() {
        if (isDeviceConfigurationFetched) {
            return
        }

        meshNode.node.deviceCompositionData?.let { data ->
            supportFriend = data.supportsFriend
            supportLowPower = data.supportsLowPower
        }
        updateLpnGlobalTimeout()
        isDeviceConfigurationFetched = true
    }

    private fun refreshView() {
        val currentConfig = DeviceConfig(
            meshNode.node.name,
            isProxy,
            isRelay,
            isFriend,
            isRetransmission,
            isAdvertisementExtensionOnBlobClientModelEnabled,
            supportLowPower,
            meshNode.functionality,
            pollTimeoutFriend,
            pollTimeout,
            lpnGlobalTimeout
        )

        val appKeysInSubnet = meshNode.node.subnets.first().appKeys.sortedBy { it.index }
        val nodes = meshNode.node.subnets.first().nodes.filter {
            it.deviceCompositionData?.supportsFriend ?: true
        }
        deviceConfigView?.setDeviceConfig(meshNode, currentConfig, appKeysInSubnet, nodes)
    }

    private fun startTasks() {
        if (taskList.size > 0) {
            allTasksCount = taskList.size
            deviceConfigView?.showLoadingDialog()
            takeNextTask()
        }
    }

    fun takeNextTask() {
        if (taskList.isNotEmpty()) {
            currentTask = taskList.first()
            taskList.remove(currentTask)
            currentTask.run()
        } else {
            refreshView()
            deviceConfigView?.dismissLoadingDialog()
        }
    }

    fun retryTask() {
        deviceConfigView?.dismissLoadingDialog()
        deviceConfigView?.showLoadingDialog()
        taskList.add(0, currentTask)
        takeNextTask()
    }

    fun abandonTasks() {
        taskList.clear()
    }

    fun showErrorMessage(error: NodeControlError) {
        refreshView()
        deviceConfigView?.setLoadingDialogMessage(error, showCloseButton = true)
    }

    fun showErrorMessageWithRetryButton(error: NodeControlError) {
        refreshView()
        deviceConfigView?.setLoadingDialogMessage(error, showCloseButton = true)
        deviceConfigView?.showRetryButton()
    }

    override fun connected() {
        getDeviceConfig()
    }

    private fun isConnectedToProxyDevice(): Boolean {
        val currentlyConnectedNode = networkConnectionLogic.getCurrentlyConnectedNode()
        return currentlyConnectedNode?.let { it == meshNode.node } ?: false
    }

    fun processChangeAppKey(newAppKey: AppKey?) {
        val currentGroup = BluetoothMesh.network.groups.first()
        val currentAppKey = meshNode.node.boundAppKeys.firstOrNull()

        if (currentAppKey == newAppKey) {
            //nothing changed
            return
        }

        currentAppKey?.let {
            taskList.addAll(processUnsubscribeModelFromGroup(currentGroup, meshNode.functionality))
            taskList.addAll(
                processUnbindModelFromAppKey(
                    currentGroup,
                    it,
                    DeviceFunctionality.FUNCTIONALITY.Unknown
                )
            )
            taskList.add(unbindNodeFromAppKey(it))
        }

        if (meshNode.functionality != DeviceFunctionality.FUNCTIONALITY.Unknown) {
            taskList.add(changeFunctionality(DeviceFunctionality.FUNCTIONALITY.Unknown))
        }
        if (newAppKey != null) {
            taskList.add(bindNodeToAppKey(newAppKey))
            taskList.addAll(
                processBindModelToAppKey(
                    currentGroup,
                    newAppKey,
                    DeviceFunctionality.FUNCTIONALITY.Unknown
                )
            )
            taskList.addAll(createAdvertisementExtensionSetUpTasks(newAppKey))
        }

        startTasks()
    }

    private fun createAdvertisementExtensionSetUpTasks(appKey: AppKey): List<Runnable> =
        meshNode.node.findSilabsConfigurationServerModel()?.let {
            listOf(
                bindModelToAppKey(it, appKey),
                setAdvertisementExtensionConfiguration(),
                setAdvertisementExtensionNetworkPduMaxSize()
            )
        } ?: emptyList()

    private fun setAdvertisementExtensionConfiguration() = Runnable {
        setLoadingDialogMessageWithSteps(LoadingDialogMessage.CONFIG_AE_SETTING_CONFIGURATION)

        AdvertisementExtensionHelper.setRemoteNodeConfiguration(meshNode.node)

        deviceConfigView?.scope?.launch {
            val response = AdvertisementExtensionHelper.fetchRemoteNodeConfiguration()
            if (response != null && isAdvertisementExtensionOnNodeSetCorrectly(response)) {
                takeNextTask()
            } else {
                val res = response?.status ?: "timeout"
                if (res == "timeout") {
                    takeNextTask()
                } else {
                    deviceConfigView?.setLoadingDialogMessage(
                        "Set Advertisement Extension configuration of the remote node failed (%s)".format(
                            response?.status ?: "timeout"
                        ),
                        showCloseButton = true
                    )
                    abandonTasks()
                }
            }
        }
    }

    private fun setAdvertisementExtensionNetworkPduMaxSize() = Runnable {
        setLoadingDialogMessageWithSteps(LoadingDialogMessage.CONFIG_AE_SETTING_PDU)

        AdvertisementExtensionHelper.setRemoteNodeNetworkPDUSize(meshNode.node)

        deviceConfigView?.scope?.launch {
            val response = AdvertisementExtensionHelper.fetchRemoteNodeNetworkPDUSize()
            if (response != null && isNetworkPDUSetCorrectly(response)) {
                takeNextTask()
            } else {
                val res = response?.status ?: "timeout"
                if (res == "timeout") {
                    takeNextTask()
                } else {
                    deviceConfigView?.setLoadingDialogMessage(
                        "Set the maximum Network PDU size of the remote node failed (%s)".format(
                            response?.status ?: "timeout"
                        ),
                        showCloseButton = true
                    )
                    abandonTasks()
                }
            }
        }
    }

    fun processChangeFunctionality(newFunctionality: DeviceFunctionality.FUNCTIONALITY) {
        val currentGroup = BluetoothMesh.network.groups.first()
        val currentAppKey = meshNode.node.boundAppKeys.firstOrNull()

        if (meshNode.functionality == newFunctionality) {
            //nothing changed
            return
        }

        if (currentAppKey == null) {
            MeshNodeManager.removeNodeFunc(meshNode)
            deviceConfigView?.showToast(DeviceConfigView.ToastMessage.ERROR_MISSING_APPKEY)
            refreshView()
            return
        } else {
            taskList.addAll(
                processUnbindModelFromAppKey(
                    currentGroup,
                    currentAppKey,
                    meshNode.functionality
                )
            )
            taskList.addAll(processBindModelToAppKey(currentGroup, currentAppKey, newFunctionality))
        }
        taskList.add(changeFunctionality(newFunctionality))

        startTasks()
    }

    private fun processBindModelToAppKey(
        group: Group,
        appKey: AppKey,
        functionality: DeviceFunctionality.FUNCTIONALITY,
    ): List<Runnable> {
        val tasks = mutableListOf<Runnable>()
        DeviceFunctionality.getSigModels(meshNode.node, functionality).forEach { model ->
            tasks.add(bindModelToAppKey(model, appKey))
            if (model.supportPublish) {
                tasks.add(addPublication(model, group, appKey, functionality))
            }
            if (model.supportSubscribe) {
                tasks.add(addSubscriptionSettings(model, group))
            }
        }
        return tasks
    }

    private fun addPublication(
        model: Model,
        group: Group,
        appKey: AppKey,
        functionality: DeviceFunctionality.FUNCTIONALITY,
    ) = Runnable {
        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_PUBLICATION_SETTING,
            model.identifier.encodeHex()
        )

        val period: UByte =
            if (functionality == DeviceFunctionality.FUNCTIONALITY.SensorServer) 84u else 0u
        val defaultTtl: UByte = 5u

        Publication.set(
            model = model,
            publicationAddress = group.address,
            appKey = appKey,
            ttl = defaultTtl,
            period = period,
            retransmissionIntervalSteps = 0,
            retransmissionCount = 0,
            credentials = Credentials.MASTER_SECURITY_MATERIALS,
        ).onFailure {
            showErrorMessageWithRetryButton(it)
            return@Runnable
        }

        viewModelScope.launch {
            Publication.publicationResponse.first()
                .onSuccess { takeNextTask() }
                .onFailure { showErrorMessageWithRetryButton(it) }
        }
    }

    private fun processUnbindModelFromAppKey(
        group: Group,
        appKey: AppKey,
        functionality: DeviceFunctionality.FUNCTIONALITY,
    ): List<Runnable> {
        val tasks = mutableListOf<Runnable>()
        DeviceFunctionality.getSigModels(meshNode.node, functionality).forEach { model ->
            if (model.supportPublish) {
                tasks.add(clearPublication(model))
            }
            if (model.supportSubscribe) {
                tasks.add(removeSubscriptionSettings(model, group))
            }
            tasks.add(unbindAppKeyFromModel(model, appKey))
        }
        return tasks
    }

    private fun clearPublication(model: Model) = Runnable {
        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_PUBLICATION_CLEARING,
            model.identifier.encodeHex()
        )

        Publication.clear(model).onFailure {
            showErrorMessageWithRetryButton(it)
            return@Runnable
        }

        viewModelScope.launch {
            Publication.publicationResponse.first()
                .onSuccess { takeNextTask() }
                .onFailure { showErrorMessageWithRetryButton(it) }
        }
    }

    private fun processUnsubscribeModelFromGroup(
        group: Group,
        functionality: DeviceFunctionality.FUNCTIONALITY,
    ): List<Runnable> {
        val tasks = mutableListOf<Runnable>()
        DeviceFunctionality.getSigModels(meshNode.node, functionality).forEach { model ->
            if (model.supportSubscribe) {
                tasks.add(removeSubscriptionSettings(model, group))
            }
        }
        return tasks
    }

    fun processChangeProxy(enabled: Boolean) {
        if (!enabled && isConnectedToProxyDevice()) {
            deviceConfigView?.showDisableProxyAttentionDialog { _, which ->
                when (which) {
                    AlertDialog.BUTTON_POSITIVE -> {
                        changeProxy(enabled)
                    }

                    AlertDialog.BUTTON_NEGATIVE -> {
                        refreshView()
                    }

                    AlertDialog.BUTTON_NEUTRAL -> Unit
                }
            }
        } else {
            changeProxy(enabled)
        }
    }

    private fun bindNodeToAppKey(appKey: AppKey) = Runnable {
        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_ADDING_APPKEY_TO_NODE,
            appKey.index.toString()
        )
        nodeControl.bindAppKey(appKey, PresenterNodeControlCallback())
    }

    private fun unbindNodeFromAppKey(appKey: AppKey) = Runnable {
        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_REMOVING_APPKEY_FROM_NODE,
            appKey.index.toString()
        )
        nodeControl.unbindAppKey(appKey, PresenterNodeControlCallback())
    }

    private fun bindModelToAppKey(model: Model, appKey: AppKey) = Runnable {
        val functionalityBinder = FunctionalityBinder(appKey)

        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_MODEL_ADDING,
            model.identifier.encodeHex()
        )
        functionalityBinder.bindModel(model, PresenterFunctionalityBinderCallback())
    }

    private fun unbindAppKeyFromModel(model: Model, appKey: AppKey) = Runnable {
        val functionalityBinder = FunctionalityBinder(appKey)

        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_MODEL_REMOVING,
            model.identifier.encodeHex()
        )
        functionalityBinder.unbindModel(model, PresenterFunctionalityBinderCallback())
    }

    private fun addSubscriptionSettings(model: Model, group: Group) = Runnable {
        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_SUBSCRIPTION_ADDING,
            model.identifier.encodeHex()
        )
        Subscription.add(model, group.address).onFailure {
            showErrorMessageWithRetryButton(it)
            return@Runnable
        }
        viewModelScope.launch {
            val response = Subscription.subscriptionResponse.first()

            if (response.status.isSuccess()) takeNextTask()
            else retryTask()
        }
    }

    private fun removeSubscriptionSettings(model: Model, group: Group) = Runnable {
        setLoadingDialogMessageWithSteps(
            LoadingDialogMessage.CONFIG_SUBSCRIPTION_REMOVING,
            model.identifier.encodeHex()
        )
        Subscription.delete(model, group.address).onFailure {
            showErrorMessageWithRetryButton(it)
            return@Runnable
        }

        viewModelScope.launch {
            val response = Subscription.subscriptionResponse.first()

            if (response.status.isSuccess()) takeNextTask()
            else retryTask()
        }
    }

    fun updateCompositionData() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_DCD_GETTING)
        configurationControl.getDeviceCompositionData(0, object : GetDeviceCompositionDataCallback {
            override fun success(compositionData: ByteArray) {
                meshNode.node.overrideDeviceCompositionData(compositionData)
                refreshView()
                deviceConfigView?.dismissLoadingDialog()
            }

            override fun error(error: NodeControlError) {
                showErrorMessage(error)
            }
        })
    }

    fun changeName(name: String) {
        try {
            meshNode.node.name = name
        } catch (e: Exception) {
            deviceConfigView?.showLoadingDialog()
            deviceConfigView?.setLoadingDialogMessage(
                NodeControlError.CannotSaveToDatabase,
                showCloseButton = true
            )
        }
    }

    private fun changeFunctionality(functionality: DeviceFunctionality.FUNCTIONALITY) = Runnable {
        try {
            setLoadingDialogMessageWithSteps(LoadingDialogMessage.CONFIG_FUNCTIONALITY_CHANGING)
            MeshNodeManager.updateNodeFunc(meshNode, functionality)
            takeNextTask()
        } catch (e: Exception) {
            deviceConfigView?.setLoadingDialogMessage(NodeControlError.CannotSaveToDatabase)
            error(e)
        }
    }

    fun updatePollTimeout(node: Node) {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_POLL_TIMEOUT_GETTING)
        ConfigurationControl(node).getLpnPollTimeout(
            meshNode.node,
            object : LpnPollTimeoutCallback {
                override fun success(pollTimeout: Int) {
                    if (pollTimeout == 0) {
                        deviceConfigView?.showToast(DeviceConfigView.ToastMessage.POLL_TIMEOUT_NOT_FRIEND)
                    } else {
                        this@DeviceConfigNLCPresenter.pollTimeoutFriend = node
                        this@DeviceConfigNLCPresenter.pollTimeout = pollTimeout
                        deviceConfigView?.promptGlobalTimeout(pollTimeout + 1)
                        deviceConfigView?.showToast(DeviceConfigView.ToastMessage.POLL_TIMEOUT_UPDATED)
                    }
                    takeNextTask()
                }

                override fun error(error: NodeControlError) {
                    deviceConfigView?.setLoadingDialogMessage(error, showCloseButton = true)
                    abandonTasks()
                }
            })
    }

    private fun changeProxy(enabled: Boolean) {
        deviceConfigView?.showLoadingDialog()
        if (enabled) {
            deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_PROXY_ENABLING)
        } else {
            deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_PROXY_DISABLING)
        }
        configurationControl.setProxy(enabled, object : PresenterSetNodeBehaviourCallback() {
            override fun success() {
                isProxy = enabled
                takeNextTask()
            }
        })
    }

    fun updateProxy() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_PROXY_GETTING)
        configurationControl.checkProxyStatus(object : NodeBehaviourCallback() {
            override fun success(enabled: Boolean) {
                isProxy = enabled
                super.success(enabled)
            }
        })
    }

    fun changeRelay(enabled: Boolean) {
        deviceConfigView?.showLoadingDialog()
        if (enabled) {
            deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_RELAY_ENABLING)
        } else {
            deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_RELAY_DISABLING)
        }
        configurationControl.setRelay(
            enabled,
            relayRetransmitCount,
            relayRetransmitIntervalSteps,
            object : PresenterSetNodeBehaviourCallback() {
                override fun success() {
                    isRelay = enabled
                    takeNextTask()
                }
            })
    }

    fun updateRelay() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_RELAY_GETTING)
        configurationControl.checkRelayStatus(object : RelayCallback() {
            override fun success(enabled: Boolean, count: Int, interval: Int) {
                isRelay = enabled
                super.success(enabled, count, interval)
            }
        })
    }

    fun changeFriend(enabled: Boolean) {
        deviceConfigView?.showLoadingDialog()
        if (enabled) {
            deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_FRIEND_ENABLING)
        } else {
            deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_FRIEND_DISABLING)
        }
        configurationControl.setFriend(enabled, object : PresenterSetNodeBehaviourCallback() {
            override fun success() {
                isFriend = enabled
                takeNextTask()
            }
        })
    }

    fun updateFriend() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_FRIEND_GETTING)
        configurationControl.checkFriendStatus(object : NodeBehaviourCallback() {
            override fun success(enabled: Boolean) {
                isFriend = enabled
                super.success(enabled)
            }
        })
    }

    fun changeRetransmission(enabled: Boolean) {
        deviceConfigView?.showLoadingDialog()
        if (enabled) {
            deviceConfigView?.setLoadingDialogMessage(
                LoadingDialogMessage.CONFIG_RETRANSMISSION_ENABLING
            )
        } else {
            deviceConfigView?.setLoadingDialogMessage(
                LoadingDialogMessage.CONFIG_RETRANSMISSION_DISABLING
            )
        }
        val count = if (enabled) relayRetransmitCount else 0
        val interval = if (enabled) relayRetransmitIntervalSteps else 0
        configurationControl.setRetransmissionConfiguration(
            count,
            interval,
            object : NodeRetransmissionConfigurationCallback {
                override fun success(retransmissionCount: Int, retransmissionIntervalSteps: Int) {
                    isRetransmission = enabled
                    takeNextTask()
                }

                override fun error(error: NodeControlError) {
                    showErrorMessage(error)
                }
            })
    }

    fun updateRetransmission() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_RETRANSMISSION_GETTING)
        configurationControl.checkRetransmissionConfigurationStatus(object :
            NodeRetransmissionConfigurationCallback {
            override fun success(retransmissionCount: Int, retransmissionIntervalSteps: Int) {
                isRetransmission = retransmissionCount != 0
                takeNextTask()
            }

            override fun error(error: NodeControlError) {
                deviceConfigView?.setLoadingDialogMessage(error, showCloseButton = true)
                abandonTasks()
            }
        })
    }

    fun changeAdvertisementExtensionForDistributorRole(state: Boolean) {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(
            if (state) {
                LoadingDialogMessage.CONFIG_AE_ENABLING_ON_BLOB_CLIENT_MODEL
            } else {
                LoadingDialogMessage.CONFIG_AE_DISABLING_ON_BLOB_CLIENT_MODEL
            },
            ModelIdentifier.BlobTransferClient.id.toShort().encodeHex()
        )

        Logger.debug { "AE: set AE transmission state on a BLOB client model" }
        AdvertisementExtensionHelper.setUsageForRemoteBlobClient(meshNode.node, state)

        deviceConfigView?.scope?.launch {
            val response = fetchRemoteModelState()
            if (response != null && response.status == ConfigurationStatus.Ok) {
                isAdvertisementExtensionOnBlobClientModelEnabled = response.state
                takeNextTask()
            } else {
                deviceConfigView?.setLoadingDialogMessage(
                    "Setting the Advertisement Extension message transmission setting on a BLOB client model failed (%s)"
                        .format(response?.status ?: "timeout"),
                    showCloseButton = true
                )
                abandonTasks()
            }
        }
    }

    fun updateAdvertisementExtensionForDistributorRole() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(
            LoadingDialogMessage.CONFIG_AE_GETTING_ON_BLOB_CLIENT_MODEL,
            ModelIdentifier.BlobTransferClient.id.toShort().encodeHex()
        )

        Logger.debug { "AE: get AE transmission state on a BLOB client model" }
        AdvertisementExtensionHelper.getUsageForRemoteBlobClient(meshNode.node)

        deviceConfigView?.scope?.launch {
            val response = fetchRemoteModelState()
            if (response != null && response.status == ConfigurationStatus.Ok) {
                isAdvertisementExtensionOnBlobClientModelEnabled = response.state
                takeNextTask()
            } else {
                deviceConfigView?.setLoadingDialogMessage(
                    "Fetching the Advertisement Extension message transmission setting on a BLOB client model failed (%s)"
                        .format(response?.status ?: "timeout"),
                    showCloseButton = true
                )
                abandonTasks()
            }
        }
    }

    fun changeLpnGlobalTimeout(lpnGlobalTimeoutSecsText: String) {
        val lpnGlobalTimeout: Int
        try {
            val lpnGlobalTimeoutMsText = lpnGlobalTimeoutSecsText.plus("000") // milliseconds
            lpnGlobalTimeout = lpnGlobalTimeoutMsText.toInt()
        } catch (e: NumberFormatException) {
            deviceConfigView?.showToast(DeviceConfigView.ToastMessage.LPN_TIMEOUT_WRONG_RANGE)
            return
        }

        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_LPN_TIMEOUT_SETTING)
        ConfigurationControlSettings().lpnLocalTimeout = lpnGlobalTimeout
        this.lpnGlobalTimeout = convertMsToS(lpnGlobalTimeout)
        takeNextTask()
    }

    private fun updateLpnGlobalTimeout() {
        deviceConfigView?.showLoadingDialog()
        deviceConfigView?.setLoadingDialogMessage(LoadingDialogMessage.CONFIG_LPN_TIMEOUT_GETTING)
        lpnGlobalTimeout = convertMsToS(ConfigurationControlSettings().lpnLocalTimeout)
        takeNextTask()
    }

    private fun setLoadingDialogMessageWithSteps(
        loadingMessage: LoadingDialogMessage,
        message: String = "",
    ) {
        deviceConfigView?.setLoadingDialogMessage(
            loadingMessage,
            message,
            taskList.size,
            allTasksCount
        )
    }

    private fun convertMsToS(milliseconds: Int) = milliseconds / 1000

    // callback classes

    inner class PresenterNodeControlCallback : NodeControlCallback {
        override fun succeed() {
            takeNextTask()
        }

        override fun error(error: NodeControlError) {
            showErrorMessageWithRetryButton(error)
        }
    }

    inner class PresenterFunctionalityBinderCallback : FunctionalityBinderCallback {
        override fun succeed(succeededModels: MutableList<Model>, appKey: AppKey) {
            takeNextTask()
        }

        override fun error(
            failedModels: MutableList<Model>,
            appKey: AppKey?,
            error: NodeControlError,
        ) {
            showErrorMessageWithRetryButton(error)
        }
    }

    abstract inner class NodeBehaviourCallback : CheckNodeBehaviourCallback {
        override fun success(enabled: Boolean) {
            takeNextTask()
        }

        override fun error(error: NodeControlError) {
            deviceConfigView?.setLoadingDialogMessage(error, showCloseButton = true)
            abandonTasks()
        }
    }

    abstract inner class RelayCallback : CheckRelayCallback {
        override fun success(enabled: Boolean, count: Int, interval: Int) {
            takeNextTask()
        }

        override fun error(error: NodeControlError) {
            deviceConfigView?.setLoadingDialogMessage(error, showCloseButton = true)
            abandonTasks()
        }
    }

    abstract inner class PresenterSetNodeBehaviourCallback : SetNodeBehaviourCallback {
        override fun error(error: NodeControlError) {
            showErrorMessage(error)
        }
    }
}
