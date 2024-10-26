/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ControlGroup

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.descendants
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import com.daimajia.swipe.util.Attributes
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.errors.MeshError
import com.siliconlabs.bluetoothmesh.App.Activities.Main.MainActivity
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlabs.bluetoothmesh.App.Dialogs.DeleteDeviceDialog
import com.siliconlabs.bluetoothmesh.App.Dialogs.DeleteDeviceLocallyDialog
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigFragment.Companion.KEY_IS_NLC_CONTROL
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.DeviceListAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ControlGroupBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ControlGroupFragment
@Deprecated(
    "use newInstance(appKey)",
    replaceWith = ReplaceWith("ControlGroupFragment.newInstance(appKey)")
) constructor() : Fragment(R.layout.control_group), ControlGroupView, MenuProvider {
    companion object {
        fun newInstance(appKey: AppKey, isNLC: Boolean) =
            @Suppress("DEPRECATION")
            ControlGroupFragment().withMeshNavArg(appKey.toNavArg()).apply {
                arguments!!.putBoolean(KEY_IS_NLC_CONTROL, isNLC)
            }
    }

    private lateinit var currentNode: Node
    private var isConfigurationOpen = false

    private val controlGroupPresenter: ControlGroupPresenter by presenters()

    private var deviceListAdapter: DeviceListAdapter? = null

    private val layout by viewBinding(ControlGroupBinding::bind)

    private var meshStatusBtn: ImageView? = null
    private var meshIconStatus = ControlGroupView.MeshIconState.DISCONNECTED

    private val deleteDeviceDialog = DeleteDeviceDialog {
        controlGroupPresenter.deleteDevice(currentNode)
    }
    private val deleteDeviceLocallyDialog = DeleteDeviceLocallyDialog {
        controlGroupPresenter.deleteDeviceLocally(currentNode)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        super.onViewCreated(view, savedInstanceState)

        layout.apply {
            ivSwitch.setOnClickListener {
                controlGroupPresenter.onMasterSwitchChanged()
            }

            setupSeekBar()
            setupDeviceList()
            showEmptyView()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_control_group_toolbar, menu)

        val menuIcon = menu.findItem(R.id.proxy_menu)

        meshStatusBtn?.clearAnimation()
        meshStatusBtn?.visibility = View.INVISIBLE
        meshStatusBtn?.setOnClickListener(null)

        meshStatusBtn = menuIcon?.actionView as ImageView

        setMeshIconState(meshIconStatus)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.proxy_menu -> true
            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        requireMainActivity().setActionBar("AppKey " + controlGroupPresenter.appKey.index.toString())
    }

    override fun onPause() {
        super.onPause()
        isConfigurationOpen = false
        meshStatusBtn?.clearAnimation()
        deviceListAdapter?.closeAllItems()
    }

    private fun setupSeekBar() {
        layout.apply {
            sbLightControl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int,
                    fromUser: Boolean,
                ) {
                    tvLightValue.text = getString(
                        R.string.device_adapter_lightness_value,
                        seekBar?.progress
                    )

                    val switchEnabled = seekBar?.let {
                        it.progress > 0
                    } ?: false

                    if (switchEnabled) {
                        ivSwitch.setImageResource(R.drawable.toggle_on)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.let {
                        controlGroupPresenter.onMasterLevelChanged(it.progress)
                    }
                }
            })
        }
    }

    private fun setupDeviceList() {
        deviceListAdapter = DeviceListAdapter(
            requireContext(), controlGroupPresenter,
            controlGroupPresenter.networkConnectionLogic,
            viewLifecycleScope,
        )
        deviceListAdapter?.mode = Attributes.Mode.Single
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

    private fun showEmptyView() {
        layout.apply {
            if (deviceListAdapter?.itemCount?.let { it > 0 } == true) {
                devicesList.visibility = View.VISIBLE
                placeholder.visibility = View.GONE
            } else {
                devicesList.visibility = View.GONE
                placeholder.visibility = View.VISIBLE
            }
        }
    }

    private fun disableEnableViews(rootView: ViewGroup) {
        for (descendant in rootView.descendants)
            descendant.isEnabled = !descendant.isEnabled

        layout.devicesList.isEnabled = true
    }

    override fun setMeshIconState(iconState: ControlGroupView.MeshIconState) {
        meshIconStatus = iconState

        meshStatusBtn?.apply {
            when (iconState) {
                ControlGroupView.MeshIconState.DISCONNECTED -> {
                    setImageResource(R.drawable.ic_mesh_red)
                    clearAnimation()
                }

                ControlGroupView.MeshIconState.CONNECTING -> {
                    setImageResource(R.drawable.ic_mesh_yellow)
                    startAnimation(AnimationUtils.loadAnimation(context, R.anim.rotate))
                }

                ControlGroupView.MeshIconState.CONNECTED -> {
                    setImageResource(R.drawable.ic_mesh_green)
                    clearAnimation()
                }
            }
            setOnClickListener {
                controlGroupPresenter.meshIconClicked(iconState)
            }
        }
    }

    override fun setMasterSwitch(isChecked: Boolean) {
        layout.apply {
            if (isChecked) {
                ivSwitch.setImageResource(R.drawable.toggle_on)
            } else {
                ivSwitch.setImageResource(R.drawable.toggle_off)
            }
        }
    }

    override fun showToast(message: String) {
        MeshToast.show(requireContext(), message)
    }

    override fun showToast(error: MeshError) {
        showToast(error.toString())
    }

    override fun setMasterLevel(progress: Int) {
        layout.apply {
            sbLightControl.progress = progress
            tvLightValue.text = requireContext().getString(R.string.device_adapter_lightness_value)
                .format(progress)
        }
    }

    override fun setMasterControlEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1f else 0.5f
        layout.apply {
            ivSwitch.isEnabled = enabled
            ivSwitch.alpha = alpha
            sbLightControl.isEnabled = enabled
            sbLightControl.alpha = alpha
        }
    }

    override fun setMasterControlVisibility(visibility: Int) {
        layout.llMasterControl.visibility = visibility
    }

    override fun refreshView() {
        deviceListAdapter?.notifyDataSetChanged()
    }

    override fun setDevicesList(devicesInfo: Set<MeshNode>) {
        deviceListAdapter?.setItems(devicesInfo)
        deviceListAdapter?.notifyDataSetChanged()
        showEmptyView()
    }

    override fun showFragment(fragment: Fragment) {
        requireMainActivity().showFragment(fragment)
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
            (requireContext().getString(
                R.string.device_dialog_delete_locally_message,
                description,
                node.name
            ))
        )
    }

    override fun showProgressBar() {
        disableEnableViews(view?.rootView as ViewGroup)
        layout.progressIndicator.isVisible = true
    }

    override fun hideProgressBar() {
        disableEnableViews(view?.rootView as ViewGroup)
        layout.progressIndicator.isInvisible = true
    }

    override fun showDeviceConfiguration(meshNode: MeshNode) {
        deviceListAdapter?.closeAllItems()
        if (!isConfigurationOpen) {
            val deviceFragment =
                DeviceFragment.newInstance(meshNode, controlGroupPresenter.subnet, true, false)
            requireMainActivity().showFragment(deviceFragment)
        }
        isConfigurationOpen = true
    }

    override fun navigateToDistributionFragment(distributor: Node) {
        requireMainActivity().navigateToDistributionFragment(distributor)
    }
}