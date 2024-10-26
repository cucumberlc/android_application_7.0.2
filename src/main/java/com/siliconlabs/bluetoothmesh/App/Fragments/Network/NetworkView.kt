/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Network

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface NetworkView : PresenterView {
    fun showDeleteSubnetLocallyDialog(subnet: Subnet, failedNodes: List<Node>)
    fun showDeleteSubnetLocallyDialog(subnet: Subnet, connectionError: ConnectionError)

    fun showToast(message: String)

    fun setSubnetsList(subnets: Set<Subnet>)

    fun showLoadingDialog()

    fun updateLoadingDialogMessage(loadingMessage: LoadingDialogMessage, message: String = "", showCloseButton: Boolean = false)

    fun dismissLoadingDialog()

    enum class LoadingDialogMessage {
        CONNECTING_TO_SUBNET,
        REMOVING_SUBNET,
    }
}
