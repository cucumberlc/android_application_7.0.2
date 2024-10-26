/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport

import android.annotation.SuppressLint
import android.util.Log
import com.siliconlab.bluetoothmesh.adk.data_model.address.IntegerAddress
import com.siliconlab.bluetoothmesh.adk.data_model.model.Addresses
import com.siliconlab.bluetoothmesh.adk.internal.util.toByteArray
import com.siliconlabs.bluetoothmesh.App.Utils.toUUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object Converter {

    private const val DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    internal fun hexToBytes(hexString: String?): ByteArray? {
        if (hexString == null) return null

        hexString.replace("-", "", false)
        if (hexString.length % 2 != 0) {
            Log.e("Converter", "hexToBytes: invalid hexString")
            return null
        }

        return ByteArray(hexString.length / 2) {
            hexString.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    internal fun uuidGenerator(): String {
        val uuid = UUID.randomUUID()
        val uuidStr = uuid.toString().replace("-", "")
        return uuidStr
    }

    internal fun bytesToHex(bytes: ByteArray?): String? {
        if (bytes == null) return null
        val sb = StringBuilder()
        bytes.forEach { sb.append(String.format("%02x", it)) }
        return sb.toString()
    }

    internal fun replaceHypes(uuid: String): String {
        return uuid.replace("-", "")
    }

    internal fun replaceHypesUUID(uuid: String): UUID {
        uuid.replace("-", "")
        return stringToUuid(uuid)
    }

    internal fun hexToInt(hexString: String?): Int? {
        return hexString?.let { Integer.parseInt(it, 16) }
    }

    internal fun IntegerAddressToHex(value: IntegerAddress, width: Int = 1): String {
        return value.let { String.format("%1$0${width}X", it) }
    }

    internal fun intToHex(value: Int?, width: Int = 1): String {
        return value.let { String.format("%1$0${width}X", it) }
    }

    internal fun hexToUnsignedInt(hexString: String): UInt {
        return hexString.toUInt(16)
    }

    internal fun isVirtualAddress(hexString: String): Boolean {
        return hexString.length == 32
    }

    internal fun stringToUuid(uuid: String): UUID {

        if (uuid.isEmpty()) {
            return replaceHypesUUID(UUID.randomUUID().toString())
        } else
            replaceHypes(uuid)
        return UUID.fromString(
            uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" +
                    uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" +
                    uuid.substring(20, 32)
        )
    }

    // private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")

    internal fun timestampToLong(timestamp: String?): Long? {
        val formatter = SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.getDefault())
        return try {
            val date = formatter.parse(timestamp!!)
            date?.time
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @SuppressLint("SimpleDateFormat")
    internal fun longToTimestamp(timestamp: Long?): String? {
        // return timestamp?.let { DATE_FORMAT_PATTERN.format(Date(it)) }
        val dateFormat = SimpleDateFormat(DATE_FORMAT_PATTERN)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(Date(timestamp!!))
    }

    internal fun uuidToString(uuid: UUID): String {
        return uuid.toString().replace("-", "")
    }

    internal fun addressToHex(address: Addresses): String? {
        return intToHex(address.value, 4) ?: bytesToHex(address.virtualLabel.toByteArray())
    }
}