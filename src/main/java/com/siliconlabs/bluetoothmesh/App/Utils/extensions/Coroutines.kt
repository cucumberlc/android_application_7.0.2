/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import java.util.concurrent.CancellationException

private val emptyJob by lazy { Job().apply { cancel(CancellationException("emptyJob() object")) } }

/** Placeholder completed and cancelled job to use for non-nullable [Job] fields. */
fun emptyJob(): Job = emptyJob

fun <T> CancellableContinuation<T>.safeResume(value: T) = safeResumeWith(Result.success(value))

fun <T> CancellableContinuation<T>.safeResumeWithException(exception: Throwable) =
    safeResumeWith(Result.failure(exception))

fun <T> CancellableContinuation<T>.safeResumeWith(result: Result<T>) = runCatching {
    resumeWith(result)
}.isSuccess