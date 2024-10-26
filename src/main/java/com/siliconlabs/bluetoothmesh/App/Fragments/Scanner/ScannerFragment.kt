/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scanner

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.SimpleItemAnimator
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Dialogs.DisconnectionDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.Adapters.ScannedDevicesAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ordinary.ProvisioningFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.DeviceScanner.ScannerState
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.collectMessages
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.launchAndRepeatWhenResumed
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.showErrorDialog
import com.siliconlabs.bluetoothmesh.App.Views.showOnce
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentScannerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.*

@AndroidEntryPoint
class ScannerFragment
@Deprecated("use newDirectInstance() or newRemoteInstance(Node, Subnet)")
constructor() : Fragment(R.layout.fragment_scanner) {
    companion object {
        fun newDirectInstance() =
            @Suppress("DEPRECATION")
            ScannerFragment()

        fun newRemoteInstance(remoteProvisioner: Node, subnet: Subnet) =
            @Suppress("DEPRECATION")
            ScannerFragment().withMeshNavArg(remoteProvisioner.toNavArg(subnet))
    }

    private val layout by viewBinding(FragmentScannerBinding::bind)

    private val viewModel: ScannerViewModel by viewModels()

    private val adapter = ScannedDevicesAdapter()
    private var scanMenu: MenuItem? = null

    private val disconnectionDialog by lazy {
        DisconnectionDialog { requireActivity().supportFragmentManager.popBackStack() }
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_scan_screen_toolbar, menu)
            scanMenu = menu.findItem(R.id.scan_menu)
            scanMenu?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.scan_menu) {
                if (!viewModel.isScanning()) viewModel.startScan()
                else viewModel.stopScan()
                return true
            }
            return false
        }

        override fun onPrepareMenu(menu: Menu) {
            setCurrentMenuState(viewModel.scanState.value)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        clearPreviousProvisionableDevices()
        collectMessages(viewModel, getString(R.string.error_message_title_scan_interrupted))
        setUpActionBarTitle()
        setUpScanMenu()
        setUpRecyclerView()
        setUpNoDevicesDisplay()
        setUpInvalidStateDialog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanMenu = null
    }

    private fun clearPreviousProvisionableDevices() {
        AppState.deviceToProvision = null
    }

    private fun setUpActionBarTitle() {
        if (viewModel.isRemoteScan) {
            requireMainActivity().setActionBar(getString(R.string.remote_scanning_appbar_title))
        }
    }

    private fun setUpScanMenu() {
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.scanState.collect(::setCurrentMenuState)
        }
    }

    private fun setCurrentMenuState(scannerState: ScannerState) {
        scanMenu?.setTitle(
            when (scannerState) {
                ScannerState.SCANNING -> R.string.device_scanner_turn_off_scan
                else -> R.string.device_scanner_turn_on_scan
            }
        )?.isEnabled = scannerState !is ScannerState.InvalidState
    }

    private fun setUpRecyclerView() {
        layout.recyclerViewScannedDevices.apply {
            adapter = this@ScannerFragment.adapter
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        }
        adapter.onScannedDeviceClick = ::onScannedDeviceClick

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.scanResults
                .onCompletion { adapter.submitList(null) }
                .collectLatest {
                    adapter.submitList(it)
                }
        }
    }

    private fun setUpNoDevicesDisplay() {
        layout.placeholder.isVisible = true

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            combine(viewModel.scanState, viewModel.scanResults) { state, results ->
                val showEmptyView = if (results.isEmpty()) {
                    layout.tvEmptyScanActionHint.setText(
                        when (state) {
                            ScannerState.SCANNING -> R.string.scanner_adapter_empty_list_message_scanning
                            else -> R.string.scanner_adapter_empty_list_message_idle
                        }
                    )
                    true
                } else false
                layout.placeholder.isVisible = showEmptyView
            }.onCompletion {
                layout.apply {
                    tvEmptyScanActionHint.setText(R.string.scanner_adapter_empty_list_message_idle)
                    placeholder.isVisible = true
                }
            }.collect()
        }
    }

    private fun setUpInvalidStateDialog() {
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            var isScanning = false
            viewModel.scanState.collect { state ->
                when (state) {
                    // maybe update this dialog and add reconnection button/hints?
                    // remote scanner does recover and work in that case
                    ScannerState.NO_BLUETOOTH, ScannerState.NO_NETWORK -> {
                        if (isScanning || viewModel.isRemoteScan)
                            disconnectionDialog.showOnce(childFragmentManager)
                    }
                    else -> if (disconnectionDialog.isAdded) disconnectionDialog.dismiss()
                }
                isScanning = state == ScannerState.SCANNING
            }
        }
    }

    private fun onScannedDeviceClick(unprovisionedDevice: UnprovisionedDevice) {
        if (viewModel.defaultSubnet() == null) {
            // cannot provision device into nothing
            showErrorDialog(Message.error(R.string.scanner_adapter_no_subnet_on_provisioning))
            return
        }
        navigateToDeviceProvisioning(viewModel.selectDevice(unprovisionedDevice))
    }

    private fun navigateToDeviceProvisioning(deviceToProvision: DeviceToProvision) {
        // app state still exists here because deviceToProvision cannot be parcelized
        if(viewModel.subnetList().size > 1) {
            AppState.deviceToProvision = deviceToProvision
            requireMainActivity().showFragment(ProvisioningFragment())
        } else {
            showErrorDialog(Message.error(R.string.scanner_adapter_no_subnet_on_provisioning))
            return
        }
    }
}
