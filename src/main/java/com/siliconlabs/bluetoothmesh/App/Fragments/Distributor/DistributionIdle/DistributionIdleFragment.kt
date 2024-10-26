/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.DistributionIdle

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.launch
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.Dialogs.DisconnectionDialog
import com.siliconlabs.bluetoothmesh.App.Dialogs.UploadFirmwareDialog
import com.siliconlabs.bluetoothmesh.App.Dialogs.UploadFirmwarePresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.UpdatableNodesAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Logic.ConnectionState
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.ActivityResultContractsExt
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.TarGzip.TarGzipFirmwareFactory
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.*
import com.siliconlabs.bluetoothmesh.App.Views.*
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentDistributionIdleBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

// almost a copy of StandaloneUpdaterFragment
@AndroidEntryPoint
class DistributionIdleFragment
@Deprecated(
    "use newInstance(Node)",
    replaceWith = ReplaceWith("DistributionIdleFragment.newInstance(distributorNode)")
)
constructor() : Fragment(R.layout.fragment_distribution_idle) {
    companion object {
        val backstackTAG: String = DistributionIdleFragment::class.java.name

        fun newInstance(distributor: Node) =
            @Suppress("DEPRECATION")
            DistributionIdleFragment().withMeshNavArg(distributor.toNavArg())
    }

    private val selectFile =
        registerForActivityResult(ActivityResultContractsExt.GzipFilePickerContract()) { result ->
            result?.onSuccess {
                viewModel.setSelectedFirmware(it)
            }?.onFailure {
                showProcessSelectedFileError()
            }
        }

    private val layout by viewBinding(FragmentDistributionIdleBinding::bind)
    private val viewModel: DistributionIdleViewModel by viewModels()

    private lateinit var adapter: UpdatableNodesAdapter

    private val disconnectionDialog = DisconnectionDialog {
        exitDeviceFirmwareUpdate()
    }

    private val listener = object : UpdatableNodesAdapter.OnSelectedNodeChangeListener {
        override fun onSelectNode(node: Node) {
            viewModel.addSelectedNode(node)
        }

        override fun onUnselectNode(node: Node) {
            viewModel.removeUnselectedNode(node)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = UpdatableNodesAdapter(viewModel.updatableNodes)
        adapter.listener = listener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireMainActivity().setActionBar(getString(R.string.device_adapter_firmware_distribution))

        setOnBackPressedListener()
        collectMessages(viewModel)
        setUpRecyclerView()
        setUpNetworkState()
        setUpButtonState()
        setUpFirmwareState()
        setOnUploadFirmwareClickListener()
        setOnSelectFirmwareFileClickListener()
        setupAESwitch()
        setUpUploadFirmwareDialog()
    }

    private fun setOnBackPressedListener() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    exitDeviceFirmwareUpdate()
                }
            })
    }

    private fun setUpRecyclerView() {
        // it should be possible to restore selected nodes but need diff etc
        viewModel.clearSelectedNodes()
        adapter.clearSelection()

        layout.recViewUpdatableNodes.adapter = adapter
        layout.tvNoNodeToUpdate.isVisible = viewModel.updatableNodes.isEmpty()
    }

    private fun setUpNetworkState() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.networkConnectionState.collect {
                if (it == ConnectionState.DISCONNECTED) {
                    disconnectionDialog.showOnce(childFragmentManager)
                } else if (disconnectionDialog.isAdded) {
                    disconnectionDialog.dismiss()
                }

                layout.buttonUploadFirmware.setText(
                    if (it == ConnectionState.CONNECTING) {
                        R.string.upload_firmware_reconnecting
                    } else {
                        R.string.upload_firmware_to_distributor
                    }
                )
            }
        }
    }

    private fun setUpButtonState() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.isUploadToNodesButtonEnabled.collect {
                layout.buttonUploadFirmware.isEnabled = it
            }
        }
    }

    private fun setUpFirmwareState() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.firmwareUri.collectLatest {
                showFilename(it)
            }
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.firmwareState.collectLatest {
                when (it) {
                    is TarGzipFirmwareFactory.Output.UnpackProgress -> {
                        // no progress display in spec
                    }
                    is TarGzipFirmwareFactory.Output.Failure -> {
                        //todo: this should be a sticky error text and not a dialog
                        showErrorDialog(Message.error(it.error))
                    }
                    is TarGzipFirmwareFactory.Output.Success -> {
                        showFirmwareIdAndListOfNodes(it.firmwareId)
                    }
                    null -> {
                        showFirmwareIdAndListOfNodes(null)
                        AppState.firmware = null
                    }
                }
            }
        }
    }

    private fun exitDeviceFirmwareUpdate() {
        requireMainActivity().exitDeviceFirmwareUpdate()
    }

    private fun setOnUploadFirmwareClickListener() {
        layout.buttonUploadFirmware.setOnClickListener {
            viewModel.startUpload()
        }
    }

    private fun navigateToUpdateNodesFragment() {
        requireMainActivity().navigateToUpdateNodesFragment(viewModel.selectedDistributor)
    }

    private fun setOnSelectFirmwareFileClickListener() {
        layout.firmwareFileWrapper.setOnClickListener {
            selectFile.launch()
        }
    }

    private fun setupAESwitch() {
        layout.switchUseAdvertisementExtension.apply {
            if (viewModel.supportsAdvertisementExtension) {
                isVisible = true
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.useAdvertisementExtension = isChecked
                }
            } else {
                isGone = true
            }
        }
    }

    private fun showFilename(gzipFileUri: Uri?) {
        layout.tvFirmwareFileName.apply {
            isVisible = if (gzipFileUri != null) {
                text = requireContext().getFileName(gzipFileUri)
                true
            } else false
        }
    }

    private fun showFirmwareIdAndListOfNodes(firmwareId: String?) {
        layout.apply {
            groupFirmwareDataDisplay.isVisible = if(firmwareId != null){
                tvFirmwareIdName.text = firmwareId
                true
            } else false
        }
    }

    private fun setUpUploadFirmwareDialog() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.firmwareUploadStarted.collect {
                // consume start state
                if (it) {
                    createUploadDialog().showOnce(childFragmentManager)
                    viewModel.notifyFirmwareUploadStartedConsumed()
                }
            }
        }
    }

    private fun createUploadDialog() = UploadFirmwareDialog().apply {
        val presenter = UploadFirmwarePresenter(
            this,
            viewModel.selectedDistributor,
            viewModel.selectedNodesToUpdate.value
        )

        isCancelable = false
        uploadFirmwareDoneListener =
            object : UploadFirmwareDialog.OnUploadFirmwareDoneListener {
                override fun onUploadFirmwareDone() {
                    navigateToUpdateNodesFragment()
                }
            }
        uploadFirmwareListener = presenter
    }

    private fun showProcessSelectedFileError() {
        requireActivity().runOnUiThread {
            MeshToast.show(requireContext(), R.string.error_process_selected_file)
        }
    }

    override fun onDestroyView() {
        layout.recViewUpdatableNodes.adapter = null
        super.onDestroyView()
    }
}