/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject

class JsonModel {
    var modelId: String? = null
    var subscribe: Array<String> = emptyArray()
    var publish: JsonPublish? = null
    var bind: Array<Int> = emptyArray()
 //   var knownAddresses: Array<String>? = null
}