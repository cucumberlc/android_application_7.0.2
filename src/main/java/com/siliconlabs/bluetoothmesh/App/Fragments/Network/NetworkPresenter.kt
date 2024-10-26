/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Network

import androidx.lifecycle.viewModelScope
import com.jcabi.aspects.Loggable
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.MeshNetworkManager
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Models.RemovalResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class NetworkPresenter @Inject constructor(
    val networkConnectionLogic: NetworkConnectionLogic,
) : BasePresenter<NetworkView>() {
    private val networkView
        get() = view

    override fun onResume() {
        refreshList()
    }

    fun refreshList() {
        networkView?.setSubnetsList(BluetoothMesh.network.subnets)
    }

    // View callbacks
    fun addSubnet() {
        BluetoothMesh.network.createSubnet()
            .onFailure { networkView?.showToast(it.toString()) }
        refreshList()
    }

    fun deleteSubnet(subnet: Subnet) {
        networkView?.showLoadingDialog()
        if (subnet.nodes.isEmpty()) {
            removeSubnet(subnet)
        } else {
            removeSubnetWithNodes(subnet)
        }
    }

    fun deleteSubnetLocally(subnet: Subnet, failedNodes: List<Node> = emptyList()) {
        val nodeUuids = subnet.nodes.map { it.uuid } + failedNodes.map { it.uuid }
        MeshNodeManager.removeMeshNodesOfSubnet(nodeUuids)
        BluetoothMesh.network.removeSubnet(subnet)
        refreshList()
    }

    private fun removeSubnet(subnet: Subnet) {
        viewModelScope.launch {
            when (val result = MeshNetworkManager.removeSubnet(subnet)) {
                RemovalResult.Success -> {
                    Logger.debug("removeSubnet success")
                    networkView?.dismissLoadingDialog()
                    refreshList()
                }

                is RemovalResult.Failure -> {
                    Logger.debug("removeSubnet error")
                    networkView?.dismissLoadingDialog()
                    networkView?.showDeleteSubnetLocallyDialog(subnet, result.failedNodes)
                    refreshList()
                }
            }
        }
    }

    private fun removeSubnetWithNodes(subnet: Subnet) {
        val networkConnectionListener = object : NetworkConnectionListener {
            fun clear() {
                refreshList()
                networkConnectionLogic.disconnect()
                networkConnectionLogic.removeListener(this)
            }

            @Loggable
            override fun connecting() {
                networkView?.updateLoadingDialogMessage(
                    NetworkView.LoadingDialogMessage.CONNECTING_TO_SUBNET,
                    subnet.netKey.index.toString()
                )
            }

            @Loggable
            override fun connected() {
                networkView?.updateLoadingDialogMessage(
                    NetworkView.LoadingDialogMessage.REMOVING_SUBNET,
                    subnet.netKey.index.toString()
                )
                viewModelScope.launch {
                    val nodeUuids = subnet.nodes.map { it.uuid }
                    when (val result = MeshNetworkManager.removeSubnet(subnet)) {
                        RemovalResult.Success -> {
                            Logger.debug { "removeSubnet success" }
                            networkConnectionLogic.disconnect()
                            MeshNodeManager.removeMeshNodesOfSubnet(nodeUuids)
                            networkView?.dismissLoadingDialog()
                            refreshList()
                        }

                        is RemovalResult.Failure -> {
                            Logger.debug { "removeSubnet error" }
                            networkView?.dismissLoadingDialog()
                            networkView?.showDeleteSubnetLocallyDialog(subnet, result.failedNodes)
                            clear()
                        }
                    }
                }
                networkConnectionLogic.removeListener(this)
            }

            @Loggable
            override fun disconnected() { }

            @Loggable
            override fun connectionErrorMessage(error: ConnectionError) {
                clear()
                networkView?.dismissLoadingDialog()
                networkView?.showDeleteSubnetLocallyDialog(subnet, error)
            }
        }
        networkConnectionLogic.addListener(networkConnectionListener)
        if(networkConnectionLogic.isConnectedTo(subnet)){
            networkConnectionListener.connected()
        } else networkConnectionLogic.connect(subnet)
    }
}