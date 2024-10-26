/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

object LocationState {
    @Suppress("ObjectPropertyName")
    private val _isEnabled = MutableStateFlow(isLocationEnabled())
    val isEnabled = _isEnabled.asStateFlow()

    init {
        MeshApplication.mainScope.launch {
            _isEnabled.hasActiveSubscriptionsFlow().collect(::registerReceiver)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                val enabled = isLocationEnabled()
                Logger.debug { "location state changed to $enabled for ${_isEnabled.subscriptionCount.value} subscribers" }
                _isEnabled.value = enabled
            }
        }
    }

    private fun registerReceiver(register: Boolean) {
        if (register) {
            _isEnabled.value = isLocationEnabled()
            val locationIntentFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
            MeshApplication.appContext.registerReceiver(receiver, locationIntentFilter)
        } else {
            MeshApplication.appContext.unregisterReceiver(receiver)
        }
    }

    private fun isLocationEnabled() = MeshApplication.appContext
        .getSystemService(LocationManager::class.java)
        ?.run {
            isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                    isProviderEnabled(LocationManager.GPS_PROVIDER)
        } == true
}