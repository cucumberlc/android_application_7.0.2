/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList

import androidx.fragment.app.Fragment
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface DeviceListView : PresenterView {
    fun setDevicesList(meshNodes: Set<MeshNode>)

    fun notifyDataSetChanged()

    fun navigateToDistributionFragment(distributor: Node)

    fun showDeleteDeviceDialog(node: Node)

    fun showProgressBar()

    fun hideProgressBar()

    fun showDeviceConfiguration(meshNode: MeshNode)

    fun showErrorToast(errorType: NodeControlError)

    fun showFragment(fragment: Fragment)

    fun showDeleteDeviceLocallyDialog(description: String, node: Node)
}