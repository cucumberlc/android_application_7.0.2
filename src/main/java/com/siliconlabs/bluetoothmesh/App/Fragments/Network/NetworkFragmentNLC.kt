/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Network

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AbsListView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import by.kirich1409.viewbindingdelegate.viewBinding
import com.daimajia.swipe.util.Attributes
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.Activities.Main.MainActivity
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.DeviceScanner
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.Scan_nlc
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.ScannerViewModel
import com.siliconlabs.bluetoothmesh.App.Fragments.Subnet.SubnetFragment_nlc
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.launchAndRepeatWhenResumed
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.SwipeBaseAdapter
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NetworkFragmentNLC : Fragment(R.layout.fragment_network_nlc),
    SwipeBaseAdapter.ItemListener<Subnet>,
    NetworkView {

    private val layout by viewBinding(FragmentNetworkNlcBinding::bind)
    private val networkPresenter: NetworkPresenter by presenters()
    private val viewModel: ScannerViewModel by viewModels()

    private var loadingDialog: AlertDialog? = null
    private lateinit var loadingDialogLayout: DialogLoadingBinding

    private var subnetsAdapter: SubnetsAdapter_nlc? = null
    private var scanMenu: MenuItem? = null

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_scan_screen_toolbar, menu)
            scanMenu = menu.findItem(R.id.scan_menu)
            scanMenu?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        override fun onMenuItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.scan_menu) {
                requireMainActivity().showFragment(Scan_nlc())
                return true
            }
            return false
        }

        override fun onPrepareMenu(menu: Menu) {
              setCurrentMenuState(viewModel.scanState.value)
        }
    }

    private fun setCurrentMenuState(scannerState: DeviceScanner.ScannerState) {
        scanMenu?.setTitle(
            when (scannerState) {
                DeviceScanner.ScannerState.SCANNING -> R.string.device_scanner_turn_off_scan
                else -> R.string.device_scanner_turn_on_scan
            }
        )?.isEnabled = scannerState !is DeviceScanner.ScannerState.InvalidState
    }

    private fun setUpScanMenu() {
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.scanState.collect(::setCurrentMenuState)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanMenu = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceFunctionalityDb.saveTab(true)
        setUpSubnetsList()
        setupAddSubnetButton()
        setUpScanMenu()
    }

    private fun setUpSubnetsList() {
        subnetsAdapter = SubnetsAdapter_nlc(this)
        subnetsAdapter?.mode = Attributes.Mode.Single
        layout.apply {
            listViewSubnets.adapter = subnetsAdapter
            listViewSubnets.emptyView = placeholder

            listViewSubnets.setOnScrollListener(object : AbsListView.OnScrollListener {
                private var lastFirstVisibleItem: Int = 0

                override fun onScroll(
                    view: AbsListView?,
                    firstVisibleItem: Int,
                    visibleItemCount: Int,
                    totalItemCount: Int,
                ) {
                    if (lastFirstVisibleItem < firstVisibleItem) {
                        fabAddSubnet.hide()
                    } else if (lastFirstVisibleItem > firstVisibleItem) {
                        fabAddSubnet.show()
                    }

                    lastFirstVisibleItem = firstVisibleItem
                }

                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                }
            })
        }
    }

    private fun setupAddSubnetButton() {
        layout.fabAddSubnet.setOnClickListener {
            networkPresenter.addSubnet()
        }
    }

    override fun onPause() {
        super.onPause()
        subnetsAdapter?.closeAllItems()
    }

    override fun onDeleteClick(item: Subnet) {
        // showDeleteSubnetDialog(item)
    }

    //    private fun showDeleteSubnetDialog(subnet: Subnet) {
    //        activity?.runOnUiThread {
    //            val builder = AlertDialog.Builder(requireContext())
    //            builder.apply {
    //                setTitle("Delete subnet${subnet.netKey.index}?")
    //                setMessage(getString(R.string.subnet_dialog_delete_message))
    //                setPositiveButton(getString(R.string.dialog_positive_ok)) { dialog, _ ->
    //                    networkPresenter.deleteSubnet(subnet)
    //                    dialog.dismiss()
    //                }
    //                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
    //                    dialog.dismiss()
    //                }
    //            }
    //
    //            val dialog = builder.create()
    //            dialog.apply {
    //                show()
    //            }
    //        }
    //    }

    override fun showDeleteSubnetLocallyDialog(subnet: Subnet, failedNodes: List<Node>) {
        //        activity?.runOnUiThread {
        //            AlertDialog.Builder(requireContext()).apply {
        //                setTitle(R.string.subnet_dialog_delete_locally_title)
        //                val failedNodesNames  = failedNodes.joinToString { it.name }
        //                setMessage(
        //                    this@NetworkFragment_nlc.getString(
        //                        R.string.subnet_dialog_delete_locally_message,
        //                        "Failed nodes:\n$failedNodesNames",
        //                        subnet.netKey.index.toString()
        //                    )
        //                )
        //                setPositiveButton(R.string.dialog_positive_delete) { dialog, _ ->
        //                    networkPresenter.deleteSubnetLocally(subnet, failedNodes)
        //                    dialog.dismiss()
        //                }
        //                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
        //                    dialog.dismiss()
        //                }
        //                create().show()
        //            }
        //        }
    }

    override fun showDeleteSubnetLocallyDialog(subnet: Subnet, connectionError: ConnectionError) {
        //        activity?.runOnUiThread {
        //            AlertDialog.Builder(requireContext()).apply {
        //                setTitle(R.string.subnet_dialog_delete_locally_title)
        //                setMessage(
        //                    this@NetworkFragment_nlc.getString(
        //                        R.string.subnet_dialog_delete_locally_message,
        //                        "$connectionError.",
        //                        subnet.netKey.index.toString()
        //                    )
        //                )
        //                setPositiveButton(R.string.dialog_positive_delete) { dialog, _ ->
        //                    networkPresenter.deleteSubnetLocally(subnet)
        //                    dialog.dismiss()
        //                }
        //                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
        //                    dialog.dismiss()
        //                }
        //                create().show()
        //            }
        //        }
    }

    override fun showToast(message: String) {
        activity?.runOnUiThread {
            MeshToast.show(requireContext(), message)
        }
    }

    //    override fun setSubnetsList(subnets: Set<Subnet>) {
    //        activity?.runOnUiThread {
    //            subnetsAdapter?.setItems(subnets)
    //            subnetsAdapter?.notifyDataSetChanged()
    //            (activity as? MainActivity)?.invalidateSubnetConnection()
    //        }
    //    }

    override fun setSubnetsList(subnets: Set<Subnet>) {
        activity?.runOnUiThread {
            // Get the item at position 1 or null if not present
            val subnetAtPosition1 = subnets.elementAtOrNull(0)

            // Create a new set with only the item at position 1 (if present)
            val subsetForAdapter =
                if (subnetAtPosition1 != null) setOf(subnetAtPosition1) else emptySet()
//            val nodeCount = subnetAtPosition1!!.nodes.size
//            if (nodeCount > 0) {
                subnetsAdapter?.setItems(subsetForAdapter)
                // Notify the adapter that the data set has changed
                subnetsAdapter?.notifyDataSetChanged()
//            }
            // Set the subset to the adapter
            // Optionally, you can perform other actions here based on the subset

            // Invalidate subnet connection in the MainActivity
            (activity as? MainActivity)?.invalidateSubnetConnection()
        }
    }

    override fun showLoadingDialog() {
        activity?.runOnUiThread {
            loadingDialogLayout = DialogLoadingBinding.inflate(layoutInflater, null, false)
            val builder = CustomAlertDialogBuilder(requireContext())
            builder.apply {
                setView(loadingDialogLayout.root)
                setCancelable(false)
                setPositiveButton(this@NetworkFragmentNLC.getString(R.string.dialog_positive_ok)) { _, _ ->
                }
            }

            loadingDialog = builder.create()
            loadingDialog?.apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
            }
        }
    }

    override fun updateLoadingDialogMessage(
        loadingMessage: NetworkView.LoadingDialogMessage,
        message: String,
        showCloseButton: Boolean,
    ) {
        activity?.runOnUiThread {
            loadingDialog?.apply {
                if (!isShowing) {
                    return@runOnUiThread
                }

                loadingDialogLayout.apply {
                    loadingText.text = when (loadingMessage) {
                        NetworkView.LoadingDialogMessage.REMOVING_SUBNET -> context.getString(
                            R.string.subnet_dialog_loading_text_removing_subnet
                        ).format(message)

                        NetworkView.LoadingDialogMessage.CONNECTING_TO_SUBNET -> context.getString(
                            R.string.subnet_dialog_loading_text_connecting_to_subnet
                        ).format(message)
                    }
                }
                if (showCloseButton) {
                    loadingDialogLayout.loadingIcon.visibility = View.GONE
                    getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.VISIBLE
                }
            }
        }
    }

    override fun dismissLoadingDialog() {
        activity?.runOnUiThread {
            loadingDialog?.dismiss()
            loadingDialog = null
        }
    }

    override fun onItemClick(item: Subnet) {
        showSubnetFragment(item)
    }

    private fun showSubnetFragment(subnet: Subnet) {
        val fragment = SubnetFragment_nlc.newInstance(subnet)
        requireMainActivity().showFragment(fragment)
    }
}
