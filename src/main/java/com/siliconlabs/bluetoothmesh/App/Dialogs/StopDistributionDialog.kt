/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogStopDistributionBinding

class StopDistributionDialog(
        private val onCancelDistributionClick: () -> Unit,
) : DialogFragment() {

    private val layout by viewBinding(DialogStopDistributionBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog?.apply {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.setLayout(width, height)
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_stop_distribution, container, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpButtonsListeners()
    }

    private fun setUpButtonsListeners() {
        layout.apply {
            buttonStopDistribution.setOnClickListener {
                onCancelDistributionClick()
                dismiss()
            }
            buttonDiscardDialog.setOnClickListener {
                dismiss()
            }
        }
    }
}