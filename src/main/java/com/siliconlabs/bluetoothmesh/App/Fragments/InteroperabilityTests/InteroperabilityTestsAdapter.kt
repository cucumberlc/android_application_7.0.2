/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ItemInteroperabilityTestBinding

class InteroperabilityTestsAdapter : ListAdapter<IOPTestItemUiState, InteroperabilityTestsAdapter.ViewHolder>(
        IOPTestItemUiState.Companion.DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ItemInteroperabilityTestBinding
                    .inflate(LayoutInflater.from(parent.context), parent, false)
                    .let { ViewHolder(it) }

    override fun onBindViewHolder(holder: ViewHolder, position: Int,
                                  payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            holder.updateTestStateIndicators(payloads.last() as IOPTest.State)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
            private val binding: ItemInteroperabilityTestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(state: IOPTestItemUiState) {
            binding.apply {
                testTitle.text = state.id.title
                testDescription.text = state.id.description
                updateTestStateIndicators(state.testState)
            }
        }

        fun updateTestStateIndicators(state: IOPTest.State) {
            binding.apply {
                testState.text = when (state) {
                    IOPTest.State.Pending -> "Pending"
                    is IOPTest.State.InProgress -> "In progress"
                    is IOPTest.State.Passed -> "Passed"
                    is IOPTest.State.Failed -> "Failed"
                    IOPTest.State.Canceled -> "Canceled"
                }

                if (state is IOPTest.State.InProgress) {
                    testStateProgress.isVisible = true
                    testStateIcon.isInvisible = true
                } else {
                    testStateProgress.isInvisible = true
                    testStateIcon.isVisible = true
                }

                testStateIcon.setImageResource(when (state) {
                    is IOPTest.State.Passed -> R.drawable.ic_test_passed
                    is IOPTest.State.Failed -> R.drawable.ic_test_failed
                    is IOPTest.State.Canceled -> R.drawable.ic_test_canceled
                    else -> R.drawable.ic_test_pending
                    // in InProgress state status icon is hidden
                })
            }
        }
    }
}
