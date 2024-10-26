/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.jcabi.aspects.Loggable
import com.siliconlabs.bluetoothmesh.App.Logic.Scanner.ScanLimitGuard
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.tinylog.Logger
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

@SuppressLint("MissingPermission")
object BluetoothScanner {
    private var leScanStarted: Boolean = false

    private val bluetoothAdapter by lazy { MeshApplication.appContext.bluetoothAdapter }
    private val scanner get() = bluetoothAdapter?.bluetoothLeScanner

    private val scanCallbacks = ConcurrentLinkedQueue<ScanCallback>()

    private val scanLimitGuard = ScanLimitGuard()

    init {
        MeshApplication.mainScope.launch {
            BluetoothState.isEnabled.collectLatest { isEnabled ->
                if(!isEnabled) {
                    Logger.debug { "Scan stopped because bluetooth was disabled" }
                    synchronized(BluetoothScanner) {
                        if (leScanStarted) {
                            leScanStarted = false
                        }
                    }
                } else {
                    if (leScanStarted) {
                        Logger.debug { "Unexpected: Bluetooth was enabled during the scan" }
                    }
                }
            }
        }
    }

    fun addScanCallback(scanCallback: ScanCallback) {
        scanCallbacks.add(scanCallback)
    }

    fun removeScanCallback(scanCallback: ScanCallback) {
        scanCallbacks.remove(scanCallback)
    }

    val resultsFlow = callbackFlow {
        val callback = object : ScanCallback(){
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                launch { send(result) }
            }
        }
        addScanCallback(callback)
        awaitClose{ removeScanCallback(callback) }
    }

    @Loggable
    fun startLeScan(serviceUUID: UUID? = null, mode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY): Boolean {
        synchronized(BluetoothScanner) {
            if (leScanStarted) {
                return true
            }
            if (bluetoothAdapter?.isEnabled == false) {
                return false
            }

            return scanLimitGuard.storeScanningTimestamp().fold(
                    onSuccess = {
                        return try {
                            scanner?.startScan(getFilters(serviceUUID), getSettings(mode), scanCallback)
                            leScanStarted = true
                            true
                        } catch (exception: SecurityException) {
                            Logger.error { exception.toString() }
                            false
                        }
                    },
                    onFailure = { cause ->
                        Logger.error { cause.toString() }
                        false
                    }
            )
        }
    }

    private fun getSettings(mode: Int): ScanSettings = ScanSettings.Builder()
            .setScanMode(mode)
            .build()

    private fun getFilters(serviceUUID: UUID?): List<ScanFilter> =
            serviceUUID?.let { listOf(getFilter(it)) } ?: listOf()

    private fun getFilter(serviceUUID: UUID): ScanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUUID))
            .build()

    suspend fun delayBeforeScanning() {
        val delayDuration = scanLimitGuard.getDelayDuration()
        Logger.debug { "delay $delayDuration before scanning" }
        delay(delayDuration)
    }

    @Loggable
    fun stopLeScan() {
        synchronized(BluetoothScanner) {
            if (!leScanStarted) {
                return
            }
            try {
                scanner?.stopScan(scanCallback)
                leScanStarted = false
            }
            catch (exception: SecurityException) {
                Logger.debug { exception.toString() }
            }
        }
    }

    fun isLeScanStarted(): Boolean {
        return leScanStarted
    }

    //

    // ScanCallback

    private val scanCallback = object : ScanCallback(){
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            synchronized(BluetoothScanner) {
                if (!leScanStarted) {
                    return
                }
            }
            scanCallbacks.forEach { callback ->
                callback.onScanResult(callbackType, result)
            }
        }
    }
}

val Context.bluetoothAdapter: BluetoothAdapter?
    get() = getSystemService(BluetoothManager::class.java).adapter