/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ViewModelWithMessages
import com.siliconlabs.bluetoothmesh.App.Utils.*
import com.siliconlabs.bluetoothmesh.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.tinylog.Logger

abstract class CertificateViewModel(protected val context: Application) : ViewModelWithMessages() {
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun certificateStateStateFlowFrom(
        sourceFlow: MutableStateFlow<CertificateFileUtils.CertificateFile?>,
    ) = sourceFlow.transformLatest {
        if (it == null) {
            emit(null)
            return@transformLatest
        }
        val name = it.getName(context)!!
        emit(CertificateState(name, true))
        it.loadData(context)
            .onSuccess {
                emit(CertificateState(name, false))
            }.onFailure { error ->
                Logger.error(error) { error.message }
                emitMessage(Message.error(R.string.error_process_selected_file))
                sourceFlow.value = null
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    data class CertificateState(val name: String, val isLoading: Boolean) {
        val isReady = !isLoading
    }
}