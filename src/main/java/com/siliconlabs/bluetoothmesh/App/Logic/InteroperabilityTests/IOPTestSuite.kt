/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tinylog.Logger
import java.time.LocalDateTime

class IOPTestSuite {
    private val sharedEnvironment = IOPTestProcedureSharedProperties()
    private val tests: LinkedHashSet<IOPTest<*>> = IOPTests.create(sharedEnvironment)

    private val mutableState = MutableStateFlow<State>(State.Ready)
    val state: StateFlow<State> = mutableState.asStateFlow()

    private val testsMutableState = MutableStateFlow(tests.associate { it.id to it.state.value })
    val testsState: StateFlow<Map<IOPTestIdentity, IOPTest.State>> = testsMutableState.asStateFlow()

    private lateinit var executionStartTime: LocalDateTime
    private lateinit var report: IOPTestSuiteReport

    sealed interface State {
        object Ready : State
        data class InProgress(val currentTest: IOPTestIdentity) : State
        object Finished : State
    }

    suspend fun execute() {
        check(state.value is State.Ready)
        Logger.debug { "Suite execution started" }
        executionStartTime = LocalDateTime.now()
        performTests()
    }

    private suspend fun performTests() {
        coroutineScope {
            val iterator = tests.iterator()
            launch {
                iterator.forEach { performTest(it) }
            }.invokeOnCompletion { reason ->
                if (reason != null) handleCancellation(reason, iterator)
                handleCompletion()
            }
        }
    }

    private fun handleCancellation(reason: Throwable, iterator: MutableIterator<IOPTest<*>>) {
        if (reason is CancellationException) Logger.debug { "Suite execution canceled (${reason.log})" }
        else Logger.error { "Suite execution terminated by error ($reason)" }

        iterator.forEachRemaining { it.markAsCancelled() }
        testsMutableState.update { tests.associate { it.id to it.state.value } }
    }

    private fun handleCompletion() {
        Logger.debug { "Suite execution finished" }
        mutableState.update { State.Finished }
        storeReport()
    }

    private fun storeReport() {
        assert(state.value is State.Finished)

        val detectedDevices = IOPTestProcedure.NodeType.values()
            .filter { sharedEnvironment.getDevice(it).isSuccess }
            .toSet()
        val states =
            testsMutableState.value.mapValues { (_, state) -> state as IOPTest.State.Finished }

        report = IOPTestSuiteReport(executionStartTime, detectedDevices, states)
        Logger.debug { report.generate() }
    }

    fun getReport(): IOPTestSuiteReport = report

    private suspend fun performTest(test: IOPTest<*>) {
        Logger.debug { "Performing test ${test.id.specificationOrdinalNumber}" }
        mutableState.emit(State.InProgress(currentTest = test.id))

        test.execute()
            .onCompletion { reason ->
                if (reason != null) {
                    if (reason is CancellationException) Logger.debug {
                        "Execution of test ${test.id.specificationOrdinalNumber} cancelled (${reason.log})"
                    }
                    else Logger.error {
                        "Execution of test ${test.id.specificationOrdinalNumber} terminated by error ($reason)"
                    }
                    test.markAsCancelled()
                }
            }
            .collect { testState ->
                Logger.debug {
                    "State of test ${test.id.specificationOrdinalNumber} changed: $testState"
                }
                testsMutableState.update {
                    it + (test.id to testState)
                }
            }
    }

    companion object {
        private val CancellationException.log get() = "${message}${cause?.let { "; ${it.message}" } ?: ""}"
    }
}
