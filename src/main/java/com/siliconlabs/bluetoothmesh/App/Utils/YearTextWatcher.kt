/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import android.text.Editable
import android.text.Selection
import android.text.TextWatcher
import android.widget.EditText

/**
 * Preserves edit text format "’YY" and makes it overtype mode and two-sided
 */
class YearTextWatcher(private val yearEditText: EditText) : TextWatcher {
    private var previousYear: CharSequence = StringBuilder(yearEditText.text)
    private val yearRegex = Regex("’\\d{2}")

    override fun afterTextChanged(s: Editable?) {}

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        s?.takeIf { it.matches(yearRegex) && previousYear != s }
            ?.let { previousYear = StringBuilder(it) }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        if (s == null) {
            restorePreviousYear()
        } else if (s.matches(yearRegex)) {
            return
        } else if (count - before == 1) {
            replaceDigit(s, start)
        } else if (count - before == -1 && start > 0) {
            changeDigitToZero(start)
        } else {
            restorePreviousYear()
        }
    }

    private fun replaceDigit(s: CharSequence, start: Int) {
        val newDigit = s[start].toString()
        val newYear = when {
            start < 2 -> "’$newDigit${s[3]}"
            start == 2 -> "’${s[1]}$newDigit"
            else -> "’${s[2]}$newDigit"
        }
        val selection = when {
            start < 2 -> 2
            else -> 3
        }
        yearEditText.setText(newYear)
        Selection.setSelection(yearEditText.text, selection)
    }

    private fun changeDigitToZero(start: Int) {
        yearEditText.setText(previousYear.replaceRange(start, start + 1, "0"))
        Selection.setSelection(yearEditText.text, start)
    }

    private fun restorePreviousYear() {
        yearEditText.setText(StringBuilder(previousYear))
        Selection.setSelection(yearEditText.text, 1)
    }
}