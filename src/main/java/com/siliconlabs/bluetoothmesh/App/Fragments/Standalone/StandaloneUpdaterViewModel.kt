/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Standalone

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.BlobTransfer
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.Mode
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.FirmwareReceiver
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Firmware
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.Cancelling
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.Completed
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.Failed
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.Idle
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.StartingUpdate
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase.TransferringImage
import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.StandaloneUpdater
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.onSuccess
import com.siliconlab.bluetoothmesh.adk.requireSuccess
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper.supportsAdvertisementExtension
import com.siliconlabs.bluetoothmesh.App.Logic.ConnectionState
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.MessageBearer
import com.siliconlabs.bluetoothmesh.App.Utils.TarGzip.TarGzipFirmwareFactory
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.conflatedCollectLatest
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.hasActiveSubscriptionsFlow
import com.siliconlabs.bluetoothmesh.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class StandaloneUpdaterViewModel @Inject constructor(
    private val networkConnectionLogic: NetworkConnectionLogic,
    private val context: Application,
    val node: Node,
) : ViewModelWithMessages() {
    val supportsAdvertisementExtension = node.supportsAdvertisementExtension()
    var useAdvertisementExtension = false

    private val _networkConnectionState = MutableStateFlow(ConnectionState.CONNECTED)
    val networkConnectionState = _networkConnectionState.asStateFlow()

    private val _firmwareUri = MutableStateFlow<Uri?>(null)
    val firmwareUri = _firmwareUri.asStateFlow()

    private val _firmwareState = MutableStateFlow<TarGzipFirmwareFactory.Output?>(null)
    val firmwareState = _firmwareState.asStateFlow()

    private val _updatePhase = MutableStateFlow(Idle to 0.0)
    val updatePhase = _updatePhase.asStateFlow()

    private val isUpdateStartRequested = MutableStateFlow(false)

    // odd design carried over from view logic; it needs previous phase to highlight errors
    var previousUpdatePhase = _updatePhase.value.first
        private set

    init {
        viewModelScope.launch {
            _networkConnectionState.hasActiveSubscriptionsFlow().collectLatest {
                if (it) observeNetworkState()
            }
        }
        viewModelScope.launch {
            var previousCollectedPhase = previousUpdatePhase
            updatePhase.collect {
                previousUpdatePhase = previousCollectedPhase
                previousCollectedPhase = it.first
            }
        }
        viewModelScope.launch {
            collectAndUnpackFirmwareUri()
        }
        viewModelScope.launch {
            collectAndPerformFirmwareUpdate()
        }
    }

    // just current updatePhase without progress
    val currentUpdatePhase
        get() = updatePhase.value.first

    val isStartFirmwareUpdateButtonEnabled =
        combine(
            networkConnectionState,
            firmwareState
        ) { connected, fw ->
            connected == ConnectionState.CONNECTED
                    && fw is TarGzipFirmwareFactory.Output.Success
        }

    fun startUpdate() {
        val currentPhase = currentUpdatePhase
        if (currentPhase == Completed) {
            sendMessage(Message.error(R.string.error_firmware_update_already_completed))
            return
        }
        if (currentPhase != Idle && currentPhase != Cancelling) {
            sendMessage(Message.error(R.string.error_firmware_update_not_idle))
            return
        }
        if (networkConnectionState.value != ConnectionState.CONNECTED) {
            sendMessage(Message.error(R.string.error_message_disconnected_from_device))
            return
        }
        if (firmwareState.value !is TarGzipFirmwareFactory.Output.Success) {
            sendMessage(Message.error(R.string.distribution_error_firmware_not_found))
            return
        }
        if (isUpdateStartRequested.value) {
            sendMessage(Message.error(R.string.operation_in_progress))
            return
        }
        isUpdateStartRequested.value = true
    }

    fun stopUpdate() {
        if (currentUpdatePhase == Completed) {
            sendMessage(Message.error(R.string.error_firmware_update_already_completed))
            return
        }
        isUpdateStartRequested.value = false
    }

    fun setSelectedFirmware(gzipFileUri: Uri) {
        if (gzipFileUri == _firmwareUri.value) {
            _firmwareUri.value = null   // invalidate
        }
        _firmwareUri.value = gzipFileUri
    }

    private fun stopUpdateIfNotCompleted() {
        if (currentUpdatePhase != Completed) {
            isUpdateStartRequested.value = false
        }
    }

    private suspend fun observeNetworkState() {
        networkConnectionLogic.currentStateFlow.collect {
            _networkConnectionState.value = it
            if (it == ConnectionState.DISCONNECTED)
                stopUpdateIfNotCompleted()
        }
    }

    private suspend fun collectAndUnpackFirmwareUri() {
        _firmwareUri.conflatedCollectLatest { uri ->
            if (uri == null) return@conflatedCollectLatest   // user can't set a null file uri, linger old state
            _firmwareState.value = null

            TarGzipFirmwareFactory.createFirmwareFromContentUri(context, uri)
                .collect {
                    _firmwareState.value = it
                }
        }
    }

    private suspend fun collectAndPerformFirmwareUpdate() {
        combine(isUpdateStartRequested, firmwareState) { start, fwState ->
            val firmwareToUpload =
                (fwState as? TarGzipFirmwareFactory.Output.Success)?.firmware?.takeIf { start }

            val phase = when (firmwareToUpload) {
                null -> when (currentUpdatePhase) {
                    Idle -> Idle
                    else -> Cancelling
                }

                else -> StartingUpdate
            }

            _updatePhase.value = phase to 0.0
            firmwareToUpload
        }
            .distinctUntilChanged()
            .conflatedCollectLatest { firmware ->
                if (firmware == null) {
                    _updatePhase.value = Idle to 0.0
                } else {
                    try {
                        performFirmwareUpdate(firmware)
                        Logger.debug { "Finished update procedure" }
                    } catch (blobTransferError: BlobTransferFailedError) {
                        Logger.error { "Cancelling update procedure ($blobTransferError)" }
                        _updatePhase.update { it.copy(Failed) }
                        cancelOngoingUpdate()   // note: exception is consumed
                    } catch (cancelled: CancellationException) {
                        Logger.debug { "Cancelling update procedure" }
                        cancelOngoingUpdate()
                        throw cancelled
                    }
                }
            }
    }

    private fun cancelOngoingUpdate() {
        StandaloneUpdater.cancelUpdate()
            .onFailure { sendMessage(Message.error(it)) }
    }

    private suspend fun performFirmwareUpdate(firmware: Firmware) {
        networkConnectionState.first { it == ConnectionState.CONNECTED }

        AdvertisementExtensionHelper.setUsageForLocalBlobClient(useAdvertisementExtension)
        val mode = when (node.deviceCompositionData?.supportsLowPower) {
            true -> Mode.PULL
            else -> Mode.PUSH
        }

        StandaloneUpdater.start(
            listOf(FirmwareReceiver(node, 0)),
            firmware,
            node.boundAppKeys.first(),
            mode,
        ).requireSuccess {
            _updatePhase.value = Failed to 0.0
            Logger.error { "Failed to StandaloneUpdater.start: ${it.cause}" }
            sendMessage(Message.error(it.cause))
            return // just return because theres no need to cancel ongoing update
        }

        StandaloneUpdater.state
            .cancellable()
            .distinctUntilChangedBy { it.phase }
            .collectLatest { state ->
                _updatePhase.update { it.copy(state.phase) }
                when (state.phase) {
                    TransferringImage -> performImageTransfer(firmware)
                    else -> Unit // nothing else to do
                }
            }
    }

    private suspend fun performImageTransfer(firmware: Firmware) {
        coroutineScope {
            var transferError: BlobTransferFailedError? = null

            val updateProgressJob = launch(start = CoroutineStart.LAZY) {
                while (true) {
                    val progress = BlobTransfer.progress
                    _updatePhase.value = TransferringImage to progress
                    delay(3000L)
                }
            }
            BlobTransfer.startTransfer(
                firmware.blob,
            ) { outcome ->
                outcome.onFailure {
                    val message = Message.error(it)
                    Logger.error { "Failed to start blob transfer: $it" }
                    sendMessage(message)
                    transferError = BlobTransferFailedError(message)
                    updateProgressJob.cancel(message.message)
                }.onSuccess {
                    updateProgressJob.cancel("Transfer complete")
                    _updatePhase.value = TransferringImage to 1.0
                }
            }
            try {
                updateProgressJob.join()
            } finally {
                transferError?.let { throw it }
            }
        }
        _updatePhase.value = TransferringImage to 1.0
    }

    override fun onCleared() {
        super.onCleared()
        AdvertisementExtensionHelper.setUsageForLocalBlobClient(false)
    }

    private class BlobTransferFailedError(messageContent: Message) :
        MessageBearer.Exception(messageContent)
}