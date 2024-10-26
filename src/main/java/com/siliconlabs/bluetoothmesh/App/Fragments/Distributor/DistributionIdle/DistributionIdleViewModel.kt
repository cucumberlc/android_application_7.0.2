/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.DistributionIdle

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper
import com.siliconlabs.bluetoothmesh.App.Logic.AdvertisementExtensionHelper.supportsAdvertisementExtension
import com.siliconlabs.bluetoothmesh.App.Logic.ConnectionState
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.Models.AppState
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.TarGzip.TarGzipFirmwareFactory
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.conflatedCollectLatest
import com.siliconlabs.bluetoothmesh.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DistributionIdleViewModel @Inject constructor(
    private val networkConnectionLogic: NetworkConnectionLogic,
    private val context: Application,
    val selectedDistributor: Node,
) : ViewModelWithMessages() {
    val supportsAdvertisementExtension = selectedDistributor.supportsAdvertisementExtension()
    var useAdvertisementExtension = false

    val updatableNodes: List<Node>

    private val selectedNodes = mutableSetOf<Node>()

    private val _selectedNodesToUpdate = MutableStateFlow<Set<Node>>(emptySet())
    val selectedNodesToUpdate = _selectedNodesToUpdate.asStateFlow()

    private val _firmwareUri = MutableStateFlow<Uri?>(null)
    val firmwareUri = _firmwareUri.asStateFlow()

    private val _firmwareState = MutableStateFlow<TarGzipFirmwareFactory.Output?>(null)
    val firmwareState = _firmwareState.asStateFlow()

    private val _firmwareUploadStarted = MutableStateFlow(false)
    val firmwareUploadStarted = _firmwareUploadStarted.asStateFlow()

    val networkConnectionState
        get() = networkConnectionLogic.currentStateFlow

    init {
        updatableNodes = selectedDistributor.boundAppKeys.flatMap { it.nodes }
            .filter { node ->
                isUpdatable(node)
            }
        viewModelScope.launch {
            collectAndUnpackFirmwareUri()
        }
    }

    val isUploadToNodesButtonEnabled =
        combine(
            selectedNodesToUpdate,
            networkConnectionState,
            firmwareState
        ) { nodes, connected, fw ->
            nodes.isNotEmpty()
                    && connected == ConnectionState.CONNECTED
                    && fw is TarGzipFirmwareFactory.Output.Success
        }

    fun addSelectedNode(node: Node) {
        selectedNodes.add(node)
        _selectedNodesToUpdate.value = selectedNodes.toSet()
    }

    fun removeUnselectedNode(node: Node) {
        selectedNodes.remove(node)
        _selectedNodesToUpdate.value = selectedNodes.toSet()
    }

    fun setSelectedFirmware(gzipFileUri: Uri) {
        if(gzipFileUri == _firmwareUri.value){
            _firmwareUri.value = null   // invalidate
        }
        _firmwareUri.value = gzipFileUri
    }

    private fun isUpdatable(node: Node): Boolean {
        val searchFirmwareUpdateServerModel =
            node.elements.flatMap { it!!.sigModels }.find { sigModel ->
                sigModel.modelIdentifier == ModelIdentifier.DeviceFirmwareUpdateServer
            }

        return searchFirmwareUpdateServerModel != null
    }

    fun clearSelectedNodes() {
        selectedNodes.clear()
        _selectedNodesToUpdate.value = selectedNodes.toSet()
    }

    fun startUpload() {
        if (selectedNodes.isEmpty() || !networkConnectionLogic.isConnected()) {
            sendMessage(Message.error("No nodes selected or disconnected"))
            return
        }
        val firmwareState = firmwareState.value
        if (firmwareState !is TarGzipFirmwareFactory.Output.Success) {
            sendMessage(Message.error(R.string.distribution_error_firmware_not_found))
            return
        }
        if (firmwareUploadStarted.value) {
            sendMessage(Message.error(R.string.upload_is_starting))
            return
        }
        AdvertisementExtensionHelper.setUsageForLocalBlobClient(useAdvertisementExtension)
        AppState.firmware = firmwareState.firmware
        _firmwareUploadStarted.value = true
    }

    fun notifyFirmwareUploadStartedConsumed() {
        _firmwareUploadStarted.value = false
    }

    private suspend fun collectAndUnpackFirmwareUri() {
        _firmwareUri.conflatedCollectLatest { uri ->
            if(uri == null) return@conflatedCollectLatest   // user can't set a null file uri, linger old state
            _firmwareState.value = null

            TarGzipFirmwareFactory.createFirmwareFromContentUri(context, uri)
                .collect {
                    _firmwareState.value = it
                }
        }
    }

    override fun onCleared() {
        stopUpload()
        super.onCleared()
    }

    // invoke this when dialog is dismissed?
    private fun stopUpload() {
        AdvertisementExtensionHelper.setUsageForLocalBlobClient(false)
        _firmwareUploadStarted.value = false
    }
}