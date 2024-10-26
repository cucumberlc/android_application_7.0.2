/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.descendants
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Dialogs.DeleteDeviceDialog
import com.siliconlabs.bluetoothmesh.App.Dialogs.DeleteDeviceLocallyDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigFragment.Companion.KEY_IS_NLC_CONTROL
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesScreenBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeviceListFragment
@Deprecated(
    "use newInstance(subnet)",
    replaceWith = ReplaceWith("DeviceListFragment.newInstance(subnet)")
) constructor() : Fragment(R.layout.devices_screen), DeviceListView {
    companion object {
        fun newInstance(subnet: Subnet, isNLC: Boolean) =
            @Suppress("DEPRECATION")
            DeviceListFragment().withMeshNavArg(subnet.toNavArg()).apply {
                arguments!!.putBoolean(KEY_IS_NLC_CONTROL, isNLC)
            }
    }

    private lateinit var currentNode: Node
    private var isConfigurationOpen = false

    private val deviceListPresenter: DeviceListPresenter by presenters()

    private val deleteDeviceDialog = DeleteDeviceDialog {
        deviceListPresenter.deleteDevice(currentNode)
    }
    private val deleteDeviceLocallyDialog = DeleteDeviceLocallyDialog {
        deviceListPresenter.deleteDeviceLocally(currentNode)
    }

    private var deviceListAdapter: DeviceListAdapter? = null

    private val layout by viewBinding(DevicesScreenBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceListAdapter = DeviceListAdapter(
            requireContext(),
            deviceListPresenter,
            deviceListPresenter.networkConnectionLogic,
            lifecycleScope,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        DeviceFunctionalityDb.saveTab(false)
        setupDeviceList()
        deviceListPresenter.refreshList()
    }

    private fun setupDeviceList() {
        layout.apply {
            devicesList.adapter = deviceListAdapter
            devicesList.itemAnimator = null

            val dividerItemDecoration = DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL
            )
            dividerItemDecoration.setDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.recycler_view_divider)!!
            )
            devicesList.addItemDecoration(dividerItemDecoration)
        }
    }

    override fun onPause() {
        super.onPause()
        isConfigurationOpen = false
        deviceListAdapter?.closeAllItems()
    }

    override fun onDestroyView() {
        layout.devicesList.adapter = null
        super.onDestroyView()
    }

    override fun setDevicesList(meshNodes: Set<MeshNode>) {
        activity?.runOnUiThread {
            deviceListAdapter?.setItems(meshNodes)
            deviceListAdapter?.notifyDataSetChanged()
            showEmptyView()
        }
    }

    private fun showEmptyView() {
        val areItemsVisible = deviceListAdapter?.itemCount?.let { it > 0 } == true
        layout.apply {
            devicesList.isVisible = areItemsVisible
            placeholder.isVisible = !areItemsVisible
        }
    }

    private fun enableDisableViews(rootView: ViewGroup) {
        for (descendant in rootView.descendants)
            descendant.isEnabled = !descendant.isEnabled
        layout.devicesList.isEnabled = true
    }

    override fun notifyDataSetChanged() {
        activity?.runOnUiThread {
            deviceListAdapter?.notifyDataSetChanged()
        }
    }

    override fun showDeleteDeviceDialog(node: Node) {
        currentNode = node
        deleteDeviceDialog.show(childFragmentManager, null)
        deviceListAdapter?.closeAllItems()
    }

    override fun showDeleteDeviceLocallyDialog(description: String, node: Node) {
        currentNode = node
        deleteDeviceLocallyDialog.show(childFragmentManager, null)
        deleteDeviceLocallyDialog.setText(
            getString(
                R.string.device_dialog_delete_locally_message,
                description,
                node.name
            )
        )
    }

    override fun showProgressBar() {
        enableDisableViews(view?.rootView as ViewGroup)
        layout.progressIndicator.isVisible = true
    }

    override fun hideProgressBar() {
        layout.progressIndicator.isInvisible = true
        enableDisableViews(view?.rootView as ViewGroup)
    }

    override fun showDeviceConfiguration(meshNode: MeshNode) {
        deviceListAdapter?.closeAllItems()
        if (!isConfigurationOpen) {
            val isNLC = requireArguments().getBoolean(KEY_IS_NLC_CONTROL)
            val deviceFragment =
                DeviceFragment.newInstance(meshNode, deviceListPresenter.subnet,true, isNLC)
            requireMainActivity().showFragment(deviceFragment)
        }
        isConfigurationOpen = true
    }

    override fun navigateToDistributionFragment(distributor: Node) {
        requireMainActivity().navigateToDistributionFragment(distributor)
    }

    override fun showErrorToast(errorType: NodeControlError) {
        activity?.let {
            this.showToast(errorType.toString())
        }
    }

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            MeshToast.show(requireContext(), message)
        }
    }

    override fun showFragment(fragment: Fragment) {
        requireMainActivity().showFragment(fragment)
    }
}
