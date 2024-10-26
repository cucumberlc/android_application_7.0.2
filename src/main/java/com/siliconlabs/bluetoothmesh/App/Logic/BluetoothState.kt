/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

object BluetoothState {
    @Suppress("ObjectPropertyName")
    private val _isEnabled = MutableStateFlow(isBluetoothEnabled())
    val isEnabled = _isEnabled.asStateFlow()

    init {
        MeshApplication.mainScope.launch {
            _isEnabled.hasActiveSubscriptionsFlow().collect(::registerReceiver)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                when (intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.ERROR, BluetoothAdapter.STATE_TURNING_OFF -> false
                    BluetoothAdapter.STATE_ON -> true
                    else -> null
                }?.let { isEnabled ->
                    Logger.debug { "bluetooth state changed to $isEnabled for ${_isEnabled.subscriptionCount.value} subscribers" }
                    _isEnabled.value = isEnabled
                }
            }
        }
    }

    private fun registerReceiver(register: Boolean) {
        if (register) {
            _isEnabled.value = isBluetoothEnabled()
            val bluetoothIntentFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            MeshApplication.appContext.registerReceiver(receiver, bluetoothIntentFilter)
        } else {
            MeshApplication.appContext.unregisterReceiver(receiver)
        }
    }

    private fun isBluetoothEnabled() = MeshApplication.appContext
        .getSystemService(BluetoothManager::class.java)
        ?.adapter?.isEnabled == true
}