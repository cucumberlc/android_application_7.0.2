/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogDeleteDeviceLocallyBinding
import kotlinx.coroutines.launch

class DeleteDeviceLocallyDialog(
        private val deleteDeviceLocally: () -> Unit
) : DialogFragment() {
    private val layout by viewBinding(DialogDeleteDeviceLocallyBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog?.apply {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.setLayout(width, height)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_delete_device_locally, container, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpButtonsListeners()
    }

    private fun setUpButtonsListeners() {
        layout.buttonDelete.setOnClickListener {
            deleteDeviceLocally()
            dismiss()
        }

        layout.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    fun setText(errorMessage: String) {
        lifecycleScope.launch {
            withStarted { layout.tvDeleteErrorMessage.text = errorMessage }
        }
    }
}