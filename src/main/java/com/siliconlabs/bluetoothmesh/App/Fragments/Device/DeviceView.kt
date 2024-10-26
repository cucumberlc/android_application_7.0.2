/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device

import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface DeviceView : PresenterView {
    fun showLoadingDialog()

    fun setLoadingDialogMessage(connectionError: ConnectionError)

    fun setLoadingDialogMessage(loadingMessage: LOADING_DIALOG_MESSAGE)

    fun dismissLoadingDialog()

    enum class LOADING_DIALOG_MESSAGE {
        CONFIG_CONNECTING,
        CONFIG_DISCONNECTED,
    }
}