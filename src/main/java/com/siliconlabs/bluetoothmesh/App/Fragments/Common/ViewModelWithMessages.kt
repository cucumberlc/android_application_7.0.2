/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.MessageBearer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

abstract class ViewModelWithMessages : ViewModel() {
    private val _messageFlow = MutableSharedFlow<Message>()
    val messageFlow = _messageFlow.asSharedFlow()

    /** Emit a message suspending in current scope. Note that message is not emitted if
     * current scope is cancelled, for that case use [sendMessage]. */
    protected suspend fun emitMessage(message: MessageBearer) {
        _messageFlow.emit(message.messageContent)
    }

    /** Send a message on [viewModelScope]. */
    protected fun sendMessage(message: MessageBearer) {
        viewModelScope.launch { emitMessage(message) }
    }
}