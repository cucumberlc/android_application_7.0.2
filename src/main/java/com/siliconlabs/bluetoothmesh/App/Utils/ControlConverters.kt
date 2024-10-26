/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class ControlConverters {
    companion object {
        private var INT_16_MIN = -32768

        private var UINT_16_MAX = 65535

        private var DELTA_UV_MIN = -1.00f
        private var DELTA_UV_MAX = 1.00f

        fun getLevel(percentage: Int): Short {
            val percentageDouble = percentage.toDouble() / 100
            var levelValue = percentageDouble * UINT_16_MAX
            levelValue += INT_16_MIN
            return ceil(levelValue).toInt().toShort()
        }

        fun getLevelPercentage(level: Short): Int {
            val levelMoved = level + abs(INT_16_MIN)
            val percentageDouble = levelMoved.toDouble() / UINT_16_MAX
            return (percentageDouble * 100).roundToInt()
        }

        fun getLightness(percentage: Int): Int {
            val percentageDouble = percentage.toDouble() / 100
            return ceil(percentageDouble * UINT_16_MAX).toInt()
        }

        fun getLightnessPercentage(lightness: UShort): Int {
            val percentageDouble = lightness.toDouble() / UINT_16_MAX
            return (percentageDouble * 100).roundToInt()
        }

        fun getDeltaUv(percentage: Int): Int {
            val percentageDouble = percentage.toDouble() / 100
            var deltaUv = percentageDouble * UINT_16_MAX
            deltaUv += INT_16_MIN
            return ceil(deltaUv).toInt()
        }

        fun getDeltaUvPercentage(deltaUv: Short): Int {
            val deltaUvMoved = deltaUv + abs(INT_16_MIN)
            val percentageDouble = deltaUvMoved.toDouble() / UINT_16_MAX
            return (percentageDouble * 100).roundToInt()
        }

        fun getDeltaUvToShow(percentage: Int): Float {
            val value = (percentage * 2 * DELTA_UV_MAX / 100) + DELTA_UV_MIN
            return when {
                value < DELTA_UV_MIN -> DELTA_UV_MIN
                value > DELTA_UV_MAX -> DELTA_UV_MAX
                else -> value
            }
        }
    }
}