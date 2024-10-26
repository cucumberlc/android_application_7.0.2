/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Activities.Main

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcabi.aspects.Loggable
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    val networkConnectionLogic: NetworkConnectionLogic,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    companion object {
        private const val KEY_SUBNET = "MainActivityViewModel.KEY_SUBNET"
    }

    private val _currentSubnet = MutableStateFlow(restoreSavedSubnet())
    val currentSubnet = _currentSubnet.asStateFlow()

    init {
        viewModelScope.launch {
            _currentSubnet.hasActiveSubscriptionsFlow()
                .onCompletion { networkConnectionLogic.disconnect() }
                .collectLatest { hasSubscribers ->
                    if (hasSubscribers) invalidateSubnet()
                }
        }
    }

    @Loggable
    fun setCurrentSubnet(subnet: Subnet?) {
        if (subnet == currentSubnet.value) return
        _currentSubnet.value = subnet
        saveSubnet(subnet)
        if (subnet != null) {
            connectTo(subnet)
        } else {
            networkConnectionLogic.disconnect()
        }
    }

    // clear and disconnect current subnet if it doesn't exist
    fun invalidateSubnet() {
        currentSubnet.value?.let {
            if(!subnetIsValid(it)){
                setCurrentSubnet(null)
                Logger.debug { "Disconnecting from subnet ($it) because it doesn't exist in network" }
            }
        }
    }

    private fun connectTo(subnet: Subnet) {
        if(subnetIsValid(subnet)){
            if (!networkConnectionLogic.isConnectedTo(subnet)) {
                networkConnectionLogic.connect(subnet)
            }
        } else {
            setCurrentSubnet(null)
            Logger.error { "Leaked subnet ($subnet): it doesn't exist in network" }
        }
    }

    private fun subnetIsValid(subnet: Subnet) = BluetoothMesh.network.subnets.contains(subnet)

    // have to store currently set subnet
    private fun restoreSavedSubnet(): Subnet? {
        return savedStateHandle.run {
            get<Int>(KEY_SUBNET)?.let { subnetKeyIndex ->
                BluetoothMesh.network.subnets.find { it.netKey.index == subnetKeyIndex }
            }
        }
    }

    private fun saveSubnet(subnet: Subnet?) {
        return savedStateHandle.run {
            if (subnet != null) {
                set(KEY_SUBNET, subnet.netKey.index)
            } else {
                remove<Subnet>(KEY_SUBNET)
            }
        }
    }
}