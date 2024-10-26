/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject

class JsonProvisioner {
    var provisionerName: String? = null
    var UUID: String? = null
    var allocatedUnicastRange = arrayOf<JsonAddressRange>()
    var allocatedGroupRange = arrayOf<JsonAddressRange>()
    var allocatedSceneRange = arrayOf<JsonSceneRange>()
}