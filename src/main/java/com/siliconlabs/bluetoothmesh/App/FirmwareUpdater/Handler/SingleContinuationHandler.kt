/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.FirmwareUpdater.Handler

import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

internal class SingleContinuationHandler<RESULT> {
    private var continuation: Continuation<RESULT?>? = null

    fun assign(newContinuation: Continuation<RESULT?>): Boolean {
        return if (continuation == null) {
            continuation = newContinuation
            addCancellationHandler(newContinuation)
            true
        } else {
            newContinuation.resume(null)
            false
        }
    }

    fun abort() {
        continuation?.resume(null)
        continuation = null
    }

    private fun addCancellationHandler(newContinuation: Continuation<RESULT?>) {
        if (newContinuation is CancellableContinuation<RESULT?>) {
            newContinuation.invokeOnCancellation {
                if (continuation === newContinuation) continuation = null
            }
        }
    }

    fun proceed(result: RESULT) {
        continuation?.resume(result)
        continuation = null
    }
}