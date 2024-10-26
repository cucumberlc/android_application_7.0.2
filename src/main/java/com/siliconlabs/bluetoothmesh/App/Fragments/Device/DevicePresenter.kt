/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device

import androidx.lifecycle.SavedStateHandle
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevicePresenter @Inject constructor(
    val meshNode: MeshNode,
    private val networkConnectionLogic: NetworkConnectionLogic,
    savedStateHandle: SavedStateHandle,
) : BasePresenter<DeviceView>(), NetworkConnectionListener {
    private val deviceView
        get() = view

    val isFirstConfiguration: Boolean = savedStateHandle[DeviceFragment.KEY_IS_FIRST_CONFIG]!!

    override fun onResume() {
        networkConnectionLogic.addListener(this)
    }

    override fun onPause() {
        networkConnectionLogic.removeListener(this)
    }

    // NetworkConnectionListener
    override fun connecting() {
        deviceView?.showLoadingDialog()
        deviceView?.setLoadingDialogMessage(DeviceView.LOADING_DIALOG_MESSAGE.CONFIG_CONNECTING)
    }

    override fun connected() {
        deviceView?.dismissLoadingDialog()
    }

    override fun disconnected() {
        deviceView?.showLoadingDialog()
        deviceView?.setLoadingDialogMessage(DeviceView.LOADING_DIALOG_MESSAGE.CONFIG_DISCONNECTED)
    }

    override fun connectionErrorMessage(error: ConnectionError) {
        deviceView?.setLoadingDialogMessage(error)
    }

    fun disconnectFromSubnet() {
        if (isFirstConfiguration && AppState.isProcessingDeviceDirectly()) {
            networkConnectionLogic.disconnect()
        }
    }
}
