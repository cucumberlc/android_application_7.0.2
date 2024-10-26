/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.ExportDataProvider
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestIdentity
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestSuite
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.AddingNodeProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.ProvisioningFriendNodeProcedure
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Procedures.Provisioning.ProvisioningLpnNodeProcedure
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger
import java.io.InputStream

class InteroperabilityTestsViewModel : ViewModel() {
    private lateinit var suite: IOPTestSuite
    private lateinit var suiteStateObservation: Job
    private var suiteExecution: Job? = null

    private val _suiteState: MutableStateFlow<IOPTestSuite.State> =
        MutableStateFlow(IOPTestSuite.State.Ready)
    val suiteState: StateFlow<IOPTestSuite.State> = _suiteState.asStateFlow()

    private val _testsState = MutableStateFlow<List<IOPTestItemUiState>>(emptyList())
    val testsState: StateFlow<List<IOPTestItemUiState>> = _testsState.asStateFlow()

    private val _onProvidedOutputOOBValue: MutableStateFlow<((Int) -> Unit)?> =
        MutableStateFlow(null)
    val onProvidedOutputOOBValue: StateFlow<((Int) -> Unit)?> =
        _onProvidedOutputOOBValue.asStateFlow()

    private val _inputOOBValueToDisplay: MutableStateFlow<Int?> = MutableStateFlow(null)
    val inputOOBValueToDisplay: StateFlow<Int?> = _inputOOBValueToDisplay.asStateFlow()

    init {
        initializeSuite()
    }

    private fun initializeSuite() {
        suite = IOPTestSuite()
        suiteStateObservation = viewModelScope.launch {
            launch { observeSuiteState() }
            launch { observeTestsState() }
            launch { observeStateOfProvisioningTestWithOutputOOB() }
            launch { observeStateOfProvisioningTestWithInputOOB() }
        }
    }

    fun startExecution() {
        check(suite.state.value !is IOPTestSuite.State.InProgress)
        replaceMeshNetworkStructure()
        replaceSuiteIfFinished()
        suiteExecution = viewModelScope.launch { suite.execute() }
    }

    private fun replaceMeshNetworkStructure() {
        BluetoothMesh.replaceStructure()
    }

    private fun replaceSuiteIfFinished() {
        if (suite.state.value != IOPTestSuite.State.Finished) return
        suiteStateObservation.cancel()
        initializeSuite()
    }

    private suspend fun observeSuiteState() {
        suite.state.collect { state -> _suiteState.value = state }
    }

    private suspend fun observeTestsState() {
        suite.testsState.collect { testsState ->
            _testsState.update { testsState.map { IOPTestItemUiState(it.key, it.value) } }
        }
    }

    private suspend fun observeStateOfProvisioningTestWithOutputOOB() {
        suite.testsState.map {
            setOfNotNull(
                (it[IOPTestIdentity.ProvisioningFriendNode] as? ProvisioningFriendNodeProcedure.WaitingForUserAction)?.onProvidedOutputOOBValue,
                (it[IOPTestIdentity.AddNodeToNetwork] as? AddingNodeProcedure.WaitingForUserAction)?.onProvidedOutputOOBValue
            ).singleOrNull()
        }.collect { callback ->
            _onProvidedOutputOOBValue.update { callback }
        }
    }

    private suspend fun observeStateOfProvisioningTestWithInputOOB() {
        suite.testsState.map {
            (it[IOPTestIdentity.ProvisioningLpnNode] as? ProvisioningLpnNodeProcedure.WaitingForUserAction)?.inputOOBValueToDisplay
        }.collect { value ->
            _inputOOBValueToDisplay.update { value }
        }
    }

    fun stopExecution() {
        suiteExecution?.cancel(CancellationException("Stopped by user"))
    }

    val provider = object : ExportDataProvider {
        override val data: InputStream get() = suite.getReport().generate().byteInputStream()
        override val mimeType: String get() = "text/plain"
        override val defaultName: String get() = suite.getReport().getLogFilename()
    }

    companion object {
        private fun BluetoothMesh.replaceStructure() {
            deinitialize().onFailure {
                Logger.error { "Failed to deinitialize mesh" }
            }
            initialize(MeshApplication.appContext).onFailure {
                Logger.error { "Failed to initialize mesh" }
            }

            createSubnet()
        }

        private fun createSubnet() {
            val subnets = BluetoothMesh.network.subnets
            if (subnets.isEmpty()) BluetoothMesh.network.createSubnet()
            else subnets.first()

            createGroup()
        }

        private fun createGroup() {
            val groups = BluetoothMesh.network.groups
            if (groups.isEmpty()) {
                BluetoothMesh.network.createGroup()
            }

            createAppKey()
        }

        private fun createAppKey() {
            val appKeys = BluetoothMesh.network.subnets.first().appKeys
            if (appKeys.isEmpty()) {
                BluetoothMesh.network.subnets.first().createAppKey()
            }
        }
    }
}
