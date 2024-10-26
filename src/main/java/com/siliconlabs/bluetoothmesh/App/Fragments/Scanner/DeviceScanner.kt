/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scanner

import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Models.DeviceToProvision
import com.siliconlabs.bluetoothmesh.App.Models.UnprovisionedDevice
import com.siliconlabs.bluetoothmesh.App.Utils.MessageBearer
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.conflatedCollectLatest
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

sealed class DeviceScanner(private val scope: CoroutineScope) {

    abstract val scannedDevices: StateFlow<List<UnprovisionedDevice>>

    abstract fun selectDevice(device: UnprovisionedDevice, targetSubnet: Subnet): DeviceToProvision
    abstract fun clearDevices()

    protected abstract suspend fun performScan()
    protected abstract fun isInInvalidStateFlow(): Flow<ScannerState.InvalidState?>

    private val errorState = MutableStateFlow<ScannerState.InvalidState?>(null)
    private val isScanning = MutableStateFlow(false)

    private val _errors = MutableSharedFlow<MessageBearer>()
    val errors: Flow<MessageBearer> = _errors.asSharedFlow()

    private val _scannerState = MutableStateFlow<ScannerState>(ScannerState.IDLE)
    val scannerState = _scannerState.asStateFlow()

    init {
        scope.launch { observeInvalidStates() }
        scope.launch { startStopScanner() }
    }

    protected suspend fun sendError(messageBearer: MessageBearer) =
        scope.launch { _errors.emit(messageBearer) }

    fun startScan() {
        check(_scannerState.subscriptionCount.value > 0) { "ScannerState must be collected" }
        check(scope.isActive) { "Scanner scope was already shut down" }

        if (_scannerState.value is ScannerState.InvalidState) {
            Logger.error { "Invalid state: cannot start scan when scanner state is ${_scannerState.value}" }
        } else {
            isScanning.value = true
        }
    }

    fun stopScan() {
        isScanning.value = false
    }

    private suspend fun startStopScanner() {
        combine(isScanning, errorState) { scanRequested, currentErrorState ->
            when {
                currentErrorState != null -> {
                    clearDevices()
                    _scannerState.value = currentErrorState as ScannerState
                    null
                }
                !scanRequested -> {
                    _scannerState.value = ScannerState.CLOSING_SCAN
                    false
                }
                else -> {
                    _scannerState.value = ScannerState.SCANNING
                    true
                }
            }
        }.distinctUntilChanged().conflatedCollectLatest {
            when (it) {
                null -> Unit    // keep the error state
                false -> _scannerState.value = ScannerState.IDLE    // closing -> idle
                true -> {
                    clearDevices()
                    performScan()
                    isScanning.compareAndSet(expect = true, update = false)
                }
            }
        }
    }

    private suspend fun observeInvalidStates() {
        _scannerState.hasActiveSubscriptionsFlow().collectLatest {
            if (it) {
                isInInvalidStateFlow().collect { invalidState ->
                    errorState.value = invalidState
                }
            }
        }
    }

    sealed class ScannerState {
        sealed interface InvalidState
        object NO_BLUETOOTH : ScannerState(), InvalidState
        object NO_NETWORK : ScannerState(), InvalidState
        object IDLE : ScannerState()
        object SCANNING : ScannerState()
        object CLOSING_SCAN : ScannerState()
    }
}