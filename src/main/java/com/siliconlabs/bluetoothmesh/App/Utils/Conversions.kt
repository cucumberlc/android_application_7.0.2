/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import com.siliconlab.bluetoothmesh.adk.provisioning.OobInformation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Must have an even length" }
    return chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

fun ByteArray.encodeHex(separator: String = ""): String =
    joinToString(separator) { "%02X".format(it) }

fun Int.toByteArray(bytes: Int): ByteArray =
    ByteBuffer
        .allocate(4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(this)
        .array()
        .copyOf(
            newSize = bytes.coerceAtMost(4)
        )

fun ByteArray.toUUID(): UUID {
    val byteBuffer = ByteBuffer.wrap(this)
    val high = byteBuffer.long
    val low = byteBuffer.long

    return UUID(high, low)
}

fun UUID.toByteArray(): ByteArray = ByteBuffer
    .wrap(ByteArray(16))
    .apply {
        putLong(mostSignificantBits)
        putLong(leastSignificantBits)
    }.array()

fun fromBitset(bitset: BitSet): Set<OobInformation> =
    enumValues<OobInformation>().filter {
        bitset.get(it.bitPosition)
    }.toSet()

fun UInt.encodeHex() = "0x%08X".format(this.toInt())
fun Short.encodeHex() = "0x%04X".format(this)
