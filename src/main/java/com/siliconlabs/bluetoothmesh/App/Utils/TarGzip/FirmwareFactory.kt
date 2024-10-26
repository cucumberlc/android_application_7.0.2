/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.TarGzip

import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.Blob
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_update.FirmwareId
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware

object FirmwareFactory {

    fun createFirmware(firmwareId: String, firmwareData: ByteArray, metadata: ByteArray?): Firmware {
        val firmwareIdFormatRegex = "^(?:[\\dA-F]{2}+){2,108}+$".toRegex()
        val matches = firmwareId.matches(firmwareIdFormatRegex)

        if (!matches) {
            throw IllegalArgumentException("FirmwareId has wrong format!")
        }

        val parsedFirmwareId = parseHexToByteArray(firmwareId)
        val id = FirmwareId(parsedFirmwareId)

        return Firmware(id, Blob(firmwareData), metadata)
    }

    private fun parseHexToByteArray(hexFirmwareId: String): ByteArray {
        val result = mutableListOf<Byte>()
        val symbolsPerByte = 2
        val hexEncodingBase = 16

        for (i in (hexFirmwareId.indices step symbolsPerByte)) {
            val hexStr = hexFirmwareId.substring(i, i + symbolsPerByte)
            val decodedNumber = hexStr.toInt(radix = hexEncodingBase).toByte()

            result.add(decodedNumber)
        }

        return result.toByteArray()
    }
}