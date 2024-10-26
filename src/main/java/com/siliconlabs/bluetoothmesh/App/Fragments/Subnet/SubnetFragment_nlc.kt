/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Subnet

import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.AppKeysList.AppKeysListFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.DeviceListFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentSubnetBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SubnetFragment_nlc
@Deprecated(
    "use newInstance(Subnet)",
    replaceWith = ReplaceWith("SubnetFragment_nlc.newInstance(subnet)")
)
constructor() : Fragment(R.layout.fragment_subnet_nlc), SubnetView, MenuProvider {
    companion object {
        fun newInstance(subnet: Subnet) =
            @Suppress("DEPRECATION")
            SubnetFragment_nlc().withMeshNavArg(subnet.toNavArg())
    }

    private val layout by viewBinding(FragmentSubnetBinding::bind)
    private val subnetPresenter: SubnetPresenter by presenters()

    private var meshStatusBtn: ImageView? = null
    private var meshIconStatus = SubnetView.MeshIconState.DISCONNECTED

    private val rotate by lazy {
        AnimationUtils.loadAnimation(context, R.anim.rotate)
    }

    override fun onPause() {
        super.onPause()
        meshStatusBtn?.clearAnimation()
    }

    override fun onResume() {
        super.onResume()
        DeviceFunctionalityDb.saveTab(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        super.onViewCreated(view, savedInstanceState)

        layout.viewPager.adapter = SubnetPageAdapter()

        TabLayoutMediator(layout.tabLayout, layout.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.page_title_devices)
                else -> getString(R.string.page_title_appkeys)
            }
        }.attach()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_groups_toolbar, menu)

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

    // View

    override fun setActionBarTitle(title: String) {
        requireMainActivity().setActionBar(title)
    }

    override fun setMeshIconState(iconState: SubnetView.MeshIconState) {
        activity?.runOnUiThread {
            meshIconStatus = iconState

            meshStatusBtn?.apply {
                when (iconState) {
                    SubnetView.MeshIconState.DISCONNECTED -> {
                        setImageResource(R.drawable.ic_mesh_red)
                        clearAnimation()
                    }

                    SubnetView.MeshIconState.CONNECTING -> {
                        setImageResource(R.drawable.ic_mesh_yellow)
                        startAnimation(rotate)
                    }

                    SubnetView.MeshIconState.CONNECTED -> {
                        setImageResource(R.drawable.ic_mesh_green)
                        clearAnimation()
                    }
                }

                setOnClickListener {
                    subnetPresenter.meshIconClicked(iconState)
                }
            }
        }
    }

    override fun showErrorToast(connectionError: ConnectionError) {
        lifecycleScope.launch {
            MeshToast.show(requireContext(), connectionError.toString())
        }
    }

    private inner class SubnetPageAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = 1

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> DeviceListFragment.newInstance(subnetPresenter.subnet, true)
                else -> AppKeysListFragment.newInstance(subnetPresenter.subnet, true)
            }
        }
    }
}
