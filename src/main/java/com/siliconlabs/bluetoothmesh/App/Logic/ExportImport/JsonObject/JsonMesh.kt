/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject

import com.google.gson.annotations.SerializedName

class JsonMesh {
    @SerializedName("\$schema")
    var schema: String? = null
    var id: String? = null
    var version: String? = null
    var meshUUID: String? = null
    var meshName: String? = null
    var timestamp: String? = null
    var provisioners: Array<JsonProvisioner> = emptyArray()
    var netKeys: Array<JsonNetKey> = emptyArray()
    var appKeys: Array<JsonAppKey> = emptyArray()
    var nodes: Array<JsonNode> = emptyArray()
    var groups: Array<JsonGroup> = emptyArray()
    var scenes: Array<JsonScene> = emptyArray()
}