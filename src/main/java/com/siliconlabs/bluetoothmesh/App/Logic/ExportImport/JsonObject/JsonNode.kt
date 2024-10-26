/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject

class JsonNode {
    var UUID: String? = null
    var unicastAddress: String? = null
    var deviceKey: String? = null
    var security: String? = null
    var name: String? = null
    var configComplete: Boolean = false
    var elements: Array<JsonElement> = emptyArray()
    var cid: String? = null
    var pid: String? = null
    var vid: String? = null
    var crpl: String? = null
    var secureNetworkBeacon: Boolean? = null
    var defaultTTL: Int? = null
    var features: JsonFeature? = null
    var blacklisted: Boolean = false
    var netKeys: Array<JsonNodeKey> = emptyArray()
    var appKeys: Array<JsonNodeKey> = emptyArray()
    var knownAddresses: Array<String>? = null
    var networkTransmit: JsonNetworkTransmit? = null
    var relayRetransmit: JsonRelayRetransmit? = null
}