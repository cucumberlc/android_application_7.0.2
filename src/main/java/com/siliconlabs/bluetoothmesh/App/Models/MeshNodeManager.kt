/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import java.util.UUID

object MeshNodeManager {

    private val meshNodes = mutableMapOf<Node, MeshNode>()

    fun getMeshNode(node: Node): MeshNode {
        return wrapNode(node)
    }

    fun getMeshNodes(subnet: Subnet): Set<MeshNode> {
        return wrapNodes(subnet.nodes)
    }

    fun getMeshNodes(appKey: AppKey): Set<MeshNode> {
        return wrapNodes(appKey.nodes.toSet())
    }

    private fun wrapNodes(nodes: Set<Node>): Set<MeshNode> {
        val result = mutableSetOf<MeshNode>()
        nodes.forEach { node ->
            result.add(wrapNode(node))
        }
        return result
    }

    private fun wrapNode(node: Node): MeshNode {
        var meshNode: MeshNode? = meshNodes[node]
        if (meshNode == null) {
            meshNode = MeshNode(node)
            if (node.boundAppKeys.isEmpty()) {    // edge case
                removeNodeFunc(meshNode)
            } else {
                meshNode.functionality = DeviceFunctionalityDb.get(node)
            }
            meshNodes[node] = meshNode
        }

        return meshNode
    }

    fun updateNodeFunc(meshNode: MeshNode, functionality: DeviceFunctionality.FUNCTIONALITY) {
        meshNode.functionality = functionality
        if (functionality != DeviceFunctionality.FUNCTIONALITY.Unknown) {
            DeviceFunctionalityDb.save(meshNode)
        } else {
            DeviceFunctionalityDb.remove(meshNode)
        }
    }

    fun removeNodeFunc(meshNode: MeshNode) {
        meshNode.functionality = DeviceFunctionality.FUNCTIONALITY.Unknown
        DeviceFunctionalityDb.remove(meshNode)
    }

    fun removeMeshNode(node: Node) {
        meshNodes.remove(node)?.also {
            removeNodeFunc(it)
        } ?: DeviceFunctionalityDb.remove(node.uuid) // needed if node exists in db but wasn't initialized in meshNodes
    }

    fun removeMeshNode(nodeUuid: UUID) {
        meshNodes.keys
            .find { it.uuid == nodeUuid }
            ?.also {
                removeMeshNode(it)
            } ?: DeviceFunctionalityDb.remove(nodeUuid)
    }

    fun removeMeshNodesOfSubnet(nodeUuids: List<UUID>) {
        meshNodes.keys
            .filter { it.uuid in nodeUuids }
            .forEach { removeMeshNode(it) }
    }
}
