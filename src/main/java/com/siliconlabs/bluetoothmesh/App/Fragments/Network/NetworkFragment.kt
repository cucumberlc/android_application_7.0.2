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
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.ScannerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.ScannerViewModel
import com.siliconlabs.bluetoothmesh.App.Fragments.Subnet.SubnetFragment
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
class NetworkFragment : Fragment(R.layout.fragment_network),
    SwipeBaseAdapter.ItemListener<Subnet>,
    NetworkView {

    private val layout by viewBinding(FragmentNetworkBinding::bind)
    private val networkPresenter: NetworkPresenter by presenters()
    private val viewModel: ScannerViewModel by viewModels()

    private var loadingDialog: AlertDialog? = null
    private lateinit var loadingDialogLayout: DialogLoadingBinding

    private var subnetsAdapter: SubnetsAdapter? = null
    private var scanNonNLCMenu: MenuItem? = null

    private val nonNLCMenuProvider = object : MenuProvider {
        override fun onCreateMenu(nonNLCMenu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_scan_screen_toolbar, nonNLCMenu)
            scanNonNLCMenu = nonNLCMenu.findItem(R.id.scan_menu)
            scanNonNLCMenu?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.scan_menu) {
                requireMainActivity().showFragment(ScannerFragment())
                return true
            }
            return false
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            setCurrentMenuState(viewModel.scanState.value)
        }
    }

    private fun setCurrentMenuState(scannerState: DeviceScanner.ScannerState) {
        scanNonNLCMenu?.setTitle(
            when (scannerState) {
                DeviceScanner.ScannerState.SCANNING -> R.string.device_scanner_turn_off_scan
                else -> R.string.device_scanner_turn_on_scan
            }
        )?.isEnabled = scannerState !is DeviceScanner.ScannerState.InvalidState
    }

    private fun setUpNonNLCScanMenu() {
        requireActivity().addMenuProvider(
            nonNLCMenuProvider,
            viewLifecycleOwner, Lifecycle.State.RESUMED
        )
        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.scanState.collect(::setCurrentMenuState)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceFunctionalityDb.saveTab(false)
        setUpSubnetsList()
        setupAddSubnetButton()
        setUpNonNLCScanMenu()
    }

    private fun setUpSubnetsList() {
        subnetsAdapter = SubnetsAdapter(this)
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
        showDeleteSubnetDialog(item)
    }

    private fun showDeleteSubnetDialog(subnet: Subnet) {
        activity?.runOnUiThread {
            val builder = AlertDialog.Builder(requireContext())
            builder.apply {
                setTitle("Delete subnet${subnet.netKey.index}?")
                setMessage(getString(R.string.subnet_dialog_delete_message))
                setPositiveButton(getString(R.string.dialog_positive_ok)) { dialog, _ ->
                    networkPresenter.deleteSubnet(subnet)
                    dialog.dismiss()
                }
                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
            }

            val dialog = builder.create()
            dialog.apply {
                show()
            }
        }
    }

    override fun showDeleteSubnetLocallyDialog(subnet: Subnet, failedNodes: List<Node>) {
        activity?.runOnUiThread {
            AlertDialog.Builder(requireContext()).apply {
                setTitle(R.string.subnet_dialog_delete_locally_title)
                val failedNodesNames = failedNodes.joinToString { it.name }
                setMessage(
                    this@NetworkFragment.getString(
                        R.string.subnet_dialog_delete_locally_message,
                        "Failed nodes:\n$failedNodesNames",
                        subnet.netKey.index.toString()
                    )
                )
                setPositiveButton(R.string.dialog_positive_delete) { dialog, _ ->
                    networkPresenter.deleteSubnetLocally(subnet, failedNodes)
                    dialog.dismiss()
                }
                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                create().show()
            }
        }
    }

    override fun showDeleteSubnetLocallyDialog(subnet: Subnet, connectionError: ConnectionError) {
        activity?.runOnUiThread {
            AlertDialog.Builder(requireContext()).apply {
                setTitle(R.string.subnet_dialog_delete_locally_title)
                setMessage(
                    this@NetworkFragment.getString(
                        R.string.subnet_dialog_delete_locally_message,
                        "$connectionError.",
                        subnet.netKey.index.toString()
                    )
                )
                setPositiveButton(R.string.dialog_positive_delete) { dialog, _ ->
                    networkPresenter.deleteSubnetLocally(subnet)
                    dialog.dismiss()
                }
                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                create().show()
            }
        }
    }

    override fun showToast(message: String) {
        activity?.runOnUiThread {
            MeshToast.show(requireContext(), message)
        }
    }

    override fun setSubnetsList(subnets: Set<Subnet>) {
        activity?.runOnUiThread {
            // Exclude item at position 1 from the set
            val modifiedSubnets = subnets.toMutableSet()
            modifiedSubnets.elementAtOrNull(0)?.let {
                modifiedSubnets.remove(it)
            }
            // Set the modified subnets to the adapter
            subnetsAdapter?.setItems(modifiedSubnets)
            // Notify the adapter that the data set has changed
            subnetsAdapter?.notifyDataSetChanged()
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
                setPositiveButton(this@NetworkFragment.getString(R.string.dialog_positive_ok)) { _, _ ->
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
        val fragment = SubnetFragment.newInstance(subnet)
        requireMainActivity().showFragment(fragment)
    }

    override fun onDestroy() {
        super.onDestroy()
        scanNonNLCMenu = null
    }
}
