/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.*
import org.tinylog.Logger

class IOPTest<T : IOPTestProcedure.Artifact>(
    val id: IOPTestIdentity,
    private val procedure: IOPTestProcedure<T>,
) {
    private val mutableState = MutableStateFlow<State>(State.Pending)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val testNumber = id.specificationOrdinalNumber

    private var executeScope: ProducerScope<State>? = null

    suspend fun execute() = channelFlow {
        assert(state.value is State.Pending)
        executeScope = this
        Logger.debug { "Execution of test ${id.specificationOrdinalNumber} started" }

        send(executeWithRetry())
    }.onCompletion {
        executeScope = null
    }.onEach {
        mutableState.emit(it)
    }

    private suspend fun executeWithRetry(): State.Finished {
        var attempt = 0
        return flow<State.Finished> {
            attempt++
            val artifact = performAttempt(attempt)
            Logger.debug { "Test $testNumber passed on attempt no. $attempt" }
            emit(State.Passed(artifact))
        }.retryWhen { cause, _ ->
            Logger.debug { "Test $testNumber failed after attempt no. $attempt (${cause}) - performing rollback" }
            val rollbackResult = performRollback(cause)
            val hasRemainingRetryAttempts = attempt < procedure.retriesCount
            if (!hasRemainingRetryAttempts) {
                Logger.debug { "Test $testNumber failed in all attempts" }
            }
            if (!hasRemainingRetryAttempts || rollbackResult is ExecutionAndRollbackFailed) {
                emit(State.Failed(rollbackResult))
                false
            } else {
                true
            }
        }.first()
    }

    private suspend fun performAttempt(number: Int): T {
        Logger.debug { "Test $testNumber, attempt no. $number" }
        return executeProcedure().getOrThrow()
    }

    private suspend fun executeProcedure(): Result<T> {
        val scope = requireNotNull(executeScope)
        scope.send(State.Executing)
        return procedure.execute(scope)
    }

    private suspend fun performRollback(cause: Throwable) =
        rollback(cause).fold(
            onSuccess = {
                Logger.debug { "Rollback finished" }
                ExecutionFailed(cause)
            },
            onFailure = { rollbackFailCause ->
                Logger.debug { "Rollback failed (${rollbackFailCause.message})" }
                ExecutionAndRollbackFailed(cause, rollbackFailCause)
            }
        )

    private suspend fun rollback(cause: Throwable): Result<Unit> {
        val scope = requireNotNull(executeScope)
        scope.send(State.Rollback)
        return procedure.rollback(cause)
    }

    fun markAsCancelled() {
        mutableState.update { State.Canceled }
    }

    sealed interface State {
        object Pending : State {
            override fun toString(): String = "Pending"
        }

        interface InProgress : State

        object Executing : InProgress {
            override fun toString() = "Execution in progress"
        }

        object Rollback : InProgress {
            override fun toString() = "Rollback in progress"
        }

        sealed interface Finished : State

        data class Passed(val artifact: IOPTestProcedure.Artifact) : Finished {
            override fun toString() = "Passed (${artifact})"
        }

        data class Failed(val reason: FailReason) : Finished {
            override fun toString() = "Failed ($reason)"
        }

        object Canceled : Finished {
            override fun toString() = "Cancelled"
        }
    }

    sealed interface FailReason
    data class ExecutionFailed(val cause: Throwable) : FailReason
    data class ExecutionAndRollbackFailed(val cause: Throwable, val rollbackFailCause: Throwable) :
        FailReason
}
