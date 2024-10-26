/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogInteroperabilityTestsOutputOobAuthBinding

class OutputOOBDialog(private val context: Context) {

    private var dialog: AlertDialog? = null

    fun show(callback: (Int) -> Unit) {
        with(createDialog(callback)) {
            dialog = this
            show()
        }
    }

    private fun createDialog(callback: (Int) -> Unit): AlertDialog {
        val binding = DialogInteroperabilityTestsOutputOobAuthBinding.inflate(LayoutInflater.from(context))

        fun provideValue() {
            val providedValue = binding.oobValue.text.toString()
            callback.invoke(providedValue.toIntOrNull() ?: 0)
        }

        return AlertDialog.Builder(context).apply {
            setView(binding.root)
            setTitle("Authentication Output OOB")
            setPositiveButton(R.string.dialog_positive_ok) { _, _ -> provideValue() }
            setCancelable(false)
        }.create()
    }

    fun hide() {
        with(dialog) {
            this?.dismiss()
            dialog = null
        }
    }
}