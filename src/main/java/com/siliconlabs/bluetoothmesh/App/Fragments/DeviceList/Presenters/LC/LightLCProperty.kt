/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.LC

import androidx.annotation.VisibleForTesting
import com.siliconlabs.bluetoothmesh.App.Utils.Converters
import com.siliconlabs.bluetoothmesh.App.Utils.toByteArray
import kotlin.math.pow
import kotlin.math.roundToInt

enum class LightLCProperty(val id: Int, val characteristic: Characteristic) {
    AmbientLuxLevelOn(0x002B, Characteristic.Illuminance),
    AmbientLuxLevelProlong(0x002C, Characteristic.Illuminance),
    AmbientLuxLevelStandby(0x002D, Characteristic.Illuminance),
    LightnessOn(0x002E, Characteristic.PerceivedLightness),
    LightnessProlong(0x002F, Characteristic.PerceivedLightness),
    LightnessStandby(0x0030, Characteristic.PerceivedLightness),
    RegulatorAccuracy(0x0031, Characteristic.Percentage8),
    RegulatorKid(0x0032, Characteristic.Coefficient),
    RegulatorKiu(0x0033, Characteristic.Coefficient),
    RegulatorKpd(0x0034, Characteristic.Coefficient),
    RegulatorKpu(0x0035, Characteristic.Coefficient),
    TimeFade(0x0036, Characteristic.TimeMillisecond24),
    TimeFadeOn(0x0037, Characteristic.TimeMillisecond24),
    TimeFadeStandbyAuto(0x0038, Characteristic.TimeMillisecond24),
    TimeFadeStandbyManual(0x0039, Characteristic.TimeMillisecond24),
    TimeOccupancyDelay(0x003A, Characteristic.TimeMillisecond24),
    TimeProlong(0x003B, Characteristic.TimeMillisecond24),
    TimeRunOn(0x003C, Characteristic.TimeMillisecond24);

    enum class Characteristic(val range: IntRange? = null, val factor: Int = 1, val bytes: Int) {
        Illuminance(0..16777214, factor = 100, bytes = 3),
        PerceivedLightness(0..65535, bytes = 2),
        Percentage8(0..200, factor = 2, bytes = 1),
        Coefficient(0..1000, bytes = 4),
        TimeMillisecond24(0..16777214, factor = 1000, bytes = 3);

        val min get() = range?.first()?.toDouble()
        val max get() = range?.last()?.toDouble()
        val firstElement get() = range?.elementAtOrNull(1)?.toDouble()?.div(factor)
    }

    private fun checkRange(value: Double) {
        if (value < characteristic.min!! || value > characteristic.max!!) {
            throw LightLCPropertyValueRangeException()
        }
    }

    @Throws(LightLCPropertyValueRangeException::class)
    fun convertToByteArray(data: String): ByteArray {
        return when (characteristic) {
            Characteristic.Illuminance,
            Characteristic.Percentage8,
            Characteristic.TimeMillisecond24,
            Characteristic.PerceivedLightness -> {
                val value = convertToNormalized(data)
                checkRange(value)
                val valueInt = value.roundToInt()
                valueInt.toByteArray(characteristic.bytes)
            }
            Characteristic.Coefficient -> {
                checkRange(data.toDouble())
                val value = data.toFloat()
                Converters.convertFloatToByteArray(value)
            }
        }
    }

    @VisibleForTesting
    fun convertToNormalized(data: String): Double {
        val commaIndex = data.indexOf('.')
        val numerator = (if (commaIndex == -1) data else data.removeRange(commaIndex,
                commaIndex + 1)).toDouble()
        val denominator = if (commaIndex == -1) 1
        else 10f.pow(data.length - 1 - commaIndex).toInt()

        return numerator * characteristic.factor / denominator
    }

    fun convertToValue(data: ByteArray): String {
        return when (this.characteristic) {
            Characteristic.Illuminance -> {
                val factor: Float = Characteristic.Illuminance.factor.toFloat()
                val value = Converters.convertUint24ToInt(data, 0) / factor
                value.toString()
            }
            Characteristic.PerceivedLightness -> {
                Converters.convertUint16ToInt(data, 0).toString()
            }
            Characteristic.Percentage8 -> {
                val factor: Float = Characteristic.Percentage8.factor.toFloat()
                val value = Converters.convertUint8ToInt(data, 0)
                if (value == 255) "(Not known)" else (value / factor).toString()
            }
            Characteristic.Coefficient -> {
                Converters.convertByteArrayToFloat(data).toString()
            }
            Characteristic.TimeMillisecond24 -> {
                val factor: Float = Characteristic.TimeMillisecond24.factor.toFloat()
                val value = Converters.convertUint24ToInt(data, 0).toDouble() / factor
                value.toString()
            }
        }
    }

    class LightLCPropertyValueRangeException : Exception()
}
