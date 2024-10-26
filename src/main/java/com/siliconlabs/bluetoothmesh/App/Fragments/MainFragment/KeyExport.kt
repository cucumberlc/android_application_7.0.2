/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.MainFragment

import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.configuration_control.iv_index.IvIndexControl
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import org.json.JSONArray
import org.json.JSONObject

object KeyExport {
    fun export(): String {
        val netKeyArray = JSONArray()
        val appKeyArray = JSONArray()
        val devKeyArray = JSONArray()
        for (subnet in BluetoothMesh.network.subnets) {
            netKeyArray.put(createNetKey(subnet))
            for (appKey in subnet.appKeys) {
                appKeyArray.put(createAppKey(appKey))
            }
            for (node in subnet.nodes) {
                devKeyArray.put(createDevKey(node))
            }
        }
        val keys = JSONObject()
        keys.put("netKeys", netKeyArray)
        keys.put("devKeys", devKeyArray)
        keys.put("appKeys", appKeyArray)
        keys.put("ivIndex", IvIndexControl().value)
        return keys.toString()
    }

    private fun createNetKey(subnet: Subnet): JSONObject {
        val net = JSONObject()
        val netKey = subnet.netKey
        net.put("index", netKey.index.toString())
        net.put("value", convertByteToNoSpacesHex(netKey.key))
        netKey.oldKey?.let {
            net.put("oldValue", convertByteToNoSpacesHex(it))
        }
        return net
    }

    private fun createAppKey(appKey: AppKey): JSONObject {
        val app = JSONObject()
        app.put("boundNetKeyIndex", appKey.subnet.netKey.index.toString())
        app.put("index", appKey.index.toString())
        app.put("value", convertByteToNoSpacesHex(appKey.key))
        appKey.oldKey?.let {
            app.put("oldValue", convertByteToNoSpacesHex(it))
        }
        return app
    }

    private fun createDevKey(node: Node): JSONObject {
        val dev = JSONObject()
        dev.put("primaryAddress", node.primaryElementAddress.toString())
        dev.put("value", convertByteToNoSpacesHex(node.devKey.key))
        dev.put("uuid", node.uuid)
        return dev
    }

    private fun convertByteToNoSpacesHex(bytes: ByteArray): String {
        val stringBuilder = StringBuilder()
        for (b in bytes) {
            stringBuilder.append(String.format("%02X", b))
        }
        return stringBuilder.toString()
    }
}