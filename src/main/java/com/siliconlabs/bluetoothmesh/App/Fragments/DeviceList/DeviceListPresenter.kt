/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList

import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.FactoryResetCallback
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.ScannerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Scheduler.SchedulerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.TimeControl.TimeControlFragment
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DeviceListPresenter @Inject constructor(
    val networkConnectionLogic: NetworkConnectionLogic,
    val subnet: Subnet
) : BasePresenter<DeviceListView>(),
    DeviceListAdapter.DeviceListAdapterListener,
    NetworkConnectionListener {

    private val deviceListView
        get() = view

    init {
        networkConnectionLogic.addListener(this)
    }

    override fun onResume() {
        refreshList()
    }

    override fun onCleared() {
        super.onCleared()
        networkConnectionLogic.removeListener(this)
    }

    fun deleteDeviceLocally(node: Node) {
        node.removeOnlyFromLocalStructure()
        MeshNodeManager.removeMeshNode(node)
        refreshList()
    }

    fun refreshList() {
        for (node in MeshNodeManager.getMeshNodes(subnet)){
           if(node.node.boundAppKeys != null && node.node.boundAppKeys.isEmpty()){
               node.functionality = DeviceFunctionality.FUNCTIONALITY.Unknown
           }
        }
        val meshNodes = MeshNodeManager.getMeshNodes(subnet)
        deviceListView?.setDevicesList(meshNodes)
    }

    fun deleteDevice(node: Node) {
        deviceListView?.showProgressBar()
        ConfigurationControl(node).factoryReset(object : FactoryResetCallback {
            override fun success() {
                MeshNodeManager.removeMeshNode(node)
                deviceListView?.hideProgressBar()
                refreshList()
            }

            override fun error(error: NodeControlError) {
                deviceListView?.hideProgressBar()
                deviceListView?.showDeleteDeviceLocallyDialog(error.toString(), node)
            }
        })
    }

    // device list adapter listener

    override fun onDeleteClicked(node: Node) {
        if (networkConnectionLogic.isConnected()) {
            deviceListView?.showDeleteDeviceDialog(node)
        } else {
            deviceListView?.showDeleteDeviceLocallyDialog(
                "Error: Not connected to subnet",
                node
            )
        }
    }

    override fun onConfigureClicked(meshNode: MeshNode) {
        deviceListView?.showDeviceConfiguration(meshNode)
    }

    override fun onFunctionalityClicked(
        meshNode: MeshNode,
        functionality: DeviceFunctionality.FUNCTIONALITY
    ) {
        when (functionality) {
            DeviceFunctionality.FUNCTIONALITY.TimeServer -> TimeControlFragment.newInstance(meshNode)
            DeviceFunctionality.FUNCTIONALITY.Scheduler -> SchedulerFragment.newInstance(meshNode)
            else -> null
        }?.let { fragmentToOpen ->
            deviceListView?.showFragment(fragmentToOpen)
        }
    }

    override fun onUpdateFirmwareClick(node: Node) {
        deviceListView?.navigateToDistributionFragment(node)
    }

    override fun onRemoteProvisionClick(node: Node) {
        deviceListView?.showFragment(ScannerFragment.newRemoteInstance(node, subnet))
    }

    // network connection callback

    override fun connecting() {
        deviceListView?.notifyDataSetChanged()
    }

    override fun connected() {
        deviceListView?.notifyDataSetChanged()
    }

    override fun disconnected() {
        deviceListView?.notifyDataSetChanged()
    }
}
