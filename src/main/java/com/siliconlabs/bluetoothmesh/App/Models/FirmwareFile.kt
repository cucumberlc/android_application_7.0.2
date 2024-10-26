/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.google.gson.annotations.SerializedName


data class ManifestContainer(
        @SerializedName("manifest")
        val manifestContent: ManifestContent
)

data class ManifestContent(
        @SerializedName("firmware")
        val firmwareFile: FirmwareFile
)

data class FirmwareFile(
        @SerializedName("firmware_file")
        val firmwareName: String = "",
        @SerializedName("firmware_id")
        val firmwareId: String = "",
        @SerializedName("metadata_file")
        val metadata: String? = null
)