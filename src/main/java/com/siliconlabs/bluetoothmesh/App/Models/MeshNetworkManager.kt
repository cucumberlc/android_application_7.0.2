/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.configuration_control.ConfigurationControl
import com.siliconlab.bluetoothmesh.adk.configuration_control.FactoryResetCallback
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.node_control.NodeControl
import com.siliconlab.bluetoothmesh.adk.node_control.NodeControlCallback
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.onSuccess
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object MeshNetworkManager {
    private var subnet: Subnet? = null

    /**
     * Ensure demo app has at least 1 subnet, group and appkey.
     * */
    fun createDefaultStructure() {
            GlobalScope.launch {
            val taskA = listOf( async { createDefaultSubnet() }, async { createDefaultGroup() } )
            taskA.awaitAll()
            val taskB = listOf( async { createDefaultSubnetNLC() }, async { createDefaultAppKey() } )
            taskB.awaitAll()
            createDefaultAppKeyNLC()
        }
    }

    private fun createDefaultSubnet() {
        if (BluetoothMesh.network.subnets.isEmpty()) {
            BluetoothMesh.network.createSubnet()
                .onSuccess { subnet = it }
                .onFailure { Logger.error { it.toString() } }
        } else {
            subnet = BluetoothMesh.network.subnets.first()
        }
    }

    private fun createDefaultSubnetNLC() {
        if (BluetoothMesh.network.subnets.size <= 1) {
            DeviceFunctionalityDb.saveFirstTimeLaunch(false)
            BluetoothMesh.network.createSubnet()
                .onSuccess { subnet = it }
                .onFailure { Logger.error { it.toString() } }
        } else {
            subnet = BluetoothMesh.network.subnets.first()
        }
    }

    private fun createDefaultGroup() {
        if (BluetoothMesh.network.groups.isEmpty()) {
            BluetoothMesh.network.createGroup().onFailure {
                Logger.error { it.toString() }
            }
        }
    }

    private fun createDefaultAppKey() {
        subnet?.also { subnet ->
            if (subnet.appKeys.isEmpty()) {
                subnet.createAppKey()
                    .onFailure { Logger.error { it.toString() } }
            }
        } ?: Logger.error("There is no subnet to create an AppKey in")
    }

    private fun createDefaultAppKeyNLC() {
        subnet?.also { subnet ->
            if (subnet.appKeys.isEmpty()) {
                subnet.createAppKey()
                    .onFailure { Logger.error { it.toString() } }
            }
        } ?: Logger.error("There is no subnet to create an AppKey in")
    }

    suspend fun removeSubnet(subnet: Subnet): RemovalResult {
        if (subnet.nodes.isEmpty()) {
            forceRemoveSubnet(subnet)
            return RemovalResult.Success
        }

        val nodesToReset = subnet.nodes
        val failedNodes = mutableListOf<Node>()

        for (i in nodesToReset.size - 1 downTo 0) {
            val isReset = factoryResetNode(nodesToReset.elementAt(i))
            if (!isReset)
                failedNodes.add(nodesToReset.elementAt(i))
        }

        return if (failedNodes.isEmpty()) {
            forceRemoveSubnet(subnet)
            RemovalResult.Success
        } else {
            Logger.debug { "removeSubnet failed $failedNodes" }
            RemovalResult.Failure(failedNodes)
        }
    }

    private suspend fun factoryResetNode(node: Node) = suspendCoroutine<Boolean> {
        ConfigurationControl(node).factoryReset(object : FactoryResetCallback {
            override fun success() {
                Logger.debug { "$node has been successfully reset." }
                it.resume(true)
            }

            override fun error(error: NodeControlError) {
                it.resume(false)
            }
        })
    }

    private fun forceRemoveSubnet(subnet: Subnet) {
        BluetoothMesh.network.removeSubnet(subnet)
    }

    suspend fun removeAppKey(appKey: AppKey): RemovalResult {
        if (appKey.nodes.isEmpty()) {
            forceRemoveAppKey(appKey)
            return RemovalResult.Success
        }

        val failedNodes = mutableListOf<Node>()
        val nodesToUnbind = appKey.nodes
        for (i in nodesToUnbind.size - 1 downTo 0) {
            val unboundSuccess = unbindAppKeyFromNode(nodesToUnbind.elementAt(i), appKey)
            if (!unboundSuccess)
                failedNodes.add(nodesToUnbind.elementAt(i))
        }

        return if (failedNodes.isEmpty()) {
            forceRemoveAppKey(appKey)
            RemovalResult.Success
        } else {
            Logger.debug { "removeAppKey failed $failedNodes" }
            RemovalResult.Failure(failedNodes)
        }
    }

    private suspend fun unbindAppKeyFromNode(node: Node, appKey: AppKey) =
        suspendCoroutine<Boolean> {
            NodeControl(node).unbindAppKey(appKey, object : NodeControlCallback {
                override fun succeed() {
                    Logger.debug { "$appKey has been successfully unbind from node." }
                    it.resume(true)
                }

                override fun error(error: NodeControlError) {
                    it.resume(false)
                }
            })
        }

    private fun forceRemoveAppKey(appKey: AppKey) {
        appKey.subnet.removeAppKey(appKey)
    }
}
