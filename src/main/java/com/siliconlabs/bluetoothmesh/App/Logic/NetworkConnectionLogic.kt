/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import com.jcabi.aspects.Loggable
import com.siliconlab.bluetoothmesh.adk.connectable_device.ConnectableDevice
import com.siliconlab.bluetoothmesh.adk.connectable_device.ConnectionCallback
import com.siliconlab.bluetoothmesh.adk.connectable_device.DisconnectionCallback
import com.siliconlab.bluetoothmesh.adk.connectable_device.FilterType
import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection
import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyConnection.MESH_PROXY_SERVICE
import com.siliconlab.bluetoothmesh.adk.connectable_device.ProxyControl
import com.siliconlab.bluetoothmesh.adk.connectable_device.SetFilterTypeCallback
import com.siliconlab.bluetoothmesh.adk.connectable_device.asNode
import com.siliconlab.bluetoothmesh.adk.connectable_device.doesNetworkIdentityMatch
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlab.bluetoothmesh.adk.errors.GattConnectionError
import com.siliconlabs.bluetoothmesh.App.Models.BluetoothConnectableDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import org.tinylog.kotlin.Logger

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class NetworkConnectionLogic : BluetoothConnectableDevice.DeviceConnectionCallback {
    private val uiHandler: Handler = Handler(Looper.getMainLooper())
    private var currentState = ConnectionState.DISCONNECTED
    private val listeners = mutableSetOf<NetworkConnectionListener>()
    private var subnet: Subnet? = null
    private var proxyConnection: ProxyConnection? = null
    private var connectionTimeoutRunnable = Runnable { connectionTimeout() }
    private val delayInMillis = 4000L

    @Synchronized
    fun connect(subnet: Subnet) {
        if (!shouldConnect(subnet)) {
            return
        }

        Logger.debug { "Connecting to subnet" }
        setCurrentState(ConnectionState.CONNECTING)

        this.subnet = subnet
        BluetoothScanner.addScanCallback(scanCallback)
        startScan()
    }

    private fun shouldConnect(subnet: Subnet) =
        this.subnet != subnet || currentState == ConnectionState.DISCONNECTED

    private fun connect(bluetoothConnectableDevice: BluetoothConnectableDevice) {
        synchronized(currentState) {
            if (subnet != null) {
                doDisconnect()
            }

            Logger.debug { "Connecting to device" }
            setCurrentState(ConnectionState.CONNECTING)

            // workaround to 133 gatt issue
            // https://github.com/googlesamples/android-BluetoothLeGatt/issues/44
            uiHandler.postDelayed({
                bluetoothConnectableDevice.addDeviceConnectionCallback(this@NetworkConnectionLogic)
                proxyConnection = ProxyConnection(bluetoothConnectableDevice)
                proxyConnection!!.connectToProxy(false,
                    @Loggable object : ConnectionCallback {
                        override fun success(device: ConnectableDevice) {
                            setRejectFilterType()
                        }

                        override fun error(
                        device: ConnectableDevice,
                        error: ConnectionError
                    ) {
                            setCurrentState(ConnectionState.DISCONNECTED)
                            connectionErrorMessage(error)
                        }
                    })
            }, 500)
        }
    }

    private fun setRejectFilterType() {
        val proxyControl = ProxyControl(proxyConnection!!)

        proxyControl.setFilterType(FilterType.REJECT_LIST, object : SetFilterTypeCallback {
            override fun success(filterType: FilterType) {
                Logger.debug { "Success setting reject filter type" }
                setCurrentState(ConnectionState.CONNECTED)
            }

            override fun error(filterType: FilterType, error: ConnectionError) {
                Logger.debug { "Failure setting reject filter type: $error" }
                setCurrentState(ConnectionState.DISCONNECTED)
                connectionErrorMessage(error)
            }
        })
    }

    @Loggable
    fun disconnect() {
        subnet = null
        doDisconnect()
    }

    @Loggable
    private fun doDisconnect() {
        stopScan()
        BluetoothScanner.removeScanCallback(scanCallback)
        setCurrentState(ConnectionState.DISCONNECTED)
        val bluetoothConnectableDevice =
            proxyConnection?.connectableDevice as BluetoothConnectableDevice?
        bluetoothConnectableDevice?.removeDeviceConnectionCallback(this)
        proxyConnection?.disconnect(object : DisconnectionCallback {
            @Loggable
            override fun success(device: ConnectableDevice) {
            }

            @Loggable
            override fun error(device: ConnectableDevice?, error: ConnectionError?) {
            }
        })
    }

    fun setEstablishedProxyConnection(proxyConnection: ProxyConnection, subnet: Subnet) {
        this.proxyConnection = proxyConnection
        this.subnet = subnet
        setCurrentState(ConnectionState.CONNECTED)
    }

    fun addListener(networkConnectionListener: NetworkConnectionListener) {
        synchronized(listeners) {
            listeners.add(networkConnectionListener)

            notifyCurrentState(networkConnectionListener)
        }
    }

    fun removeListener(networkConnectionListener: NetworkConnectionListener) {
        synchronized(listeners) {
            listeners.remove(networkConnectionListener)
        }
    }

    val currentStateFlow
        get() = callbackFlow {
            val l = object : NetworkConnectionListener {
                override fun onNetworkConnectionStateChanged(state: ConnectionState) {
                    trySend(state)
                }
            }
            addListener(l)
            awaitClose { removeListener(l) }
        }.distinctUntilChanged().conflate()

    // DeviceConnectionCallback

    @Loggable
    override fun onConnectedToDevice() {
        //ignore
    }

    @Loggable
    override fun onDisconnectedFromDevice() {
        disconnect()
    }

    // ScanCallback

    private val scanCallback = @Loggable object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bluetoothConnectableDevice = BluetoothConnectableDevice(result)

            subnet?.let {
                if (!bluetoothConnectableDevice.isInSubnet(it) && !bluetoothConnectableDevice.doesNetworkIdentityMatch(
                        it
                    ))
             {
                    return
                }
            }
            stopScan()
            connect(bluetoothConnectableDevice)
        }
    }

    private fun ConnectableDevice.isInSubnet( subnet: Subnet?): Boolean =
        asNode()?.subnets?.contains(subnet) ?: false

    @Loggable
    private fun startScan() {
        if (subnet?.nodes!!.isEmpty()) {
            disconnect()
            return
        }

        BluetoothScanner.startLeScan(MESH_PROXY_SERVICE)
        uiHandler.removeCallbacks(connectionTimeoutRunnable)
        uiHandler.postDelayed(connectionTimeoutRunnable, delayInMillis)
    }

    @Loggable
    private fun stopScan() {
        BluetoothScanner.stopLeScan()
    }

    @Loggable
    private fun connectionTimeout() {
        stopScan()
        setCurrentState(ConnectionState.DISCONNECTED)
        connectionErrorMessage(GattConnectionError.Status.CouldNotConnectToDevice())
    }

    fun isConnected(): Boolean {
        synchronized(this) {
            return currentState == ConnectionState.CONNECTED
        }
    }

    fun isConnectedTo(subnet: Subnet): Boolean {
        synchronized(this) {
            return subnet == this.subnet && currentState == ConnectionState.CONNECTED
        }
    }

    @Loggable
    private fun setCurrentState(currentState: ConnectionState) {
        synchronized(this) {
            if (this.currentState == currentState) {
                return
            }
            uiHandler.removeCallbacks(connectionTimeoutRunnable)
            this.currentState = currentState
        }
        notifyCurrentState()
    }

    private fun notifyCurrentState() {
        synchronized(listeners) {
            listeners.forEach { listener -> notifyCurrentState(listener) }
        }
    }

    private fun notifyCurrentState(listener: NetworkConnectionListener) {
        uiHandler.post { listener.onNetworkConnectionStateChanged(currentState) }
    }

    private fun connectionErrorMessage(connectionError: ConnectionError) {
        synchronized(listeners) {
            uiHandler.post {
                listeners.forEach { listener -> listener.connectionErrorMessage(connectionError) }
            }
        }
    }

    fun getCurrentlyConnectedNode(): Node? =
        proxyConnection?.connectableDevice?.asNode()
}