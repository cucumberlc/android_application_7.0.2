/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.AppKeysList

import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Models.MeshNetworkManager
import com.siliconlabs.bluetoothmesh.App.Models.RemovalResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppKeysListPresenter @Inject constructor(
    private val subnet: Subnet,
) : BasePresenter<AppKeysListView>() {
    private val appKeysListView
        get() = view

    fun refreshList() {
        appKeysListView?.refreshAppKeys(subnet.appKeys)
    }

    // View callbacks

    fun addAppKey() {
        subnet.createAppKey()
            .onFailure { appKeysListView?.showToast(it.toString()) }
        refreshList()
    }

    fun deleteAppKey(appKey: AppKey) {
        appKeysListView?.setRemovingAppKeyMessage(appKey.index.toString())
        appKeysListView?.showLoadingDialog()
        viewModelScope.launch {
            when (val result = MeshNetworkManager.removeAppKey(appKey)) {
                RemovalResult.Success -> {
                    appKeysListView?.dismissLoadingDialog()
                    refreshList()
                }
                is RemovalResult.Failure -> {
                    appKeysListView?.dismissLoadingDialog()
                    appKeysListView?.showDeleteAppKeyLocallyDialog(appKey, result.failedNodes)
                    refreshList()
                }
            }
        }
    }

    fun deleteAppKeyLocally(appKey: AppKey) {
        subnet.removeAppKey(appKey)
        refreshList()
    }
}
