/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.ExportImport

import com.google.gson.GsonBuilder
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.DeviceCompositionData
import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.model.Retransmit
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.provisioner.AddressRange
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.Converter
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonAddressRange
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonAppKey
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonElement
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonFeature
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonGroup
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonMesh
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonModel
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonNetKey
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonNode
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonNodeKey
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonProvisioner
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonPublish
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonRetransmit
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonScene
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonSceneRange

class JsonExporter {
    private val network = BluetoothMesh.network

    fun exportJson(): String {
        val jsonMesh = generateMeshJson()
        val gson = GsonBuilder().create()
        return gson.toJson(jsonMesh)
    }

    private fun generateMeshJson(): JsonMesh {
        return JsonMesh().apply {
            schema = "http://json-schema.org/draft-04/schema#"
            id =
                "https://www.bluetooth.com/specifications/assigned-numbers/mesh-profile/cdb-schema.json#"
            version = network.version
            meshUUID = Converter.uuidGenerator()
            meshName = "Mesh Test"
            timestamp = Converter.longToTimestamp(System.currentTimeMillis())
            provisioners = createJsonProvisioners()
            netKeys = createJsonNetKeys()
            appKeys = createJsonAppKeys()
            groups = createJsonGroups()
            nodes = createJsonNodes()
            scenes = createJsonScenes()
        }
    }

    private fun createJsonNetKeys(): Array<JsonNetKey> {
        return network.subnets.map {
            println("IMPEXP --- subnets:$it.appKeys")
            JsonNetKey().apply {
                name = it.name
                index = it.netKey.index
                timestamp = Converter.longToTimestamp(it.subnetSecurity.keyRefreshTimestamp)
                phase = it.subnetSecurity.keyRefreshPhase ?: 0
                key = Converter.bytesToHex(it.netKey.key)
                // minSecurity = it.subnetSecurity.subnet.name.toLowerCase(Locale.ROOT)
                oldKey = Converter.bytesToHex(it.netKey.oldKey)
            }
        }.toTypedArray()
    }

    private fun createJsonAppKeys(): Array<JsonAppKey> {
        return network.subnets.flatMap { it.appKeys }.map {
            println("IMPEXP --- appKeys:$it.appKeys")
            JsonAppKey().apply {
                // name = it.name ?: ""
                index = it.index
                key = Converter.bytesToHex(it.key)
                oldKey = Converter.bytesToHex(it.oldKey)
                boundNetKey = it.subnet.netKey.index ?: 0
            }
        }.toTypedArray()
    }

    private fun createJsonNodes(): Array<JsonNode> {
        return network.subnets.flatMap { it.nodes }.map {
            println("IMPEXP --- nodes:$it.nodes")
            JsonNode().apply {
                name = it.name //2
                UUID = Converter.replaceHypes(it.uuid.toString())
                val uniCast = it.primaryElementAddress
                unicastAddress = uniCast.toString() //3
                if (it.devKey != null && it.devKey.key != null) {
                    deviceKey = Converter.bytesToHex(it.devKey.key)
                }
                configComplete = it.nodeSettings.configComplete //5
                cid = Converter.intToHex(it.deviceCompositionData!!.cid, 4)
                pid = Converter.intToHex(it.deviceCompositionData!!.pid, 4)
                vid = Converter.intToHex(it.deviceCompositionData!!.vid, 4)
                crpl = Converter.intToHex(it.deviceCompositionData!!.crpl, 4)
                defaultTTL = it.nodeSettings.defaultTTL
                blacklisted = it.nodeSecurity.isNSBlackListed
                security = it.nodeSecurity.nsSecurity.name
                secureNetworkBeacon = it.nodeSecurity.isNSSecureNetworkBeacon //4
                netKeys = createNodeNetKey(it)
                appKeys = createNodeAppKey(it.boundAppKeys)
                elements = createJsonElement(it)
                features = createJsonFeatures(it) //1
                knownAddresses = fillGroupsInJsonNode(it)
            }
        }.toTypedArray()
    }

    private fun createNodeNetKey(node: Node): Array<JsonNodeKey> {
        return node.subnets.map {
            JsonNodeKey().apply {
                index = it.netKey.index
            }
        }.toTypedArray()
    }

    private fun createNodeAppKey(boundAppKeys: Set<AppKey>): Array<JsonNodeKey> {
        return boundAppKeys.map {
            JsonNodeKey().apply {
                index = it.index
            }
        }.toTypedArray()
    }

    private fun fillGroupsInJsonNode(node: Node): Array<String> {
        return node.subnets.flatMap { it.groups }.map {
            Converter.intToHex(it.address.value)
        }.toTypedArray()
    }

