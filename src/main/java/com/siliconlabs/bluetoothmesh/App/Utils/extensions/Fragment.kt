/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import android.app.AlertDialog
import androidx.fragment.app.Fragment
import com.siliconlabs.bluetoothmesh.App.Activities.Main.MainActivity
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.isCritical
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.flow.Flow

fun Fragment.requireMainActivity() = requireActivity() as MainActivity

/**
 * If [onCriticalMessage] is provided it gets invoked when dialog is dismissed after displaying critical message.
 * */
fun Fragment.showErrorDialog(
    message: Message,
    defaultTitle: String? = null,
    onCriticalMessage: ((Message) -> Unit)? = null,
) {
    AlertDialog.Builder(requireContext())
        .setTitle(message.title ?: defaultTitle)
        .setMessage(message.message)
        .setPositiveButton(R.string.dialog_positive_ok, null)
        .apply {
            if (message.isCritical && onCriticalMessage != null) {
                setOnDismissListener{
                    onCriticalMessage(message)
                }
            }
        }
        .show()
}

fun Fragment.collectMessages(
    from: Flow<Message>, defaultTitle: String?,
    onCriticalMessage: ((Message) -> Unit)? = null,
) = viewLifecycleOwner.launchAndRepeatWhenResumed {
        from.collect {
            when (it.level) {
                Message.Level.INFO -> MeshToast.show(requireContext(), it.message)
                else -> showErrorDialog(it, defaultTitle, onCriticalMessage)
            }
        }
    }

/**
 * Default message collection that only shows toast for info and dialog with OK button for errors.
 *
 * If [onCriticalMessage] is provided it gets invoked when dialog is dismissed after displaying critical message.
 * */
fun Fragment.collectMessages(
    from: ViewModelWithMessages, defaultTitle: String? = null,
    onCriticalMessage: ((Message) -> Unit)? = null,
) = collectMessages(from.messageFlow, defaultTitle, onCriticalMessage)