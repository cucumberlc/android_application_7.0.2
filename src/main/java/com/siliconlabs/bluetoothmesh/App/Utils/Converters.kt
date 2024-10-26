/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */
package com.siliconlabs.bluetoothmesh.App.Utils

/**
 * Converters - converts value between different numeral system
 */
object Converters {

    fun invAtou32(value: Int): ByteArray {
        return byteArrayOf(
            (value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte()
        )
    }

    fun Short.encodeHex(): String {
        val byteValue = byteArrayOf(
            (toInt() shr 8).toByte(),
            (toInt() and 0xff).toByte()
        )
        return byteValue.encodeHex()
    }

    fun convertUint8ToInt(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toInt() and 0x000000FF
    }

    fun convertUint16ToInt(bytes: ByteArray, offset: Int): Int {
        val component1 = bytes[offset].toInt() and 0x000000FF
        val component2 = bytes[1 + offset].toInt() shl 8 and 0x0000FF00
        return component1 or component2
    }

    fun convertUint24ToInt(bytes: ByteArray, offset: Int): Int {
        val component1 = bytes[offset].toInt() and 0x000000FF
        val component2 = bytes[1 + offset].toInt() shl 8 and 0x0000FF00
        val component3 = bytes[2 + offset].toInt() shl 16 and 0x00FF0000
        return component1 or component2 or component3
    }

    fun convertFloatToByteArray(value: Float): ByteArray {
        val bytes = ByteArray(4)
        val intBits = value.toBits()
        bytes[0] = intBits.toByte()
        bytes[1] = (intBits shr 8).toByte()
        bytes[2] = (intBits shr 16).toByte()
        bytes[3] = (intBits shr 24).toByte()
        return bytes
    }

    fun convertByteArrayToFloat(bytes: ByteArray): Float {
        val component4 = bytes[0].toInt() and 0xFF
        val component3 = bytes[1].toInt() and 0xFF shl 8
        val component2 = bytes[2].toInt() and 0xFF shl 16
        val component1 = bytes[3].toInt() shl 24
        val intBits = component1 or component2 or component3 or component4
        return Float.fromBits(intBits)
    }
}