    private fun createJsonFeatures(node: Node): JsonFeature? {
        val features = node.nodeSettings.features ?: return null
        val dcd = node.deviceCompositionData
        return JsonFeature().apply {
            relay = convertFeatureState(dcd?.supportsProxy, features.isRelayEnabled())
            friend = convertFeatureState(dcd?.supportsFriend, features.isFriendEnabled())
            lowPower = convertLowPowerFeatureState(dcd, features.isLowPower())
            proxy = convertFeatureState(dcd?.supportsProxy, features.isProxyEnabled())
        }
    }

    private fun createJsonScenes(): Array<JsonScene> {
        return network.scenes.map {
            JsonScene().apply {
                name = it.name
            }
        }.toTypedArray()
    }

    private fun createJsonElement(node: Node): Array<JsonElement> {
        return node.elements.map {
            JsonElement().apply {
                name = ""
                index = it!!.index
                location = Converter.intToHex(it.address.rawAddress, 4)
                models = createJsonModels(it)
            }
        }.toTypedArray()
    }

    private fun createJsonModels(element: Element): Array<JsonModel> {
        return element.sigModels.union(element.vendorModels).map {
            val idWidth = if (it.isSIGModel) 4 else 8
            JsonModel().apply {
                if (idWidth == 8) {
                    // val modId = Converter.intToHex(it.identifier.toInt())
                    val id = swap16Bits(it.identifier.toInt())
                    modelId = Converter.intToHex(id, idWidth)
                } else
                    modelId = Converter.intToHex(it.identifier.toInt(), idWidth)
                subscribe = createSubscribe(it)
                publish = createJsonPublish(it)
                bind = createBind(it)
                //knownAddresses = fillGroupsInJsonModel(it)
            }
        }.toTypedArray()
    }

   private fun swap16Bits(value: Int): Int {
        return ((value and 0x0000FFFF) shl 16) or ((value and 0xFFFF0000.toInt()) ushr 16)
    }

    private fun createBind(model: Model): Array<Int> {
        return model.boundAppKeys.map {
            it.index
        }.toTypedArray()
    }

    private fun createSubscribe(model: Model): Array<String> {
        return model.modelSettings.subscriptions
            .map { Converter.addressToHex(it)!! }
            .toTypedArray()
    }

    private fun createJsonPublish(model: Model): JsonPublish? {
        return model.modelSettings.publish?.let {
            JsonPublish().apply {
                address = Converter.addressToHex(it.address)
                index = it.appKeyIndex
                ttl = it.ttl
                period = it.period
                if (it.credentials == null) {
                    credentials = 0
                } else {
                    credentials = it.credentials.value.toInt()
                }
                retransmit = createJsonRetransmit(it.retransmit)
            }
        }
    }

    private fun createJsonRetransmit(retransmit: Retransmit): JsonRetransmit {
        return JsonRetransmit().apply {
            count = retransmit.getCount()!!
            interval = retransmit.getInterval()!!
        }
    }

    private fun convertFeatureState(supports: Boolean?, enabled: Boolean?): Int? {
        return when {
            supports == false -> 2
            enabled == null -> null
            enabled -> 1
            else -> 0
        }
    }

    private fun convertLowPowerFeatureState(dcd: DeviceCompositionData?, feature: Boolean?): Int? {
        return when {
            dcd != null && !dcd.supportsLowPower!! || feature != null && !feature -> 2
            dcd != null && dcd.supportsLowPower == true || feature != null && feature -> 1
            else -> null
        }
    }

    private fun createJsonProvisioners(): Array<JsonProvisioner> {
        return network.provisioners.map {
            JsonProvisioner().apply {
                provisionerName = it.name
                UUID = Converter.uuidToString(it.uuid)
                allocatedUnicastRange = createJsonAddressRange(it.allocatedUnicastRange)
                allocatedGroupRange = createJsonAddressRange(it.allocatedGroupRange)
                allocatedSceneRange = createJsonSceneRange(it.allocatedSceneRange)
            }
        }.toTypedArray()
    }

    private fun createJsonGroups(): Array<JsonGroup> {
        return network.groups.map {
            JsonGroup().apply {
                name = it.name
                address = it.address.toString()
            }
        }.toTypedArray()
    }

    private fun createJsonAddressRange(ranges: List<AddressRange>): Array<JsonAddressRange> {
        return ranges.map {
            JsonAddressRange().apply {
                lowAddress = Converter.intToHex(it.lowAddress, 4)
                highAddress = Converter.intToHex(it.highAddress, 4)
            }
        }.toTypedArray()
    }

    private fun createJsonSceneRange(ranges: List<AddressRange>): Array<JsonSceneRange> {
        return ranges.map {
            JsonSceneRange().apply {
                firstScene = Converter.intToHex(it.lowAddress, 4)
                lastScene = Converter.intToHex(it.highAddress, 4)
            }
        }.toTypedArray()
    }
}