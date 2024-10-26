/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.MainFragment

import androidx.lifecycle.ViewModel
import com.siliconlab.bluetoothmesh.adk.data_model.network.Network
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ExportDataProvider
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothState
import com.siliconlabs.bluetoothmesh.App.Logic.LocationState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject

@HiltViewModel
class MainFragmentViewModel @Inject constructor() : ViewModel() {
    val isBluetoothEnabled
        get() = BluetoothState.isEnabled

    val isLocationEnabled
        get() = LocationState.isEnabled

    val exportDataProvider = object : ExportDataProvider {
        override val data: InputStream get() = KeyExport.export().byteInputStream()
        override val mimeType: String get() = "application/json"
        override val defaultName: String get() = "bluetooth-mesh-keys"
    }
}