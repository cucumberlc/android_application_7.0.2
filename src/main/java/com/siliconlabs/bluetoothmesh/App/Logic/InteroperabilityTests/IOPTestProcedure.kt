/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import android.bluetooth.le.ScanSettings
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.data_model.element.Element
import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.BeaconingCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.OpenProxyConnectionCommand
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.Commands.RemoveNodeCommand
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import com.siliconlabs.bluetoothmesh.App.Utils.flatMap
import kotlinx.coroutines.channels.ProducerScope
import org.tinylog.Logger
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class IOPTestProcedure<out T : IOPTestProcedure.Artifact>(
    private val sharedProperties: IOPTestProcedureSharedProperties,
    val retriesCount: Int = 5
) {
    abstract suspend fun execute(stateOutputChannel: ProducerScope<IOPTest.State>): Result<T>
    open suspend fun rollback(cause: Throwable): Result<Unit> = Result.success(Unit)

    protected suspend fun <R> performAsTransaction(
        transaction: suspend (UByte) -> Result<R>
    ): Result<R> =
        sharedProperties.performAsTransaction(transaction)

    protected fun storeSubnetConnection(connection: ProxyConnection) =
        sharedProperties.storeSubnetConnection(connection)

    private fun getSubnetConnection() =
        sharedProperties.getSubnetConnection()

    protected fun getDevice(type: NodeType) =
        sharedProperties.getDevice(type)

    protected fun storeDevice(type: NodeType, device: BluetoothConnectableDevice) =
        sharedProperties.storeDevice(type, device)

    protected fun getSubnet(): Result<Subnet> {
        val subnet = BluetoothMesh.network.subnets.firstOrNull()
        return subnet
            ?.let { Result.success(it) }
            ?: Result.failure(NotFound("No subnet found"))
    }

    protected fun getGroup(): Result<Group> {
        return BluetoothMesh.network.groups.firstOrNull()
            ?.let { Result.success(it) }
            ?: Result.failure(NotFound("No group found"))
    }

    protected fun getAppKey(): Result<AppKey> {
        return getSubnet().flatMap { subnet ->
            subnet.appKeys.firstOrNull()
                ?.let { Result.success(it) }
                ?: Result.failure(NotFound("No appkey found"))
        }
    }

    protected fun getNodes(types: Set<NodeType>): Result<Map<NodeType, Node>> {
        val foundNodes = mutableMapOf<NodeType, Node>()
        val notFoundTypes = mutableSetOf<NodeType>()

        types.forEach { type ->
            getNode(type)
                .onSuccess { node -> foundNodes[type] = node }
                .onFailure { notFoundTypes.add(type) }
        }

        return if (notFoundTypes.isEmpty()) Result.success(foundNodes)
        else {
            val typesChunk = notFoundTypes.joinToString(", ")
            val nodeChunk = if (notFoundTypes.size == 1) "node" else "nodes"
            Result.failure(
                NotFound("$typesChunk $nodeChunk not found in subnet")
            )
        }
    }

    protected fun getNode(type: NodeType): Result<Node> {
        return getSubnet().flatMap { subnet ->
            subnet.nodes.find { it.uuid == type.uuid }
                ?.let { Result.success(it) }
                ?: Result.failure(NotFound("${type.name} node not found in subnet"))
        }
    }

    protected fun getElement(node: Node): Result<Element> {
        val element =
            node.elements.find { element -> element!!.sigModels.any { it.modelIdentifier == ModelIdentifier.GenericOnOffServer } }
        return element
            ?.let { Result.success(it) }
            ?: Result.failure(
                NotFound("No element with Generic OnOff Server Model found in node")
            )
    }

    protected fun getModel(node: Node): Result<Model> {
        val model = node.elements.flatMap { it!!.sigModels }
            .find { it.modelIdentifier == ModelIdentifier.GenericOnOffServer }
        return model
            ?.let { Result.success(it) }
            ?: Result.failure(NotFound("No Generic OnOff Server Model found in node"))
    }

    protected suspend fun checkConnection(): Result<ProxyConnection> {
        return getSubnetConnection().flatMap { connection ->
            OpenProxyConnectionCommand(connection).executeWithTimeout(10.seconds).map { connection }
        }
    }

    protected fun checkTime(subject: String, time: Duration, timeout: Duration): Result<Duration> {
        return if (time <= timeout) Result.success(time)
        else Result.failure(TimeExceeded(subject, time, timeout))
    }

    protected suspend fun removeNodeAndRescanDevice(type: NodeType, node: Node): Result<Unit> {
        return removeNode(node).fold(
            onSuccess = { refreshDevice(type) },
            onFailure = { removalFail ->
                refreshDevice(type).fold(
                    onSuccess = {
                        Logger.debug {
                            "Device has been unprovisioned, but didn't get response from it. " +
                                    "Delete node from database and continue"
                        }
                        node.removeOnlyFromLocalStructure()
                        Result.success(Unit)
                    },
                    onFailure = {
                        Result.failure(removalFail)
                    }
                )
            }
        )
    }

    private suspend fun removeNode(node: Node): Result<Unit> {
        return RemoveNodeCommand(node).executeWithTimeout(defaultCommandTimeout)
    }

    private suspend fun refreshDevice(type: NodeType): Result<Unit> {
        return run {
            beaconDevice(type)
        }.map { (device, _) ->
            storeDevice(type, device)
        }
    }

    protected suspend fun beaconDevice(
        type: NodeType,
        scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY
    ): Result<BeaconingCommand.Artifacts> {
        return BeaconingCommand(type, scanMode, beaconingCommandDefaultTimeout).executeWithLogging()
    }

    protected suspend fun removeAndFindNodes(nodes: Map<NodeType, Node>): Result<Unit> {
        val removalFailures = mutableMapOf<NodeType, Throwable>()
        nodes.forEach { (type, node) ->
            removeNodeAndRescanDevice(type, node).onFailure {
                removalFailures[type] = it
            }
        }

        return if (removalFailures.isEmpty()) Result.success(Unit)
        else Result.failure(RemovalFailed(removalFailures))
    }

    sealed interface Artifact {
        interface Reportable : Artifact {
            val reportLine: String
        }

        object Unit : Artifact
    }

    data class TimeArtifact(
        private val subject: String,
        private val time: Duration
    ) : Artifact.Reportable {
        override val reportLine = "$subject time: ${time.inWholeMilliseconds} ms"
    }

    data class TimeWithTimeoutArtifact(
        private val subject: String,
        private val time: Duration,
        private val timeout: Duration
    ) : Artifact.Reportable {
        override val reportLine =
            "$subject time: ${time.inWholeMilliseconds} ms; acceptable time: ${timeout.inWholeMilliseconds} ms"
    }

    data class SettingArtifact(
        private val settingTime: TimeArtifact,
        private val obtainingTimeArtifact: TimeWithTimeoutArtifact
    ) : Artifact.Reportable {
        override val reportLine = "${settingTime.reportLine}, ${obtainingTimeArtifact.reportLine}"
    }

    open class ProcedureError(message: String) : Throwable(message) {
        override fun toString() = "${this::class.simpleName}(message=$message)"
    }

    class NotFound(message: String) : ProcedureError(message)

    class TimeExceeded(
        subject: String,
        time: Duration,
        timeout: Duration
    ) : ProcedureError(
        "Expected $subject time was exceeded ($subject time: ${time.inWholeMilliseconds} ms; acceptable time: ${timeout.inWholeMilliseconds} ms)"
    )

    class RemovalFailed(
        causes: Map<NodeType, Throwable>
    ) : ProcedureError("Removal of nodes failed: $causes}")

    enum class NodeType(val uuid: UUID) {
        Proxy(UUID.fromString("00010203-0405-0607-0809-0A0B0C0D0E0F")),
        Relay(UUID.fromString("00010203-0405-0607-0809-0A0B0C0D0E1F")),
        Friend(UUID.fromString("00010203-0405-0607-0809-0A0B0C0D0E2F")),
        LPN(UUID.fromString("00010203-0405-0607-0809-0A0B0C0D0E3F"));
    }

    companion object {
        val defaultCommandTimeout = 10.seconds
        val beaconingCommandDefaultTimeout = 5.seconds
    }
}
