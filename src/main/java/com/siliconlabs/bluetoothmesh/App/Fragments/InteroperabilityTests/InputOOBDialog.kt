/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests

import android.app.AlertDialog
import android.content.Context

class InputOOBDialog(context: Context) {

    private val dialog: AlertDialog =
            AlertDialog.Builder(context).apply {
                setTitle("Authentication Input OOB")
                setCancelable(false)
            }.create()

    fun show(value: Int) {
        dialog.apply {
            setMessage("Push the button on the device $value times")
            show()
        }
    }

    fun hide() = dialog.hide()
}