/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogDeleteDeviceBinding

class DeleteDeviceDialog(
        private val deleteDevice: () -> Unit
) : DialogFragment() {

    private val layout by viewBinding(DialogDeleteDeviceBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog?.apply {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.setLayout(width, height)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_delete_device, container, true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpButtonsListeners()
    }

    override fun onPause() {
        super.onPause()
        dismiss()
    }

    private fun setUpButtonsListeners() {
        layout.buttonDelete.setOnClickListener {
            deleteDevice()
            dismiss()
        }
        layout.buttonCancel.setOnClickListener {
            dismiss()
        }
    }
}