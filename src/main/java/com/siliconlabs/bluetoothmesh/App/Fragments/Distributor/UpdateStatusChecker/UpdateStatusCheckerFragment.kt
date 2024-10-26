/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.UpdateStatusChecker

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributorPhase
import com.siliconlabs.bluetoothmesh.App.Activities.Main.MainActivity
import com.siliconlabs.bluetoothmesh.App.Dialogs.DisconnectionDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.UpdateStatusChecker.UpdateStatusCheckerViewModel.Status
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.launchAndRepeatWhenResumed
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.showOnce
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentDistributionPhaseCheckerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class UpdateStatusCheckerFragment
@Deprecated(
    "use newInstance(Node)",
    replaceWith = ReplaceWith("UpdateStatusCheckerFragment.newInstance(distributor)")
) constructor() : Fragment(R.layout.fragment_distribution_phase_checker) {
    companion object {
        val backstackTAG: String = UpdateStatusCheckerFragment::class.java.name

        fun newInstance(distributor: Node) =
            @Suppress("DEPRECATION")
            UpdateStatusCheckerFragment().withMeshNavArg(distributor.toNavArg())
    }

    private val layout by viewBinding(FragmentDistributionPhaseCheckerBinding::bind)
    private val viewModel: UpdateStatusCheckerViewModel by viewModels()

    private val disconnectionDialog = DisconnectionDialog {
        requireMainActivity().exitDeviceFirmwareUpdate()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setActionBarTitle()
        collectDistributorStatus()
    }

    private fun setActionBarTitle() {
        requireMainActivity().setActionBar(title = null)
    }

    private fun collectDistributorStatus() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.status.collectLatest { status ->
                when (status) {
                    Status.INIT -> Unit
                    Status.DISCONNECTED -> showDisconnectionDialog()
                    Status.TIMEOUT -> showTimeoutToastAndLeave()
                    is Status.Phase -> setDistributionPhase(status.phase)
                }
            }
        }
    }

    private fun showTimeoutToastAndLeave() {
        activity?.let {
            MeshToast.show(it, it.getString(R.string.timeout), Toast.LENGTH_LONG)
            (it as MainActivity).exitDeviceFirmwareUpdate()
        }
    }

    private fun showDisconnectionDialog() {
        layout.layoutCheckingDistributor.isGone = true
        disconnectionDialog.showOnce(childFragmentManager)
    }

    private fun setDistributionPhase(phase: DistributorPhase) {
        when (phase) {
            DistributorPhase.IDLE -> navigateToDistributionIdleFragment()
            else -> navigateToUpdateNodesFragment()
        }
    }

    private fun navigateToDistributionIdleFragment() {
        activity?.let {
            (it as MainActivity).navigateToDistributionIdleFragment(viewModel.distributor)
        } ?: stopProgressBarAndShowErrorToast()
    }

    private fun navigateToUpdateNodesFragment() {
        activity?.let {
            (it as MainActivity).navigateToUpdateNodesFragment(viewModel.distributor)
        } ?: stopProgressBarAndShowErrorToast()
    }

    private fun stopProgressBarAndShowErrorToast() {
        layout.progressBarCheckingDistributorPhase.isGone = true
        context?.let {
            MeshToast.show(it, R.string.fetching_distributor_status_failed)
        }
    }
}