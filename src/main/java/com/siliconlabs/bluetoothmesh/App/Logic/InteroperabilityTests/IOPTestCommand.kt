/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import com.siliconlab.bluetoothmesh.adk.errors.MeshError
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import kotlinx.coroutines.withTimeoutOrNull
import org.tinylog.kotlin.Logger
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class IOPTestCommand<T> {
    abstract val name: String
    abstract val description: String

    suspend fun executeWithLogging(): Result<T> {
        Logger.debug { description }
        return execute()
            .onSuccess { Logger.debug { "$name succeeded ($it)" } }
            .onFailure { Logger.debug { it.message!! } }
    }

    protected abstract suspend fun execute(): Result<T>

    protected suspend fun <R> executeWithTimeMeasurement(
        block: suspend () -> Result<R>,
        combine: (R, Duration) -> T
    ): Result<T> {
        val partialResult: Result<R>
        val time = measureTimeMillis { partialResult = block() }.milliseconds
        return partialResult.flatMap {
            Result.success(combine(it, time))
        }
    }

    open class CommandError(message: String) : Throwable(message) {
        override fun toString() = "${this::class.simpleName}(message=$message)"
    }

    class Failed(
        command: IOPTestCommand<*>,
        message: String
    ) : CommandError("${command.name} failed ($message)") {
        constructor(
            command: IOPTestCommand<*>,
            error: MeshError
        ) : this(command, error.toString())
    }

    class Timeout(
        command: IOPTestCommand<*>,
        timeout: Duration
    ) : CommandError("${command.name} timeout (acceptable time: ${timeout.inWholeMilliseconds} ms)")

    class IncompleteResults(
        items: Set<*>
    ) : CommandError(if (items.isNotEmpty()) "Incomplete results obtained (found: $items)." else "Nothing found.") {
        constructor(items: Map<*, *>) : this(items.entries)
    }
}

suspend fun <T> IOPTestCommand<T>.executeWithTimeout(timeout: Duration): Result<T> {
    val result: Result<T>? = withTimeoutOrNull(timeout) {
        executeWithLogging()
    }
    return result ?: IOPTestCommand.Timeout(this, timeout).let {
        Logger.debug { it.message!! }
        Result.failure(it)
    }
}
