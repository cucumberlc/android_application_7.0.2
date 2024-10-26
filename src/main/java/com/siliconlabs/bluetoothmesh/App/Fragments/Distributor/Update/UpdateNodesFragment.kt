/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Update

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.DistributorPhase
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatingNode
import com.siliconlabs.bluetoothmesh.App.Dialogs.DisconnectionDialog
import com.siliconlabs.bluetoothmesh.App.Dialogs.StopDistributionDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.FirmwareReceiversAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Utils.updatingNodeComparator
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.*
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentUpdateNodesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UpdateNodesFragment
@Deprecated(
    "use newInstance(Node)",
    replaceWith = ReplaceWith("UpdateNodesFragment.newInstance(distributor)")
) constructor() : Fragment(R.layout.fragment_update_nodes), UpdateNodesView {
    companion object{
        fun newInstance(distributor: Node) =
            @Suppress("DEPRECATION")
            UpdateNodesFragment().withMeshNavArg(distributor.toNavArg())
    }

    private val presenter: UpdateNodesPresenter by presenters()

    private val layout by viewBinding(FragmentUpdateNodesBinding::bind)

    private val stopDistributionDialog = StopDistributionDialog {
        presenter.cancelDistribution()
    }
    private val disconnectionDialog = DisconnectionDialog {
        exitDeviceFirmwareUpdate()
    }
    private var currentDistributorPhase = DistributorPhase.IDLE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setActionBarTitle(getString(R.string.device_adapter_firmware_distribution))

        presenter.startDistribution()
        setOnBackPressedDispatcher()
        setUpActionButtonsListener()
    }

    override fun showFirmwareId(firmwareId: String) {
        layout.tvFirmwareIdName.text = firmwareId
    }

    private fun setOnBackPressedDispatcher() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    exitDeviceFirmwareUpdate()
                }
            })
    }

    override fun exitDeviceFirmwareUpdate() {
        requireMainActivity().exitDeviceFirmwareUpdate()
    }

    private fun showStopDistributionDialog() {
        stopDistributionDialog.show(childFragmentManager, null)
    }

    private fun setUpActionButtonsListener() {
        layout.buttonCancelUpdate.setOnClickListener {
            showStopDistributionDialog()
        }
    }

    override fun showUpdatingNodes(updatingNodes: Collection<UpdatingNode>) {
        activity?.runOnUiThread {
            val sortedByErrorNodes = updatingNodes.sortedWith(updatingNodeComparator)
            layout.recViewFirmwareReceivers.adapter = FirmwareReceiversAdapter(sortedByErrorNodes)
        }
    }

    override fun showDistributionPhase(distributorPhase: DistributorPhase) {
        currentDistributorPhase = distributorPhase

        refreshDistributorPhase()
        refreshActionButtons()
        checkDistributorPhase()
    }

    private fun refreshDistributorPhase() {
        val distributorStatus = getBeautifiedDistributorPhase()
        val distributionColor = getDistributionColor()

        layout.tvCurrentStateName.apply {
            text = distributorStatus
            setTextColor(distributionColor)
        }
    }

    private fun getBeautifiedDistributorPhase(): String {
        return when (currentDistributorPhase) {
            DistributorPhase.IDLE -> getString(R.string.distribution_idle)
            DistributorPhase.ACTIVE -> getString(R.string.distribution_in_progress)
            DistributorPhase.TRANSFERRED -> getString(R.string.distribution_ready_to_apply)
            DistributorPhase.APPLYING -> getString(R.string.distribution_applying)
            DistributorPhase.COMPLETED -> getString(R.string.distribution_completed)
            DistributorPhase.FAILED -> getString(R.string.distribution_failed)
            DistributorPhase.CANCELLING -> getString(R.string.distribution_cancelling_update)
            else -> getString(R.string.unknown_phase)
        }
    }

    private fun getDistributionColor(): Int {
        val colorBlue = ContextCompat.getColor(requireContext(), android.R.color.holo_blue_bright)
        val colorGreen = ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
        val colorRed = ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
        val colorWhite = ContextCompat.getColor(requireContext(), android.R.color.white)

        return when (currentDistributorPhase) {
            DistributorPhase.IDLE -> colorWhite
            DistributorPhase.ACTIVE -> colorBlue
            DistributorPhase.TRANSFERRED -> colorGreen
            DistributorPhase.APPLYING -> colorBlue
            DistributorPhase.COMPLETED -> colorGreen
            DistributorPhase.FAILED -> colorRed
            DistributorPhase.CANCELLING -> colorBlue
            else -> colorWhite
        }
    }

    private fun refreshActionButtons() {
        val colorTransparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)
        val colorPrimaryRed = ContextCompat.getColor(requireContext(), R.color.alizarin_crimson)
        layout.apply {
            when (currentDistributorPhase) {
                DistributorPhase.IDLE -> {
                    buttonApplyUpdate.isGone = true
                    buttonCancelUpdate.setBackgroundColor(colorPrimaryRed)
                }

                DistributorPhase.ACTIVE -> {
                    buttonApplyUpdate.isGone = true
                    buttonCancelUpdate.setBackgroundColor(colorPrimaryRed)
                }

                DistributorPhase.TRANSFERRED -> {
                    buttonCancelUpdate.apply {
                        setBackgroundColor(colorPrimaryRed)
                        isVisible = true
                    }
                }

                DistributorPhase.APPLYING -> {
                    buttonApplyUpdate.isGone = true

                    buttonCancelUpdate.apply {
                        setBackgroundColor(colorPrimaryRed)
                        isVisible = true
                    }
                }

                DistributorPhase.COMPLETED -> {
                    buttonCancelUpdate.isGone = true
                    buttonApplyUpdate.apply {
                        text = getString(R.string.distribution_done)
                        isVisible = true
                        setOnClickListener {
                            presenter.resetDistributor()
                        }
                    }
                }

                DistributorPhase.FAILED -> {
                    buttonApplyUpdate.apply {
                        text = getString(R.string.distribution_try_update_again)
                        isVisible = true
                        setOnClickListener {
                            presenter.startUpdateAgainOnFailedNodes()
                        }
                    }
                    buttonCancelUpdate.apply {
                        setBackgroundColor(colorTransparent)
                        isVisible = true
                    }
                }

                DistributorPhase.CANCELLING -> {
                    buttonApplyUpdate.isGone = true
                    buttonCancelUpdate.setBackgroundColor(colorPrimaryRed)
                }

                else -> Unit
            }
        }
    }

    private fun checkDistributorPhase() {
        if (currentDistributorPhase == DistributorPhase.IDLE)
            exitDeviceFirmwareUpdate()
    }

    override fun setActionBarTitle(title: String) {
        requireMainActivity().setActionBar(title)
    }

    override fun showWarningToast(message: String) {
        MeshToast.show(requireContext(), message)
    }

    override fun showDisconnectionDialog() {
        dismissAllVisibleDialogs()
        disconnectionDialog.showOnce(childFragmentManager)
    }

    private fun dismissAllVisibleDialogs() {
        stopDistributionDialog.dismissIfVisible()
    }
}