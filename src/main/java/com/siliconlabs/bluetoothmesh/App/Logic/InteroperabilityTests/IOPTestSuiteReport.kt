/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import android.os.Build
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.node
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class IOPTestSuiteReport(
        private val timestamp: LocalDateTime,
        private val detectedDevices: Set<IOPTestProcedure.NodeType>,
        private val states: Map<IOPTestIdentity, IOPTest.State.Finished>
) {
    fun generate(): String =
            listOf(
                    timestampTag,
                    deviceInformationTag,
                    firmwareInformationTag,
                    testResultsTag,
            ).joinToString(separator = "\n") {
                it.toString(PrintOptions(singleLineTextElements = true, indent = "\t"))
            }

    private val timestampTag
        get() = node(timestampTagName) {
            text(timestamp.format(timestampFormatter))
        }

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val deviceInformationTag
        get() = node(deviceInformationTagName) {
            element(deviceNameTagName) {
                text(getDeviceName())
            }
            element(deviceOsVersionTagName) {
                val version = Build.VERSION.RELEASE
                val sdkVersion = Build.VERSION.SDK_INT

                text("Android $version (SDK $sdkVersion)")
            }
        }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL

        return "$manufacturer $model"
    }

    private val firmwareInformationTag
        get() = node(firmwareInformationTagName) {
            IOPTestProcedure.NodeType.values().forEach {
                element(it.tagName) {
                    text(if (it in detectedDevices) it.uuid.toString() else "N/A")
                }
            }
        }

    private val testResultsTag
        get() = node(testArtifactsTagName) {
            states.forEach { (id, state) ->
                text(generateTestResultLine(id, state))
            }
        }

    private fun generateTestResultLine(id: IOPTestIdentity, state: IOPTest.State.Finished): String {
        val number = id.specificationOrdinalNumber
        val status = when (state) {
            is IOPTest.State.Passed -> {
                val additionalDescription = when (val artifact = state.artifact) {
                    is IOPTestProcedure.Artifact.Reportable -> ", ${artifact.reportLine}"
                    IOPTestProcedure.Artifact.Unit -> ""
                }
                "passed$additionalDescription"
            }
            is IOPTest.State.Failed -> {
                val description = when (val reason = state.reason) {
                    is IOPTest.ExecutionFailed -> "execution failed (${reason.cause.message})"
                    is IOPTest.ExecutionAndRollbackFailed ->
                        "rollback failed (${reason.rollbackFailCause.message}) after execution fail (${reason.cause.message})"
                }
                "failed, $description"
            }
            IOPTest.State.Canceled -> "cancelled"
        }
        return "Test case $number $status."
    }

    fun getLogFilename(): String {
        val formattedDeviceName = getDeviceName().replace(Regex("[ .]"), "_")
        return "${formattedDeviceName}_$timestamp"
    }

    companion object {
        private const val timestampTagName = "timestamp"

        private const val deviceInformationTagName = "phone_informations"
        private const val deviceNameTagName = "phone_name"
        private const val deviceOsVersionTagName = "phone_os_version"

        private const val firmwareInformationTagName = "firmware_informations"

        private val IOPTestProcedure.NodeType.tagName
            get() = when (this) {
                IOPTestProcedure.NodeType.Proxy -> proxyNodeUUIDTagName
                IOPTestProcedure.NodeType.Relay -> relayNodeUuidTagName
                IOPTestProcedure.NodeType.Friend -> friendNodeUuidTagName
                IOPTestProcedure.NodeType.LPN -> lpnNodeUuidTagName
            }

        private const val proxyNodeUUIDTagName = "proxy_node_uuid"
        private const val relayNodeUuidTagName = "relay_node_uuid"
        private const val friendNodeUuidTagName = "friend_node_uuid"
        private const val lpnNodeUuidTagName = "lpn_node_uuid"

        private const val testArtifactsTagName = "test_results"
    }
}
