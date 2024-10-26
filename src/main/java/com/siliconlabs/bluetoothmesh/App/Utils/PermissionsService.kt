/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import android.Manifest
import android.os.Build

object PermissionsService {

    val requiredPermissions = listOf(
            prepareCommonPermissions(),
            prepareBluetoothPermissions(),
            prepareExternalStoragePermissions()
    ).flatten()

    private fun prepareCommonPermissions() = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
    )

    private fun prepareBluetoothPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN)
        } else emptyList()
    }

    private fun prepareExternalStoragePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        else emptyList()
    }
}