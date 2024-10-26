/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.AppKeysList

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface AppKeysListView : PresenterView{
    fun refreshAppKeys(appKeys: Set<AppKey>)

    fun showToast(message: String)

    fun showDeleteAppKeyLocallyDialog(appKey: AppKey, failedNodes: List<Node>)

    fun showLoadingDialog()

    fun setRemovingAppKeyMessage(appKeyName: String)

    fun dismissLoadingDialog()
}
