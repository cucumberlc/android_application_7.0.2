/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ControlGroup

import androidx.fragment.app.Fragment
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.MeshError
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface ControlGroupView : PresenterView {

    fun refreshView()

    fun setMeshIconState(iconState: MeshIconState)

    fun setMasterSwitch(isChecked: Boolean)

    fun setMasterLevel(progress: Int)

    fun setMasterControlEnabled(enabled: Boolean)

    fun setMasterControlVisibility(visibility: Int)

    fun setDevicesList(devicesInfo: Set<MeshNode>)

    fun showToast(message: String)

    fun showToast(error: MeshError)

    fun showFragment(fragment: Fragment)

    fun showDeleteDeviceDialog(node: Node)

    fun showDeleteDeviceLocallyDialog(description: String, node: Node)

    fun showProgressBar()

    fun hideProgressBar()

    fun showDeviceConfiguration(meshNode: MeshNode)

    fun navigateToDistributionFragment(distributor : Node)

    enum class MeshIconState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}