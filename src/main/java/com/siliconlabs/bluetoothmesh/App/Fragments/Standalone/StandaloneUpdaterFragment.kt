/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Standalone

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.launch
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.*
import com.siliconlabs.bluetoothmesh.App.Dialogs.DisconnectionDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Logic.ConnectionState
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.ActivityResultContractsExt
import com.siliconlabs.bluetoothmesh.App.Utils.TarGzip.TarGzipFirmwareFactory
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.formattedFirmwareId
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.collectMessages
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.getFileName
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.launchAndRepeatWhenResumed
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.showOnce
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentStandaloneUpdaterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.*
import org.tinylog.kotlin.Logger

@AndroidEntryPoint
class StandaloneUpdaterFragment
@Deprecated(
    "use newInstance(Node)",
    replaceWith = ReplaceWith("StandaloneUpdaterFragment.newInstance(distributorNode)")
)
constructor() : Fragment(R.layout.fragment_standalone_updater) {
    companion object {
        fun newInstance(distributor: Node) =
            @Suppress("DEPRECATION")
            StandaloneUpdaterFragment().withMeshNavArg(distributor.toNavArg())
    }

    private val selectFile =
        registerForActivityResult(ActivityResultContractsExt.GzipFilePickerContract()) { result ->
            result?.onSuccess {
                viewModel.setSelectedFirmware(it)
            }?.onFailure {
                showProcessSelectedFileError()
            }
        }

    private val layout by viewBinding(FragmentStandaloneUpdaterBinding::bind)
    private val viewModel: StandaloneUpdaterViewModel by viewModels()

    private val progressSection = StandaloneUpdaterProgressSection()

    private val disconnectionDialog = DisconnectionDialog {
        exitStandaloneView()
    }

    private fun exitStandaloneView() {
        requireMainActivity().popWholeBackStack()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpBackListener()
        setUpToolbar()
        collectMessages(viewModel)
        setUpNetworkState()
        setUpUpdateButton()
        setUpFirmwareSelection()
        setUpProgressDisplay()
        setupAdvertisementExtensionSwitch()
    }

    private fun setUpBackListener() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when (viewModel.currentUpdatePhase) {
                        Idle, Completed, Failed -> {
                            navigateBackToConfigurationView()
                        }
                        else -> {
                            showCancelUpdateDialog()
                        }
                    }
                }
            })
    }

    private fun setUpToolbar() {
        requireMainActivity().setActionBar(viewModel.node.name)
    }

    private fun setUpNetworkState() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.networkConnectionState.collect {
                layout.tvReconnectingState.isVisible = it == ConnectionState.CONNECTING
                if (it == ConnectionState.DISCONNECTED) {
                    disconnectionDialog.showOnce(childFragmentManager)
                } else if (disconnectionDialog.isAdded) {
                    disconnectionDialog.dismiss()
                }
            }
        }
    }

    private fun setUpUpdateButton() {
        layout.buttonUpdate.setOnClickListener {
            when (viewModel.currentUpdatePhase) {
                Completed, Failed -> navigateBackToConfigurationView()
                // possible to use stopUpdate to clear the error and try again
                //Failed -> viewModel.stopUpdate()
                Idle, Cancelling -> viewModel.startUpdate()
                else -> viewModel.stopUpdate()  //showCancelUpdateDialog()
            }
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.isStartFirmwareUpdateButtonEnabled.collect {
                layout.buttonUpdate.isEnabled = it
            }
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.updatePhase.collect {
                layout.buttonUpdate.text = when (it.first) {
                    Completed -> "Done"
                    Cancelling -> "Cancelling"
                    Idle -> "Upload and apply"
                    Failed -> "Back"
                    else -> "Cancel"
                }
            }
        }
    }

    private fun setUpFirmwareSelection() {
        layout.tvFirmwareVersion.text =
            viewModel.node.deviceCompositionData?.formattedFirmwareId()

        layout.wrapperFirmwarePicker.setOnClickListener {
            selectFile.launch()
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.firmwareUri.collectLatest {
                layout.apply {
                    if (it == null)
                        tvFirmwareName.setText(R.string.standalone_pick_a_file)
                    else
                        tvFirmwareName.text = requireContext().getFileName(it)
                }
            }
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.updatePhase.collectLatest {
                layout.wrapperFirmwarePicker.isEnabled = it.first == Idle
            }
        }
    }

    private fun setUpProgressDisplay() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            combine(viewModel.firmwareState, viewModel.updatePhase) { firmwareState, updatePhase ->
                Logger.debug { "Got $updatePhase, fw: $firmwareState" }
                // no progress (from Progress) or firmwareId (from Success) to display in spec,
                // so no sealed class switch just display errors...
                if (firmwareState is TarGzipFirmwareFactory.Output.Failure) {
                    showErrorText(firmwareState.error.message)
                } else
                    showErrorText(null)

                updatePhase
            }.distinctUntilChanged().collect {
                updateProgressSection(it.first, it.second, viewModel.previousUpdatePhase)
            }
        }
    }

    private fun setupAdvertisementExtensionSwitch() {
        layout.switchUseAdvertisementExtension.apply {
            if (viewModel.supportsAdvertisementExtension) {
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.useAdvertisementExtension = isChecked
                }
                isVisible = true
            } else
                isVisible = false
        }
    }

    private fun showErrorText(message: String?) {
        layout.apply {
            val showError = !message.isNullOrEmpty()
            groupErrorDisplay.isVisible = showError
            tvError.text = if (showError) message else ""
        }
    }

    private fun updateProgressSection(currentPhase: Phase, progress: Double, previousPhase: Phase) {
        val (uploadText, verificationText, applyingText) = progressSection.getColoredTexts(
            currentPhase,
            previousPhase,
            progress
        )
        layout.apply {
            tvUploadProgress.apply {
                text = uploadText.text
                setTextColor(uploadText.color)
            }
            tvVerificationProgress.apply {
                text = verificationText.text
                setTextColor(verificationText.color)
            }
            tvApplyingProgress.apply {
                text = applyingText.text
                setTextColor(applyingText.color)
            }
        }
    }

    private fun navigateBackToConfigurationView() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    private fun showCancelUpdateDialog() {
        AlertDialog.Builder(requireContext()).run {
            setMessage(this@StandaloneUpdaterFragment.getString(R.string.standalone_cancellation_message))
            setPositiveButton(R.string.dialog_yes) { _, _ ->
                viewModel.stopUpdate()
                navigateBackToConfigurationView()
            }
            setNegativeButton(R.string.dialog_no, null)
            show()
        }
    }

    private fun showProcessSelectedFileError() {
        showErrorText(getString(R.string.error_process_selected_file))
    }
}