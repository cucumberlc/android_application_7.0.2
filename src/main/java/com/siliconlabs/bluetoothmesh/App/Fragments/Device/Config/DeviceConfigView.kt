/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config

import android.content.DialogInterface
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceConfig
import com.siliconlabs.bluetoothmesh.App.PresenterView
import kotlinx.coroutines.CoroutineScope

interface DeviceConfigView : PresenterView {

    fun setDeviceConfig(
        meshNode: MeshNode,
        deviceConfig: DeviceConfig,
        appKeysInSubnet: List<AppKey>,
        nodes: List<Node>
    )

    fun showToast(message: ToastMessage)

    fun showLoadingDialog()

    fun setLoadingDialogMessage(message: String, showCloseButton: Boolean = false)

    fun setLoadingDialogMessage(error: NodeControlError, showCloseButton: Boolean = false)

    fun setLoadingDialogMessage(loadingMessage: LoadingDialogMessage, message: String = "")

    fun setLoadingDialogMessage(loadingMessage: LoadingDialogMessage, message: String, leftTasksCount: Int, allTasksCount: Int)

    fun showRetryButton()

    fun dismissLoadingDialog()

    fun showDisableProxyAttentionDialog(onClickListener: DialogInterface.OnClickListener)

    fun promptGlobalTimeout(timeout: Int)

    val scope: CoroutineScope

    enum class LoadingDialogMessage {
        CONFIG_ADDING_APPKEY_TO_NODE,
        CONFIG_REMOVING_APPKEY_FROM_NODE,
        CONFIG_PROXY_ENABLING,
        CONFIG_PROXY_DISABLING,
        CONFIG_PROXY_GETTING,
        CONFIG_MODEL_ADDING,
        CONFIG_MODEL_REMOVING,
        CONFIG_SUBSCRIPTION_ADDING,
        CONFIG_SUBSCRIPTION_REMOVING,
        CONFIG_PUBLICATION_SETTING,
        CONFIG_PUBLICATION_CLEARING,
        CONFIG_FUNCTIONALITY_CHANGING,
        CONFIG_FRIEND_ENABLING,
        CONFIG_FRIEND_DISABLING,
        CONFIG_FRIEND_GETTING,
        CONFIG_RETRANSMISSION_ENABLING,
        CONFIG_RETRANSMISSION_DISABLING,
        CONFIG_RETRANSMISSION_GETTING,
        CONFIG_RELAY_ENABLING,
        CONFIG_RELAY_DISABLING,
        CONFIG_RELAY_GETTING,
        CONFIG_POLL_TIMEOUT_GETTING,
        CONFIG_LPN_TIMEOUT_SETTING,
        CONFIG_LPN_TIMEOUT_GETTING,
        CONFIG_DCD_GETTING,
        CONFIG_AE_SETTING_CONFIGURATION,
        CONFIG_AE_SETTING_PDU,
        CONFIG_AE_ENABLING_ON_BLOB_CLIENT_MODEL,
        CONFIG_AE_DISABLING_ON_BLOB_CLIENT_MODEL,
        CONFIG_AE_GETTING_ON_BLOB_CLIENT_MODEL
    }

    enum class ToastMessage {
        ERROR_MISSING_APPKEY,
        POLL_TIMEOUT_UPDATED,
        POLL_TIMEOUT_NOT_FRIEND,
        LPN_TIMEOUT_WRONG_RANGE
    }
}