/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.ExportImport

import android.util.Log
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.Security
import com.siliconlab.bluetoothmesh.adk.data_model.address.IntegerAddress
import com.siliconlab.bluetoothmesh.adk.functionality_control.publication.Credentials
import com.siliconlab.bluetoothmesh.adk.importer.AppKeyImport
import com.siliconlab.bluetoothmesh.adk.importer.DevKeyImport
import com.siliconlab.bluetoothmesh.adk.importer.ElementImport
import com.siliconlab.bluetoothmesh.adk.importer.FeaturesImport
import com.siliconlab.bluetoothmesh.adk.importer.GroupImport
import com.siliconlab.bluetoothmesh.adk.importer.Importer
import com.siliconlab.bluetoothmesh.adk.importer.ModelSettingsImport
import com.siliconlab.bluetoothmesh.adk.importer.NetKeyImport
import com.siliconlab.bluetoothmesh.adk.importer.NetworkImport
import com.siliconlab.bluetoothmesh.adk.importer.NodeAppKeyImport
import com.siliconlab.bluetoothmesh.adk.importer.NodeImport
import com.siliconlab.bluetoothmesh.adk.importer.NodeSecurityImport
import com.siliconlab.bluetoothmesh.adk.importer.NodeSettingsImport
import com.siliconlab.bluetoothmesh.adk.importer.ProvisionerImport
import com.siliconlab.bluetoothmesh.adk.importer.SubnetImport
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.Converter
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonAddressRange
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonElement
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonFeature
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonGroup
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonMesh
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonModel
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonNetKey
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonNode
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonPublish
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonSceneRange
import com.siliconlabs.bluetoothmesh.App.Utils.ExportImportTestFilter
import java.util.Locale
import java.util.UUID

class JsonImporter(private val jsonMesh: JsonMesh) {
    private val appKeyImports = HashSet<AppKeyImport>()
    fun import() {
        val bluetoothMesh = BluetoothMesh
        val customAddress = 0
        val customIvIndex = 0
        val importer = createImporter()

        bluetoothMesh.deinitialize();
        bluetoothMesh.initMesh();

        importer.performImport()
        val network = bluetoothMesh.network
        bluetoothMesh.initializeNetwork(network, customAddress, customIvIndex)
        Log.i(ExportImportTestFilter, "initialised network with custom address = $customAddress")
    }

    private fun createImporter(): Importer {
        val importer = Importer()
        val uuidStr = jsonMesh.meshUUID
        uuidStr!!.replace("-", "")
        importer.createNetwork(Converter.stringToUuid(uuidStr))
            .apply { name = jsonMesh.meshName.toString() }
            .also {
                handleSubnets(it)
                handleAppKeys(it)
                handleNodes(it)
                handleGroups(it)
                handleProvisioners(it)
                handleScenes(it)
            }
        return importer
    }

    private fun handleAppKeys(networkImport: NetworkImport) {
        jsonMesh.appKeys.map { jsonAppKey ->
            val appKeys = AppKeyImport(
                jsonAppKey.index,
                Converter.hexToBytes(jsonAppKey.key)!!,
                Converter.hexToBytes(jsonAppKey.oldKey),
                jsonAppKey.boundNetKey
            )
            networkImport.createAppKey(appKeys)
        }.forEach { appKeyImports.add(it) }
    }

    private fun handleGroups(networkImport: NetworkImport) {
        jsonMesh.groups.forEach { jsonGrps ->
            val groupImport = createGroupImport(jsonGrps, networkImport)
        }
    }

    private fun handleSubnets(networkImport: NetworkImport) {
        jsonMesh.netKeys.forEach { jsonNetKey ->
            val subnetImport = createSubnetImport(jsonNetKey, networkImport)

            subnetImport.createSubnetSecurity(
                jsonNetKey.phase,
                Converter.timestampToLong(jsonNetKey.timestamp) ?: 0
            )
        }
    }

    private fun createGroupImport(
        jsonGroup: JsonGroup,
        networkImport: NetworkImport,
    ): GroupImport {
        val name = jsonGroup.name
        val addr = jsonGroup.address?.toInt(16)
        val newAddr = IntegerAddress(addr!!)
        val groupImport = GroupImport(
            newAddr.rawAddress
        )
        return networkImport.createGroups(groupImport)
    }

    private fun createSubnetImport(
        jsonNetKey: JsonNetKey,
        networkImport: NetworkImport,
    ): SubnetImport {
        val netKeyImport = NetKeyImport(
            jsonNetKey.index,
            Converter.hexToBytes(jsonNetKey.key)!!,
            Converter.hexToBytes(jsonNetKey.oldKey)
        )

        return networkImport.createSubnet(netKeyImport)
            .apply { name = jsonNetKey.name!! }
    }

