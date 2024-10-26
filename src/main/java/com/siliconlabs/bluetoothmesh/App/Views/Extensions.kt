/*
 * Copyright © 2020 Silicon Labs, http://www.silabs.com. All rights reserved.
 */
package com.siliconlabs.bluetoothmesh.App.Views

import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.EditText
import android.widget.Spinner
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager

fun Spinner.setOnItemSelectedListenerOnViewCreated(onItemSelected: (position: Int) -> Unit) = post {
    onItemSelectedListener = object : OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onItemSelected.invoke(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }
}

fun Spinner.setOnItemSelectedListener(onItemSelected: (position: Int) -> Unit) {
    onItemSelectedListener = object : OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {}

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            onItemSelected(position)
        }
    }
}

fun EditText.toInt() = text.toString().toInt()

fun DialogFragment.dismissIfVisible() {
    if (isVisible)
        dismiss()
}

fun DialogFragment.showOnce(fragmentManager: FragmentManager,
                            tag: String = this.javaClass.simpleName) {
    fragmentManager.executePendingTransactions()
    val oldDialog = fragmentManager.findFragmentByTag(tag)

    if (oldDialog == null && !isAdded && !isVisible) {
        show(fragmentManager, tag)
    }
}

fun ContentLoadingProgressBar.showIf(show: Boolean) {
    if (show) show()
    else hide()
}