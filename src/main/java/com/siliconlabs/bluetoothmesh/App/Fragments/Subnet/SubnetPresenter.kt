/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Subnet

import com.jcabi.aspects.Loggable
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionListener
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SubnetPresenter @Inject constructor(
        val subnet: Subnet,
        private val networkConnectionLogic: NetworkConnectionLogic,
) : BasePresenter<SubnetView>(), NetworkConnectionListener {
    private val subnetView
        get() = view

    private var connectToSubnet = true

    @Loggable
    override fun onResume() {
        subnetView?.setActionBarTitle("Subnet ${subnet.netKey.index}")
        networkConnectionLogic.addListener(this)

        if (connectToSubnet) {
            networkConnectionLogic.connect(subnet)
            connectToSubnet = false
        }
    }

    @Loggable
    override fun onPause() {
        networkConnectionLogic.removeListener(this)
    }

    fun meshIconClicked(iconState: SubnetView.MeshIconState) {
        when (iconState) {
            SubnetView.MeshIconState.DISCONNECTED -> {
                networkConnectionLogic.connect(subnet)
            }
            SubnetView.MeshIconState.CONNECTING -> {
                networkConnectionLogic.disconnect()
            }
            SubnetView.MeshIconState.CONNECTED -> {
                networkConnectionLogic.disconnect()
            }
        }
    }

    // NetworkConnectionListener

    override fun connecting() {
        subnetView?.setMeshIconState(SubnetView.MeshIconState.CONNECTING)
    }

    override fun connected() {
        subnetView?.setMeshIconState(SubnetView.MeshIconState.CONNECTED)
    }

    override fun disconnected() {
        subnetView?.setMeshIconState(SubnetView.MeshIconState.DISCONNECTED)
    }

    override fun connectionErrorMessage(error: ConnectionError) {
        subnetView?.showErrorToast(error)
    }
}