    private fun handleNodes(networkImport: NetworkImport) {
        jsonMesh!!.nodes.forEach { jsonNode ->
            val nodeImport = createNodeImport(networkImport, jsonNode)

            fillNodeSubnets(networkImport, nodeImport, jsonNode)
            fillAppKey(networkImport, nodeImport, jsonNode)
            handleElements(nodeImport, nodeImport.groups, jsonNode)
            handleNodeSettings(nodeImport.settings, jsonNode)
            handleDeviceCompositionData(nodeImport, jsonNode)
            handleNodeSecurity(nodeImport, nodeImport.security, jsonNode)
        }
    }

    private fun handleNodeSecurity(
        nodeImport: NodeImport,
        nodeSecurity: NodeSecurityImport,
        jsonNode: JsonNode,
    ) {
        jsonNode.appKeys.forEach {
            nodeSecurity.createNodeAppKey(it.index, it.updated)
            nodeImport.createNodeAppKey(it.index, it.updated)

        }

        jsonNode.netKeys.forEach {
            nodeSecurity.createNodeNetKey(it.index, it.updated)
            nodeImport.createNodeNetKey(it.index, it.updated)
        }

        nodeSecurity.blacklisted = jsonNode.blacklisted
        if (jsonNode.security.isNullOrEmpty()) {
            nodeSecurity.security = Security.LOW
        } else
            nodeSecurity.security = Security.valueOf(jsonNode.security!!.toUpperCase(Locale.ROOT))
        if (jsonNode.secureNetworkBeacon != null) {
            nodeSecurity.secureNetworkBeacon = jsonNode.secureNetworkBeacon!!
        }
    }

    private fun createNodeImport(networkImport: NetworkImport, jsonNode: JsonNode): NodeImport {
        val newUUIDStr = jsonNode.UUID!!.toString().replace("-", "")
        return networkImport.createNodes(
            Converter.hexToBytes(newUUIDStr),
            Converter.hexToInt(jsonNode.unicastAddress)!!,
            DevKeyImport(Converter.hexToBytes(jsonNode.deviceKey)!!)
        ).apply { name = jsonNode.name!! }
    }

        private fun fillAppKey(
            networkImport: NetworkImport,
            nodeImport: NodeImport,
            jsonNode: JsonNode,
        ) {
            jsonNode.appKeys.forEach {
                val nodeAppKey = NodeAppKeyImport(it.index, it.updated!!)
                val appKEYS = findAppKeyImport(it.index)
                nodeImport.addAppKey(appKEYS!!)
            }
        }

    private fun findAppKeyImport(appKeyIndex: Int): AppKeyImport? {
        return appKeyImports.find { it.index == appKeyIndex }
    }
    private fun fillNodeSubnets(
        networkImport: NetworkImport,
        nodeImport: NodeImport,
        jsonNode: JsonNode,
    ) {
        networkImport.subnets
            .filter { subnet ->
                jsonNode.netKeys.any { subnet.netKey!!.index == it.index }
            }.forEach { nodeImport.addSubnet(it) }
    }

    private fun isFeatureSupported(featureState: Int?): Boolean? {
        return when (featureState) {
            0, 1 -> true
            2 -> false
            else -> null
        }
    }

    private fun handleDeviceCompositionData(nodeImport: NodeImport, jsonNode: JsonNode) {
        jsonNode.features?.let { jsonFeatures ->

            val deviceCompositionData = nodeImport.createDeviceCompositionData()
            if (jsonFeatures.relay != null) {
                deviceCompositionData.supportsRelay = isFeatureSupported(jsonFeatures.relay)
            }
            if (jsonFeatures.proxy != null) {
                deviceCompositionData.supportsProxy = isFeatureSupported(jsonFeatures.proxy)
            }
            if (jsonFeatures.friend != null) {
                deviceCompositionData.supportsFriend = isFeatureSupported(jsonFeatures.friend)
            }
            if (jsonFeatures.lowPower != null) {
                deviceCompositionData.supportsLowPower = isFeatureSupported(jsonFeatures.lowPower)
            }

            if (jsonNode.cid != "NULL" &&  jsonNode.cid != null) {
                deviceCompositionData.cid = Converter.hexToInt(jsonNode.cid)
            }
            if (jsonNode.pid != "NULL" &&  jsonNode.pid != null) {
                deviceCompositionData.pid = Converter.hexToInt(jsonNode.pid)
            }
            if (jsonNode.vid != "NULL" &&  jsonNode.vid != null) {
                deviceCompositionData.vid = Converter.hexToInt(jsonNode.vid)
            }
            if (jsonNode.crpl != "NULL" &&  jsonNode.crpl != null) {
                deviceCompositionData.crpl = Converter.hexToInt(jsonNode.crpl)
            }

        }
    }

    private fun handleNodeSettings(nodeSettings: NodeSettingsImport, jsonNode: JsonNode) {
        nodeSettings.configComplete = jsonNode.configComplete
        nodeSettings.defaultTTL = jsonNode.defaultTTL
        val nodeSettings = nodeSettings.createFeatures()

        jsonNode.features?.let { handleFeatures(nodeSettings, it) }
    }

