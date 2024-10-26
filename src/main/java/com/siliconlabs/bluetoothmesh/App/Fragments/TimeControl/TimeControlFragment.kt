/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.TimeControl

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.errors.StackError
import com.siliconlab.bluetoothmesh.adk.functionality_control.time.Role
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterTimeDetailBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TimeControlFragment
@Deprecated(
    "use newInstance(MeshNode)",
    replaceWith = ReplaceWith("TimeControlFragment.newInstance(meshNode)")
) constructor() : Fragment(R.layout.devices_adapter_time_detail), TimeControlView {
    companion object {
        fun newInstance(meshNode: MeshNode) =
            @Suppress("DEPRECATION")
            TimeControlFragment().withMeshNavArg(meshNode.node.toNavArg())
    }

    private val layout by viewBinding(DevicesAdapterTimeDetailBinding::bind)
    private val timeControlPresenter: TimeControlPresenter by presenters()

    private val meshNode
        get() = timeControlPresenter.meshNode

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTimeInfo()
    }

    private fun setTimeInfo() {
        layout.apply {
            tvTimeRoleGet.text = meshNode.timeRole

            setTimeParamsTexts(meshNode, swHumanReadable.isChecked)

            swHumanReadable.setOnCheckedChangeListener(null)

            btnTimeRoleGet.setOnClickListener { timeControlPresenter.getTimeRole() }
            btnSend.setOnClickListener {
                timeControlPresenter.setTimeRole(
                    when (spChooseTimeRole.selectedItemPosition) {
                        0 -> Role.None
                        1 -> Role.Authority
                        2 -> Role.Relay
                        3 -> Role.Client
                        else -> throw IllegalStateException()
                    }

                )
            }

            btnTimeGet.setOnClickListener { timeControlPresenter.getTime() }

            swHumanReadable.setOnCheckedChangeListener { _, isChecked ->
                setTimeParamsTexts(meshNode, isChecked)
            }

            // Local Time button
            btnLocalTime.setOnClickListener {
                etTaiSeconds.setText(TimeParams.getRawLocalTimeTaiSeconds().toString())
                etSubsecond.setText(TimeParams.getRawLocalTimeSubsecond().toString())
                etUncertainty.setText("0")
                etTimeAuthority.setText("0")
                etTaiUtcDelta.setText(TimeParams.TAI_UTC_DELTA_VALUE.toString())
                etTimeZoneOffset.setText(TimeParams.getLocalTimeZoneRawOffset())
            }

            btnCopyStatusFields.setOnClickListener {
                etTaiSeconds.setText(meshNode.taiSeconds.toString())
                etSubsecond.setText(meshNode.subsecond.toString())
                etUncertainty.setText(meshNode.uncertainty.toString())
                if (meshNode.timeAuthority) etTimeAuthority.setText("1")
                else etTimeAuthority.setText("0")
                etTaiUtcDelta.setText(meshNode.taiUtcDelta.toString())
                etTimeZoneOffset.setText(meshNode.timeZoneOffset.toString())
            }

            btnTimeSet.setOnClickListener {
                val timeValuesMap = HashMap<TimeParams.ParameterType, String>()

                if (etTaiSeconds.text.isNotEmpty()) timeValuesMap[TimeParams.ParameterType.TAI_SECONDS] =
                    etTaiSeconds.text.toString()
                if (etSubsecond.text.isNotEmpty()) timeValuesMap[TimeParams.ParameterType.SUBSECOND] =
                    etSubsecond.text.toString()
                if (etUncertainty.text.isNotEmpty()) timeValuesMap[TimeParams.ParameterType.UNCERTAINTY] =
                    etUncertainty.text.toString()
                if (etTimeAuthority.text.isNotEmpty()) timeValuesMap[TimeParams.ParameterType.TIME_AUTHORITY] =
                    etTimeAuthority.text
                        .toString()
                if (etTaiUtcDelta.text.isNotEmpty()) timeValuesMap[TimeParams.ParameterType.TAI_UTC_DELTA] =
                    etTaiUtcDelta.text.toString()
                if (etTimeZoneOffset.text.isNotEmpty()) timeValuesMap[TimeParams.ParameterType.TIME_ZONE_OFFSET] =
                    etTimeZoneOffset.text
                        .toString()

                if (timeValuesMap.size == 6) timeControlPresenter.setTime(
                    timeValuesMap,
                    requireContext()
                )
                else MeshToast.show(
                    requireContext(),
                    R.string.device_adapter_time_fields_cannot_be_empty
                )
            }
        }
    }

    private fun setTimeParamsTexts(meshNode: MeshNode, isChecked: Boolean) {
        layout.apply {
            if (isChecked) {
                tvTaiSeconds.text = TimeParams.getHumanReadableTaiSeconds(meshNode.taiSeconds.toLong())
                tvSubsecond.text = TimeParams.getHumanReadableSubsecond(meshNode.subsecond)
                tvUncertainty.text = TimeParams.getHumanReadableUncertainty(meshNode.uncertainty)
                tvTimeAuthority.text =
                    TimeParams.getHumanReadableTimeAuthority(meshNode.timeAuthority)
                tvTaiUtcDelta.text = TimeParams.getHumanReadableTaiUtcDelta(meshNode.taiUtcDelta)
                tvTimeZoneOffset.text =
                    TimeParams.getHumanReadableTimeZoneOffset(meshNode.timeZoneOffset)
            } else {
                tvTaiSeconds.text = meshNode.taiSeconds.toString()
                tvSubsecond.text = meshNode.subsecond.toString()
                tvUncertainty.text = meshNode.uncertainty.toString()
                if (meshNode.timeAuthority) tvTimeAuthority.text = "1"
                else tvTimeAuthority.text = "0"
                tvTaiUtcDelta.text = meshNode.taiUtcDelta.toString()
                tvTimeZoneOffset.text = meshNode.timeZoneOffset.toString()
            }
        }
    }

    override fun showToast(message: String) {
        MeshToast.show(requireContext(), message)
    }

    override fun showToast(stackError: StackError) {
        MeshToast.show(requireContext(), stackError.toString())
    }

    override fun notifyItemChanged(item: MeshNode) {
        setTimeInfo()
    }
}

