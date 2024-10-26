/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic

import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError

interface NetworkConnectionListener {
    fun onNetworkConnectionStateChanged(state: ConnectionState) {
        when (state) {
            ConnectionState.DISCONNECTED -> disconnected()
            ConnectionState.CONNECTING -> connecting()
            ConnectionState.CONNECTED -> connected()
        }
    }

    fun connecting() {}

    fun connected() {}

    fun disconnected() {}

    fun connectionErrorMessage(error: ConnectionError) {}
}