    private fun handleFeatures(featuresImport: FeaturesImport, jsonFeature: JsonFeature) {
        featuresImport.relayEnabled = isFeatureEnabled(jsonFeature.relay)
        featuresImport.proxyEnabled = isFeatureEnabled(jsonFeature.proxy)
        featuresImport.friendEnabled = isFeatureEnabled(jsonFeature.friend)
        featuresImport.lowPower = isFeatureEnabled(jsonFeature.lowPower)
    }

    private fun handleElements(
        nodeImport: NodeImport,
        nodeImportGroups: Set<GroupImport>,
        jsonNode: JsonNode,
    ) {
        jsonNode.elements.forEach {
            val elementImport = nodeImport
                .createElement(it.index, Converter.hexToInt(it.location)!!)
                .apply {

                    if (it.name == null){
                        name = ""
                    } else{
                        name = it.name!!
                    }
                     }

            handleModels(elementImport, nodeImportGroups, it)
        }
    }

    private fun handleModels(
        elementImport: ElementImport,
        nodeImportGroups: Set<GroupImport>,
        jsonElement: JsonElement,
    ) {
        jsonElement.models.forEach {
            val modelImport = elementImport.createModel(Converter.hexToUnsignedInt(it.modelId!!).toInt())
            handleModelSettings(modelImport.settings, it)
        }
    }

    private fun handleModelSettings(
        modelSettingsImport: ModelSettingsImport,
        jsonModel: JsonModel,
    ) {
        modelSettingsImport.apply {
            handlePublish(this, jsonModel.publish)

            jsonModel.subscribe.forEach { address ->
                if (Converter.isVirtualAddress(address)) {
                    createSubscription(Converter.hexToBytes(address))
                } else {
                    createSubscription(Converter.hexToInt(address)!!)
                }
            }
        }
    }

    private fun isFeatureEnabled(featureState: Int?): Boolean? {
        return when (featureState) {
            0 -> false
            1 -> true
            else -> null
        }
    }

    private fun handleProvisioners(networkImport: NetworkImport) {
        jsonMesh.provisioners.forEach { jsonProvisioner ->
            val uuidStr = jsonProvisioner.UUID!!.toLowerCase()
            networkImport.createProvisioner(Converter.stringToUuid(uuidStr))
                .apply {
                    name = jsonProvisioner.provisionerName.toString()
                }
                .also {
                    handleUnicastRanges(it, jsonProvisioner.allocatedUnicastRange)
                    handleGroupRanges(it, jsonProvisioner.allocatedGroupRange)
                    handleSceneRanges(it, jsonProvisioner.allocatedSceneRange)
                }
        }
    }

    private fun handleUnicastRanges(
        provisionerImport: ProvisionerImport,
        jsonUnicastRanges: Array<JsonAddressRange>,
    ) {
        jsonUnicastRanges.forEach {
            val low = Converter.hexToInt(it.lowAddress)!!
            val high = Converter.hexToInt(it.highAddress)!!

            provisionerImport.createUnicastRange(low, high)
        }
    }

    private fun handleGroupRanges(
        provisionerImport: ProvisionerImport,
        jsonGroupRanges: Array<JsonAddressRange>,
    ) {
        jsonGroupRanges.forEach {
            val low = Converter.hexToInt(it.lowAddress)!!
            val high = Converter.hexToInt(it.highAddress)!!

            provisionerImport.createGroupRange(low, high)
        }
    }

    private fun handleSceneRanges(
        provisionerImport: ProvisionerImport,
        jsonSceneRanges: Array<JsonSceneRange>,
    ) {
        jsonSceneRanges.forEach {
            val low = Converter.hexToInt(it.firstScene)!!
            val high = Converter.hexToInt(it.lastScene)!!

            provisionerImport.createSceneRange(low, high)
        }
    }

    private fun handleScenes(networkImport: NetworkImport) {
        jsonMesh.scenes.forEach { jsonScene ->
            val sceneImport = networkImport
                .createScene(Converter.hexToInt(jsonScene.number)!!)
                .apply { name = jsonScene.name.toString() }
            jsonScene.addresses.mapNotNull { address ->
                networkImport.nodes.find {
                    it.primaryElementAddress == Converter.hexToInt(address)
                }
            }.forEach { sceneImport.addNode(it) }
        }
    }

    private fun handlePublish(modelSettingsImport: ModelSettingsImport, jsonPublish: JsonPublish?) {
        jsonPublish?.let {
            modelSettingsImport.createPublish().apply {
                ttl = it.ttl
                period = it.period
                if (it.credentials != null) {
                    credentials = Credentials.get(it.credentials.toShort())
                }
                appKeyIndex = it.index

                if (Converter.isVirtualAddress(it.address!!)) {
                    createAddress(Converter.hexToBytes(it.address))
                } else {
                    createAddress(Converter.hexToInt(it.address)!!)
                }

                it.retransmit?.let { createRetransmit(it.count, it.interval) }
            }
        }
    }
}