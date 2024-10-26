/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning

import android.app.AlertDialog
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.App.Utils.isCritical
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

abstract class ProvisioningFragmentBase(contentLayoutId: Int) : Fragment(contentLayoutId) {

    abstract fun isProvisioningActive(): Boolean
    open fun navigateBack() = activity?.supportFragmentManager?.popBackStack()

    protected fun deviceToProvisionIsValid(): Boolean {
        // edge case for when app is recreated from background and singleton is not in correct state
        // this HAS to prevent the fragment from attempting to touch viewmodel because it has
        // no "missing deviceToProvision" path
        if (AppState.deviceToProvision == null) {
            showErrorDialog(Message.critical(R.string.error_message_provisioning_no_device))
            return false
        }
        return true
    }

    protected fun collectMessages(messageFlow: Flow<Message>) {
        viewLifecycleScope.launch {
            messageFlow.collect {
                when (it.level) {
                    Message.Level.INFO -> MeshToast.show(requireContext(), it.message)
                    else -> showErrorDialog(it)
                }
            }
        }
    }

    protected fun setupBackPressedBehaviour() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (isProvisioningActive()) {
                MeshToast.show(
                    requireContext(),
                    getString(R.string.provisioning_back_button_disabled)
                )
            } else {
                navigateBack()
            }
        }
    }

    protected fun showErrorDialog(message: Message) {
        AlertDialog.Builder(requireContext()).run {
            setTitle(message.title ?: getString(R.string.provisioning_failed))
            setMessage(message.message)
            setPositiveButton(R.string.dialog_positive_ok, null)
            if (message.isCritical) {
                setOnDismissListener { navigateBack() }
            }
            show()
        }
    }
}