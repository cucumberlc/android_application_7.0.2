/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ControlGroup

import android.view.View
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.FactoryResetCallback
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.functionality_control.generic.GenericClient
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.DeviceListAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.ScannerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Scheduler.SchedulerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.TimeControl.TimeControlFragment
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Utils.ControlConverters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ControlGroupPresenter @Inject constructor(
    val networkConnectionLogic: NetworkConnectionLogic,
    val subnet: Subnet,
    val appKey: AppKey
) : BasePresenter<ControlGroupView>(), NetworkConnectionListener, DeviceListAdapter.DeviceListAdapterListener {
    private val controlGroupView
        get() = view

    var nodes: Set<MeshNode> = emptySet()
    private var isChecked = false

    override fun onResume() {
        networkConnectionLogic.addListener(this)
        controlGroupView?.setMasterControlEnabled(false)
        controlGroupView?.setMasterControlVisibility(View.GONE)
        refreshList()
    }

    override fun onPause() {
        networkConnectionLogic.removeListener(this)
    }

    fun refreshList() {
        nodes = MeshNodeManager.getMeshNodes(appKey)
        controlGroupView?.setDevicesList(nodes)
        if (nodes.isEmpty()) {
            controlGroupView?.setMasterControlVisibility(View.GONE)
        } else {
            controlGroupView?.setMasterControlVisibility(View.VISIBLE)
            isChecked = nodes.any { it.lightnessPercentage > 0 }
            controlGroupView?.setMasterSwitch(isChecked)
            onMasterSwitchChanged()
        }
    }

    private fun adjustMasterControl(level: Int) {
        isChecked = level > 0
        controlGroupView?.setMasterSwitch(isChecked)
        controlGroupView?.setMasterLevel(level)
    }

    // NetworkConnectionListener

    override fun connecting() {
        controlGroupView?.setMeshIconState(ControlGroupView.MeshIconState.CONNECTING)
        refreshList()
    }

    override fun connected() {
        controlGroupView?.setMeshIconState(ControlGroupView.MeshIconState.CONNECTED)
        controlGroupView?.setMasterControlEnabled(true)
        refreshList()
    }

    override fun disconnected() {
        controlGroupView?.setMeshIconState(ControlGroupView.MeshIconState.DISCONNECTED)
        controlGroupView?.setMasterControlEnabled(false)
        refreshList()
    }

    override fun connectionErrorMessage(error: ConnectionError) {
        controlGroupView?.showToast(error)
    }

    // View callbacks

    fun meshIconClicked(iconState: ControlGroupView.MeshIconState) {
        when (iconState) {
            ControlGroupView.MeshIconState.DISCONNECTED -> {
                networkConnectionLogic.connect(subnet)
            }
            ControlGroupView.MeshIconState.CONNECTING -> {
                networkConnectionLogic.disconnect()
            }
            ControlGroupView.MeshIconState.CONNECTED -> {
                networkConnectionLogic.disconnect()
            }
        }
    }

    fun onMasterSwitchChanged() {
        val level = if (isChecked) 0 else 100
        onMasterLevelChanged(level)
    }

    fun onMasterLevelChanged(percentage: Int) {
        val onOffExist = nodes.any { it.functionality.getAllModels().contains(ModelIdentifier.GenericOnOffServer) }
        val levelExist = nodes.any { it.functionality.getAllModels().contains(ModelIdentifier.GenericLevelServer) }
        val lightnessExist = nodes.any { it.functionality.getAllModels().contains(ModelIdentifier.LightLightnessServer) }

        if (onOffExist) {
            GenericClient.setOnOff(
                appKey,
                BluetoothMesh.network.groups.first().address,
                percentage > 0,
                1,
                1,
                false,
                ++transactionId,
                false,
            ).onFailure { controlGroupView?.showToast(it) }
        }
        if (levelExist) {
            GenericClient.setLevel(
                appKey,
                BluetoothMesh.network.groups.first().address,
                ControlConverters.getLevel(percentage),
                1,
                1,
                false,
                ++transactionId,
                false,
            ).onFailure{ controlGroupView?.showToast(it) }
        }
        if (lightnessExist) {
            GenericClient.setLightnessActual(
                appKey,
                BluetoothMesh.network.groups.first().address,
                ControlConverters.getLightness(percentage),
                1,
                1,
                false,
                ++transactionId,
                false,
            ).onFailure { controlGroupView?.showToast(it) }
        }

        nodes.forEach {
            it.onOffState = percentage > 0
            it.levelPercentage = percentage
            it.lightnessPercentage = percentage
        }

        adjustMasterControl(percentage)
        controlGroupView?.refreshView()
    }

    // Device list adapter listener

    override fun onDeleteClicked(node: Node) {
        if (networkConnectionLogic.isConnected()) {
            controlGroupView?.showDeleteDeviceDialog(node)
        } else {
            controlGroupView?.showDeleteDeviceLocallyDialog(
                "Error: Not connected to subnet",
                node
            )
        }
    }

    override fun onConfigureClicked(meshNode: MeshNode) {
        controlGroupView?.showDeviceConfiguration(meshNode)
    }

    override fun onFunctionalityClicked(meshNode: MeshNode, functionality: DeviceFunctionality.FUNCTIONALITY) {
        when (functionality) {
            DeviceFunctionality.FUNCTIONALITY.TimeServer -> TimeControlFragment.newInstance(meshNode)
            DeviceFunctionality.FUNCTIONALITY.Scheduler -> SchedulerFragment.newInstance(meshNode)
            else -> null
        }?.let { fragmentToOpen ->
            controlGroupView?.showFragment(fragmentToOpen)
        }
    }

    override fun onUpdateFirmwareClick(node: Node) {
        controlGroupView?.navigateToDistributionFragment(node)
    }

    override fun onRemoteProvisionClick(node: Node) {
        controlGroupView?.showFragment(ScannerFragment.newRemoteInstance(node, subnet))
    }

    //group control callback

    fun deleteDeviceLocally(node: Node) {
        node.removeOnlyFromLocalStructure()
        MeshNodeManager.removeMeshNode(node)
        refreshList()
    }

    fun deleteDevice(node: Node) {
        controlGroupView?.showProgressBar()
        ConfigurationControl(node).factoryReset(object : FactoryResetCallback {
            override fun success() {
                MeshNodeManager.removeMeshNode(node)
                controlGroupView?.hideProgressBar()
                refreshList()
            }

            override fun error(error: NodeControlError) {
                controlGroupView?.hideProgressBar()
                controlGroupView?.showDeleteDeviceLocallyDialog(error.toString(), node)
            }
        })
    }

    companion object {
        private var transactionId: UByte = 0u
    }
}