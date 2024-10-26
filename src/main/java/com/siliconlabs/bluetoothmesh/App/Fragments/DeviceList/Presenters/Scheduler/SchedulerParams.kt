package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.Scheduler

import com.siliconlab.bluetoothmesh.adk.functionality_control.scheduler.Action
import com.siliconlabs.bluetoothmesh.App.MeshApplication.Companion.appContext
import com.siliconlabs.bluetoothmesh.R

class SchedulerParams(
    val index: Int,
    val year: UByte,
    val months: Int,
    val day: Int,
    val hour: UByte,
    val minute: UByte,
    val second: UByte,
    val daysOfWeek: Int,
    val action: Action,
    val transitionTime: Int,
    val scene: Int,
) {
    fun validate(): String? {
        if (!isYearValid(year)) return yearInvalidRangeMessage()
        if (!isDayValid(day)) return dayInvalidRangeMessage()
        if (!isHourValid(hour)) return hourInvalidRangeMessage()
        if (!isMinuteValid(minute)) return minuteInvalidRangeMessage()
        if (!isSecondValid(second)) return secondInvalidRangeMessage()
        return null
    }

    companion object {
        const val YEAR_MIN:UByte = 0u
        const val YEAR_MAX = 99
        const val DAY_MIN = 1
        const val DAY_MAX = 31
        const val HOUR_MIN: UByte = 0u
        const val HOUR_MAX: UByte = 23u
        const val MINUTE_MIN:UByte = 0u
        const val MINUTE_MAX:UByte = 59u
        const val SECOND_MIN:UByte = 0u
        const val SECOND_MAX:UByte = 59u

        fun isYearValid(year: UByte): Boolean {
            return year in YEAR_MIN..100u
        }

        fun isDayValid(day: Int): Boolean {
            return day in 0..DAY_MAX
        }

        fun isHourValid(hour: UByte): Boolean {
            return hour in HOUR_MIN..25u
        }

        fun isHourValid(hour: Int): Boolean {
            return hour in HOUR_MIN.toInt()..25
        }

        fun isMinuteValid(minute: UByte): Boolean {
            return minute in MINUTE_MIN..63u
        }

        fun isMinuteValid(minute: Int): Boolean {
            return minute in MINUTE_MIN.toInt()..63
        }

        fun isSecondValid(second: UByte): Boolean {
            return second in SECOND_MIN..63u
        }

        fun isSecondValid(second: Int): Boolean {
            return second in SECOND_MIN.toInt()..63
        }

        fun yearInvalidRangeMessage(): String {
            return appContext.getString(R.string.device_adapter_scheduler_invalid_range, appContext.getString(R.string.device_adapter_scheduler_year), YEAR_MIN, YEAR_MAX)
        }

        fun dayInvalidRangeMessage(): String {
            return appContext.getString(R.string.device_adapter_scheduler_invalid_range, appContext.getString(R.string.device_adapter_scheduler_day), DAY_MIN, DAY_MAX)
        }

        fun hourInvalidRangeMessage(): String {
            return appContext.getString(R.string.device_adapter_scheduler_invalid_range, appContext.getString(R.string.device_adapter_scheduler_hour), HOUR_MIN, HOUR_MAX)
        }

        fun minuteInvalidRangeMessage(): String {
            return appContext.getString(R.string.device_adapter_scheduler_invalid_range, appContext.getString(R.string.device_adapter_scheduler_minute), MINUTE_MIN, MINUTE_MAX)
        }

        fun secondInvalidRangeMessage(): String {
            return appContext.getString(R.string.device_adapter_scheduler_invalid_range, appContext.getString(R.string.device_adapter_scheduler_second), SECOND_MIN, SECOND_MAX)
        }
    }
}