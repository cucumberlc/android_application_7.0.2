/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scheduler

import android.content.Context
import android.os.Bundle
import android.text.Selection
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.errors.StackError
import com.siliconlab.bluetoothmesh.adk.functionality_control.scheduler.Action
import com.siliconlab.bluetoothmesh.adk.functionality_control.scheduler.SchedulerActionResponse
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.Scheduler.SchedulerParams
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.YearTextWatcher
import com.siliconlabs.bluetoothmesh.App.Views.*
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesSchedulerDetailFragmentBinding
import dagger.hilt.android.AndroidEntryPoint

//TODO BTMESH-2453 correct magic numbers
@AndroidEntryPoint
class SchedulerFragment @Deprecated(
    "use newInstance(MeshNode)",
    replaceWith = ReplaceWith("SchedulerFragment.newInstance(meshNode)")
) constructor() : Fragment(R.layout.devices_scheduler_detail_fragment), SchedulerView {
    companion object {
        fun newInstance(meshNode: MeshNode) =
            @Suppress("DEPRECATION")
            SchedulerFragment().withMeshNavArg(meshNode.node.toNavArg())
    }

    private val layout by viewBinding(DevicesSchedulerDetailFragmentBinding::bind)
    private val schedulerPresenter: SchedulerPresenter by presenters()

    private var selectedEntry = 0

    private val everyYearValue: UByte = 100u
    private val everyDayValue: UByte = 0u
    private val everyDayOfWeekValue: UByte = 0b01111111u
    private val everyMonthValue: UShort = 4095u

    private val customHourValue = 2
    private val customMinuteValue = 4
    private val customSecondValue = 4

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        schedulerPresenter.refreshScheduleRegister(
            DeviceViewHolderBase.RefreshNodeListener(layout.ivSchedulerStatusRefresh)
        )
    }

    private fun bindScheduler() {
        layout.apply {
            setSchedulerVisibilityRules()

            spSchedulerEntries.apply {
                adapter = Adapter(requireContext(), mapSchedulerEntries())
                setOnItemSelectedListener { index ->
                    selectedEntry = index
                    bindDefaultAction()
                    schedulerPresenter.meshNode.scheduleRegister[index.toUByte()]?.let { bindAction(it) }
                }
                setSelection(selectedEntry)
            }

            btnSchedulerSet.setOnClickListener { onSchedulerActionSetButtonClick() }
            ivSchedulerStatusRefresh.setOnClickListener {
                schedulerPresenter.refreshScheduleRegister(
                    DeviceViewHolderBase.RefreshNodeListener(it as RefreshNodeButton)
                )
            }
            ivSchedulerActionRefresh.setOnClickListener {
                val index = spSchedulerEntries.selectedItemPosition
                schedulerPresenter.refreshSchedulerAction(
                    index,
                    DeviceViewHolderBase.RefreshNodeListener(it as RefreshNodeButton)
                )
            }
        }
        setYearEditTextListeners()
    }

    private fun setSchedulerVisibilityRules() {
        layout.apply {
            spSchedulerAction.setOnItemSelectedListener {
                val actionName = spSchedulerAction.getItemAtPosition(it).toString()
                spSchedulerScene.isVisible = actionName.contains("Scene")
            }
            spSchedulerHour.setOnItemSelectedListener {
                etSchedulerSpecificHour.isVisible = it == customHourValue
            }
            spSchedulerMinute.setOnItemSelectedListener {
                etSchedulerSpecificMinute.isVisible = it == customMinuteValue
            }
            spSchedulerSecond.setOnItemSelectedListener {
                etSchedulerSpecificSecond.isVisible = it == customSecondValue
            }
            swSchedulerEveryYear.setOnCheckedChangeListener { _, isChecked ->
                etSchedulerYear.isEnabled = isChecked.not()
            }
            swSchedulerEveryMonth.setOnCheckedChangeListener { _, isChecked ->
                spSchedulerMonth.isEnabled = isChecked.not()
            }
            swSchedulerEveryDay.setOnCheckedChangeListener { _, isChecked ->
                etSchedulerDay.isEnabled = isChecked.not()
            }
            swSchedulerEveryDayOfWeek.setOnCheckedChangeListener { _, isChecked ->
                spSchedulerDayOfWeek.isEnabled = isChecked.not()
            }
        }
    }

    private fun onSchedulerActionSetButtonClick() {
        layout.apply {
            val isSpecificHourSelected =
                spSchedulerHour.selectedItemPosition == customHourValue
            val isSpecificMinuteSelected =
                spSchedulerMinute.selectedItemPosition == customMinuteValue
            val isSpecificSecondSelected =
                spSchedulerSecond.selectedItemPosition == customSecondValue

            try {
                val index = spSchedulerEntries.selectedItemPosition
                val action = when (spSchedulerAction.selectedItemPosition) {
                    0 -> Action.TurnOff
                    1 -> Action.TurnOn
                    2 -> Action.SceneRecall
                    3 -> Action.NoAction
                    else -> throw IllegalStateException()
                }

                val scene = getCurrentScene(action)
                val year = getCurrentYear()
                val months = if (
                    swSchedulerEveryMonth.isChecked) {
                    4095
                } else {
                    1 shl
                    spSchedulerMonth
                .selectedItemPosition
                }
                val day = getCurrentDay()
                val daysOfWeek = if (
                    swSchedulerEveryDayOfWeek.isChecked) {
                    0b01111111
                } else {
                    1 shl
                    spSchedulerDayOfWeek
                .selectedItemPosition
                }
                val hour = getCurrentHour(isSpecificHourSelected)
                val minute = getCurrentMinute(isSpecificMinuteSelected)
                val second = getCurrentSecond(isSpecificSecondSelected)

                val transitionTime = 0
                val schedulerParams = SchedulerParams(
                    index,
                    year,
                    months,
                    day,
                    hour,
                    minute,
                    second,
                    daysOfWeek,
                    action,
                    transitionTime,
                    scene
                )
                schedulerPresenter.onSchedulerSetButtonClick(
                    schedulerParams,
                )
            } catch (e: NumberFormatException) {
                MeshToast.show(
                    requireContext(),
                    requireContext().getString(
                        R.string.device_adapter_scheduler_wrong_input_format,
                        e.message
                    )
                )
            } catch (e: InvalidRangeException) {
                MeshToast.show(requireContext(), e.toastMessage)
            }
        }
    }

    private fun bindDefaultAction() {
        layout.apply {
            spSchedulerAction.setSelection(3)
            etSchedulerYear.setText(R.string.device_adapter_scheduler_default_year)
            spSchedulerMonth.setSelection(0)
            etSchedulerDay.setText("")
            spSchedulerDayOfWeek.setSelection(0)
            etSchedulerYear.isEnabled = false
            spSchedulerMonth.isEnabled = false
            etSchedulerDay.isEnabled = false
            spSchedulerDayOfWeek.isEnabled = false
            swSchedulerEveryYear.isChecked = true
            swSchedulerEveryMonth.isChecked = true
            swSchedulerEveryDay.isChecked = true
            swSchedulerEveryDayOfWeek.isChecked = true
            spSchedulerHour.setSelection(0)
            spSchedulerMinute.setSelection(0)
            spSchedulerSecond.setSelection(0)
        }
    }

    private fun bindAction(status: SchedulerActionResponse) {
        layout.apply {
            spSchedulerAction.setSelection(status.action.ordinal)
            if (status.action == Action.SceneRecall) {
                spSchedulerScene.setSelection(status.sceneNumber.toInt() - 1)
            }
            bindActionDate(status)
            bindActionTime(status)
        }
    }

    private fun bindActionDate(status: SchedulerActionResponse) {
        layout.apply {
            if (status.year == everyYearValue) {
                swSchedulerEveryYear.isChecked = true
                etSchedulerYear.isEnabled = false
            } else {
                swSchedulerEveryYear.isChecked = false

                etSchedulerYear.apply {
                    isEnabled = true
                    setText(
                        requireContext().getString(
                            R.string.device_adapter_scheduler_year_number_format,
                            status.year.toInt(),
                        )
                    )
                }
            }
            if (status.month == everyMonthValue) {
                swSchedulerEveryMonth.isChecked = true
                spSchedulerMonth.isEnabled = false
            } else {
                swSchedulerEveryMonth.isChecked = false
                spSchedulerMonth.isEnabled = true
                val position = when (status.month.toInt()) {
                    1 -> 0
                    2 -> 1
                    4 -> 2
                    8 -> 3
                    16 -> 4
                    32 -> 5
                    64 -> 6
                    128 -> 7
                    256 -> 8
                    512 -> 9
                    1024 -> 10
                    2048 -> 11
                    else -> null
                }
                position?.let {
                    spSchedulerMonth.setSelection(it)
                }
            }
            if (status.day == everyDayValue) {
                swSchedulerEveryDay.isChecked = true
                etSchedulerDay.isEnabled = false
            } else {
                swSchedulerEveryDay.isChecked = false

                etSchedulerDay.apply {
                    isEnabled = true
                    setText(status.day.toString())
                }
            }
            if (status.dayOfWeek == everyDayOfWeekValue) {
                swSchedulerEveryDayOfWeek.isChecked = true
                spSchedulerDayOfWeek.isEnabled = false
            } else {
                swSchedulerEveryDayOfWeek.isChecked = false
                spSchedulerDayOfWeek.isEnabled = true
                val position = when (status.dayOfWeek.toInt()) {
                    1 -> 0
                    2 -> 1
                    4 -> 2
                    8 -> 3
                    16 -> 4
                    32 -> 5
                    64 -> 6
                    else -> null
                }
                position?.let {
                    spSchedulerDayOfWeek.setSelection(it)
                }
            }
        }
    }

    private fun bindActionTime(status: SchedulerActionResponse) {
        layout.apply {
            if (status.hour in SchedulerParams.HOUR_MIN..SchedulerParams.HOUR_MAX) {
                spSchedulerHour.setSelection(2)
                etSchedulerSpecificHour.setText(status.hour.toString())
            } else {
                spSchedulerHour.setSelection(status.hour.rem(SchedulerParams.HOUR_MAX + 1u).toInt())
            }
            if (status.minute in SchedulerParams.MINUTE_MIN..SchedulerParams.MINUTE_MAX) {
                spSchedulerMinute.setSelection(4)
                etSchedulerSpecificMinute.setText(status.minute.toString())
            } else {
                spSchedulerMinute.setSelection(
                    status.minute.rem(SchedulerParams.MINUTE_MAX + 1u).toInt()
                )
            }
            if (status.second in SchedulerParams.SECOND_MIN..SchedulerParams.SECOND_MAX) {
                spSchedulerSecond.setSelection(4)
                etSchedulerSpecificSecond.setText(status.second.toString())
            } else {
                spSchedulerSecond.setSelection(
                    status.second.rem(SchedulerParams.SECOND_MAX + 1u).toInt()
                )
            }
        }
    }

    private fun setYearEditTextListeners() {
        layout.etSchedulerYear.apply {
            setOnFocusChangeListener { _, _ ->
                Selection.setSelection(text, 1)
            }
            addTextChangedListener(YearTextWatcher(this))
        }
    }

    override fun showToast(message: String) {
        MeshToast.show(requireContext(), message)
    }

    override fun showToast(stackError: StackError) {
        MeshToast.show(requireContext(), stackError.toString())
    }

    override fun notifyItemChanged(item: MeshNode) {
        bindScheduler()
    }

    private fun getCurrentScene(action: Action): Int {
        return if (action == Action.SceneRecall) {
            layout.spSchedulerScene.selectedItemPosition + 1
        } else {
            0
        }
    }

    private fun getCurrentYear(): UByte {
        layout.run {
            return if (swSchedulerEveryYear.isChecked) {
                everyYearValue
            } else {
                validateYear(etSchedulerYear.text.substring(1).toUByte())
            }
        }
    }

    private fun getCurrentDay(): Int {
        layout.run {
            return if (swSchedulerEveryDay.isChecked) {
                0
            } else {
                validateDay(etSchedulerDay.toInt())
            }
        }
    }

    private fun getCurrentHour(selected: Boolean): UByte {
        layout.run {
            return if (selected) {
                validateHour(etSchedulerSpecificHour.text.toString())
            } else {
                when (spSchedulerHour.selectedItemPosition) {
                    0 -> 24u
                    1 -> 25u
                    else -> throw IllegalStateException()
                }
            }
        }
    }

    private fun getCurrentMinute(selected: Boolean): UByte {
        layout.run {
            return if (selected) {
                validateMinute(etSchedulerSpecificMinute.text.toString())
            } else {
                when (spSchedulerMinute.selectedItemPosition) {
                    0 -> 60u
                    1 -> 61u
                    2 -> 62u
                    3 -> 63u
                    else -> throw IllegalStateException()
                }
            }
        }
    }

    private fun getCurrentSecond(selected: Boolean): UByte {
        layout.run {
            return if (selected) {
                validateSecond(etSchedulerSpecificSecond.text.toString())
            } else {
                when (spSchedulerSecond.selectedItemPosition) {
                    0 -> 60u
                    1 -> 61u
                    2 -> 62u
                    3 -> 63u
                    else -> throw IllegalStateException()
                }
            }
        }
    }

    private fun validateYear(year: UByte): UByte {
        return if (SchedulerParams.isYearValid(year)) year else throw InvalidRangeException(
            SchedulerParams.yearInvalidRangeMessage()
        )
    }

    private fun validateDay(day: Int): Int {
        return if (SchedulerParams.isDayValid(day)) day else throw InvalidRangeException(
            SchedulerParams.dayInvalidRangeMessage()
        )
    }

    private fun validateHour(hour: String): UByte {
        return if (hour.isEmpty()) {
            return 0u
        } else if (SchedulerParams.isHourValid(hour.toInt())) {
            hour.toUByte()
        } else {throw InvalidRangeException(
            SchedulerParams.hourInvalidRangeMessage())
        }
    }

    private fun validateMinute(minute: String): UByte {
        return if (minute.isEmpty()) {
            0u
        } else if (SchedulerParams.isMinuteValid(minute.toInt())) {
            minute.toUByte()
        } else {throw InvalidRangeException(
            SchedulerParams.minuteInvalidRangeMessage())
        }
    }

    private fun validateSecond(second: String): UByte {
        return if (second.isEmpty()) {
            0u
        } else if (SchedulerParams.isSecondValid(second.toInt())) {
            second.toUByte()
        } else {throw InvalidRangeException(
            SchedulerParams.secondInvalidRangeMessage())
        }
    }

    private class Adapter(context: Context, items: Array<String>) :
        ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
        init {
            setDropDownViewResource(androidx.appcompat.R.layout.support_simple_spinner_dropdown_item)
        }
    }

    private class InvalidRangeException(val toastMessage: String) : Exception()

    private fun mapSchedulerEntries(): Array<String> =
        schedulerPresenter.meshNode.schedules.mapIndexed { index, isScheduled ->
            "Entry ${index + 1}${if (isScheduled) " (Scheduled)" else ""}"
        }.toTypedArray()
}