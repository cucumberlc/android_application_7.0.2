/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.TimeControl

import android.content.Context
import com.siliconlabs.bluetoothmesh.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class TimeParams(val taiSeconds: Long, val subsecond: Int, val uncertainty: Int, val timeAuthority: Int, val taiUtcDelta: Int, val timeZoneOffset: Short) {

    fun validate(context: Context): String {
        if (!isSubsecondValid(subsecond)) return context.getString(R.string.device_adapter_time_subsecond_invalid_range)
        if (!isUncertaintyValid(uncertainty)) return context.getString(R.string.device_adapter_time_uncertainty_invalid_range)
        if (!isTimeAuthorityValid(timeAuthority)) return context.getString(R.string.device_adapter_time_time_authority_invalid_range)
        if (!isTaiUtcDeltaValid(taiUtcDelta)) return context.getString(R.string.device_adapter_time_tai_utc_delta_invalid_range)
        if (!isTimeZoneOffsetValid(timeZoneOffset)) return context.getString(R.string.device_adapter_time_time_zone_offset_invalid_range)
        return ""
    }

    private fun isSubsecondValid(subsecond: Int): Boolean {
        return subsecond in 0..255
    }

    private fun isUncertaintyValid(uncertainty: Int): Boolean {
        return uncertainty in 0..255
    }

    private fun isTimeAuthorityValid(timeAuthority: Int): Boolean {
        return timeAuthority == 0 || timeAuthority == 1
    }

    private fun isTaiUtcDeltaValid(taiUtcDelta: Int): Boolean {
        return taiUtcDelta in -255..32512
    }

    private fun isTimeZoneOffsetValid(timeZoneOffset: Short): Boolean {
        return timeZoneOffset in -64..191
    }

    companion object {

        //TODO TAI_UTC delta is changed every few years. Please check sometimes if we should change it
        const val TAI_UTC_DELTA_VALUE = 37

        private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
                .apply { timeZone = TimeZone.getTimeZone("UTC") }

        private fun getTaiBaseDate(): Date {
            return dateFormat.parse("2000/01/01 00:00:00")!!
        }

        fun getRawLocalTimeTaiSeconds(): Long {
            return ((System.currentTimeMillis() - getTaiBaseDate().time) / 1000) + TAI_UTC_DELTA_VALUE
        }

        fun getRawLocalTimeSubsecond(): Int {
            return ((System.currentTimeMillis() % 1000) / 1000.0 * 255).roundToInt()
        }

        fun getHumanReadableTaiSeconds(taiSeconds: Long): String {
            val timeInMillis: Long = getTaiBaseDate().time + (taiSeconds * 1000)

            return dateFormat.format(Date(timeInMillis)).toString() + " TAI"
        }

        fun getHumanReadableSubsecond(subsecond: UByte): String {
            return String.format(Locale.US, "%.2f", subsecond.toDouble() / UByte.MAX_VALUE.toDouble()) + " s"
        }

        fun getHumanReadableUncertainty(uncertainty: UByte): String {
            return String.format(Locale.US, "%.2f", uncertainty.toDouble() / 100) + " s"
        }

        fun getHumanReadableTimeAuthority(timeAuthority: Boolean): String = if (timeAuthority) "Yes" else "No"

        fun getHumanReadableTaiUtcDelta(taiUtcDelta: Int): String = "$taiUtcDelta s"

        fun getHumanReadableTimeZoneOffset(timeZoneOffset: Short): String {
            val times = abs(timeZoneOffset * 0.25)
            val hours = times.toInt()
            val minutes = ((times - hours) * 60).toInt()

            return StringBuilder().apply {
                append("UTC")
                append(getTimeZoneOffsetSign(timeZoneOffset))
                append(hours)
                if (minutes > 0) append(":").append(minutes)
            }.toString()
        }

        private fun getTimeZoneOffsetSign(timeZoneOffset: Short): String {
            return when {
                timeZoneOffset < 0 -> "-"
                timeZoneOffset > 0 -> "+"
                else -> "\u00B1"
            }
        }

        fun getLocalTimeZoneRawOffset(): String {
            return (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000 / 60 / 15).toString()
        }
    }

    enum class ParameterType {
        TAI_SECONDS,
        SUBSECOND,
        UNCERTAINTY,
        TIME_AUTHORITY,
        TAI_UTC_DELTA,
        TIME_ZONE_OFFSET
    }
}