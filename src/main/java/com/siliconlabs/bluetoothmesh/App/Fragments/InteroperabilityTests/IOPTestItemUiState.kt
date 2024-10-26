/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests

import androidx.recyclerview.widget.DiffUtil
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTest
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestIdentity

data class IOPTestItemUiState(
        val id: IOPTestIdentity,
        val testState: IOPTest.State
) {
    companion object {
        object DiffCallback : DiffUtil.ItemCallback<IOPTestItemUiState>() {
            override fun areItemsTheSame(oldItem: IOPTestItemUiState,
                                         newItem: IOPTestItemUiState) = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: IOPTestItemUiState,
                                            newItem: IOPTestItemUiState) = oldItem == newItem

            override fun getChangePayload(oldItem: IOPTestItemUiState,
                                          newItem: IOPTestItemUiState) = newItem.testState
        }
    }
}
