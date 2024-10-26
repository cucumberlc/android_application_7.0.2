/*
 * Copyright © 2020 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.jcabi.aspects.Loggable
import com.siliconlab.bluetoothmesh.adk.connectable_device.*
import com.siliconlab.bluetoothmesh.adk.provisioning.OobInformation
import com.siliconlabs.bluetoothmesh.App.Logic.BluetoothScanner
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.fromBitset
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.tinylog.kotlin.Logger
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.TimeUnit

open class BluetoothConnectableDevice(
        var scanResult: ScanResult,
) : ConnectableDevice() {
    var deviceConnectionCallbacks = mutableSetOf<DeviceConnectionCallback>()
    var mainHandler = Handler(Looper.getMainLooper())
    var bluetoothGatt: BluetoothGatt? = null
    lateinit var bluetoothDevice: BluetoothDevice
    lateinit var address: String
        private set

    lateinit var bluetoothGattCallback: BluetoothGattCallback
    lateinit var scanCallback: ScanCallback
    private var refreshBluetoothDeviceCallback: RefreshBluetoothDeviceCallback? = null
    private lateinit var refreshBluetoothDeviceTimeoutRunnable: Runnable
    val oobInformation: Set<OobInformation>
        get() = fromBitset(BitSet.valueOf(byteArrayOf(advertisementData!![28], advertisementData!![27])))

    init {
        processScanResult(scanResult)
        initScanCallback()
        initBluetoothGattCallback()
        initRefreshBluetoothDeviceTimeoutRunnable()
    }

    private fun processScanResult(scanResult: ScanResult) {
        this.bluetoothDevice = scanResult.device
        this.advertisementData = scanResult.scanRecord!!.bytes
        this.address = bluetoothDevice.address
        this.scanResult = scanResult
    }

    fun initScanCallback() {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                Logger.debug { "onScanResult result = $result" }

                result?.let {
                    if (it.device.address == address) {
                        processDeviceFound(result)
                    }
                }
            }

            fun processDeviceFound(result: ScanResult) {
                stopScan()
                mainHandler.removeCallbacks(refreshBluetoothDeviceTimeoutRunnable)
                processScanResult(result)
                // workaround to 133 gatt issue
                // https://github.com/googlesamples/android-BluetoothLeGatt/issues/44
                mainHandler.postDelayed({ refreshBluetoothDeviceCallback?.success() }, 500)
            }
        }
    }

    fun initBluetoothGattCallback() {
        bluetoothGattCallback = object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            @Loggable
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                mainHandler.post {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> {
                                connectionRequest?.handleSuccess()
                            }
                            BluetoothProfile.STATE_DISCONNECTED,
                            BluetoothProfile.STATE_DISCONNECTING -> {
                                handleDisconnected()
                            }
                            BluetoothProfile.STATE_CONNECTING -> Unit
                        }
                    } else {
                        gatt.close()
                        if (connecting) {
                            connectionRequest?.handleAttemptFailure()
                        } else {
                            handleBrokenConnection()
                        }
                    }
                }
            }

            @Loggable
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                mainHandler.post {
                    this@BluetoothConnectableDevice.mtu = mtu
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        mtuRequest?.handleSuccess()
                    } else {
                        mtuRequest?.handleAttemptFailure()
                    }
                }
            }

            @Loggable
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                mainHandler.post {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        discoverServicesRequest?.handleSuccess()
                    } else {
                        discoverServicesRequest?.handleAttemptFailure()
                    }
                }
            }

            @Loggable
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                updateData(characteristic.service.uuid, characteristic.uuid, characteristic.value)
            }
        }
    }

    private fun handleBrokenConnection() {
        onConnectionError()
        notifyConnectionState(false)
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnected() {
        bluetoothGatt?.close()
        /* When connection is being closed by Android, it needs 1s to check if other apps are using this gatt connection with this peripheral.
        Otherwise, when trying to re-connect without delay, connection will close on behalf of a peripheral after a while calling
        #onConnectionStateChange with status 22 */

        notifyDisconnectionWithDelay()
    }

    private fun notifyDisconnectionWithDelay() {
        mainHandler.postDelayed({
            notifyDeviceDisconnected()
            Logger.debug { "Gatt connection closed" }
        }, 1500)
    }

    fun initRefreshBluetoothDeviceTimeoutRunnable() {
        refreshBluetoothDeviceTimeoutRunnable = Runnable {
            refreshingBluetoothDeviceTimeout()
        }
    }

    interface RequestCallback {
        fun success()
        fun failure()
    }

    @Loggable
    fun discoverServices(callback: RequestCallback) {
        DiscoverServicesRequest(callback).process()
    }

    private var discoverServicesRequest: DiscoverServicesRequest? = null

    private abstract class Request(private val callback: RequestCallback) {
        private var attempt = 0

        abstract fun process()
        abstract fun finish()

        fun handleSuccess() {
            finish()
            callback.success()
        }

        fun handleAttemptFailure() {
            if (++attempt < 3) {
                process()
            } else {
                handleFailure()
            }
        }

        fun handleFailure() {
            finish()
            callback.failure()
        }
    }

    private inner class DiscoverServicesRequest(callback: RequestCallback) : Request(callback) {
        init {
            discoverServicesRequest = this
        }

        @SuppressLint("MissingPermission")
        override fun process() {
            repeat(3) {
                if (bluetoothGatt!!.discoverServices()) {
                    return
                }
                sleep(50)
                Logger.debug { "retry discover services i: $it" }
            }
            handleFailure()
        }

        override fun finish() {
            discoverServicesRequest = null
        }
    }

    private var mtu = 0
    override fun getMTU() = mtu

    @Loggable
    fun changeMtu(callback: RequestCallback) {
        MtuRequest(callback).process()
    }

    private var mtuRequest: MtuRequest? = null

    private inner class MtuRequest(callback: RequestCallback) : Request(callback) {
        init {
            mtuRequest = this
        }

        @SuppressLint("MissingPermission")
        override fun process() {
            repeat(3) {
                if (bluetoothGatt!!.requestMtu(512)) {
                    return
                }
                sleep(50)
                Logger.debug { "retry request mtu i: $it" }
            }
            handleFailure()
        }

        override fun finish() {
            mtuRequest = null
        }
    }

    private var connectionRequest: ConnectionRequest? = null
    val connecting get() = connectionRequest != null

    private inner class ConnectionRequest(callback: RequestCallback) : Request(callback) {
        init {
            connectionRequest = this
        }

        @SuppressLint("MissingPermission")
        override fun process() {
            checkMainThread()
            bluetoothGatt = bluetoothDevice.connectGatt(
                    MeshApplication.appContext,
                    false,
                    bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE
            ).also {
                it.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                setupTimeout(it)
            }
        }

        private fun setupTimeout(bluetoothGattLast: BluetoothGatt) {
            mainHandler.postDelayed({
                if (bluetoothGatt == bluetoothGattLast && connecting) {
                    Logger.debug { "connection timeout mac: $address" }
                    onConnectionError()
                }
            }, TimeUnit.SECONDS.toMillis(30))
        }

        override fun finish() {
            connectionRequest = null
        }
    }

    fun addDeviceConnectionCallback(deviceConnectionCallback: DeviceConnectionCallback) {
        synchronized(deviceConnectionCallbacks) {
            deviceConnectionCallbacks.add(deviceConnectionCallback)
        }
    }

    fun removeDeviceConnectionCallback(deviceConnectionCallback: DeviceConnectionCallback) {
        synchronized(deviceConnectionCallbacks) {
            deviceConnectionCallbacks.remove(deviceConnectionCallback)
        }
    }

    fun notifyConnectionState(connected: Boolean) {
        synchronized(deviceConnectionCallbacks) {
            for (callback in deviceConnectionCallbacks) {
                notifyConnectionState(callback, connected)
            }
        }
    }

    fun connectionStateFlow() = callbackFlow {
        val callback = object : DeviceConnectionCallback{
            override fun onConnectedToDevice() {
                trySend(true)
            }

            override fun onDisconnectedFromDevice() {
                trySend(false)
            }
        }
        send(isConnected)
        addDeviceConnectionCallback(callback)
        awaitClose{ removeDeviceConnectionCallback(callback) }
    }.buffer(1, BufferOverflow.DROP_OLDEST).distinctUntilChanged()

    private fun notifyConnectionState(callback: DeviceConnectionCallback, connected: Boolean) {
        if (connected) {
            callback.onConnectedToDevice()
        } else {
            callback.onDisconnectedFromDevice()
        }
    }

    @SuppressLint("MissingPermission")
    override fun getName(): String? {
        return bluetoothDevice.name
    }

    override fun refreshBluetoothDevice(callback: RefreshBluetoothDeviceCallback) {
        if (startScan()) {
            Logger.debug { "refreshBluetoothDevice: starting scan succeeded" }
            onScanStarted(callback)
        } else {
            Logger.debug { "refreshBluetoothDevice: starting scan failed" }
            callback.failure()
        }
    }

    private fun onScanStarted(callback: RefreshBluetoothDeviceCallback) {
        refreshBluetoothDeviceCallback = callback
        mainHandler.removeCallbacks(refreshBluetoothDeviceTimeoutRunnable)
        mainHandler.postDelayed(refreshBluetoothDeviceTimeoutRunnable, 10000L)
    }

    @Loggable
    private fun refreshingBluetoothDeviceTimeout() {
        mainHandler.removeCallbacks(refreshBluetoothDeviceTimeoutRunnable)
        stopScan()
        refreshBluetoothDeviceCallback?.failure()
        refreshBluetoothDeviceCallback = null
    }

    override fun connect() {
        Logger.debug { "connect mac: $address" }
        ConnectionRequest(object : RequestCallback {
            override fun success() {
                configureConnectionAndNotifyResult()
            }

            override fun failure() {
                onConnectionError()
            }
        }).process()
    }

    private fun configureConnectionAndNotifyResult() {
        changeMtu(object : RequestCallback {
            private val servicesDiscoveredCallback = object : RequestCallback {
                override fun success() {
                    notifyDeviceConnected()
                }

                override fun failure() {
                    disconnect()
                }
            }

            override fun success() {
                discoverServices(servicesDiscoveredCallback)
            }

            override fun failure() {
                discoverServices(servicesDiscoveredCallback)
            }
        })
    }

    private fun startScan(): Boolean {
        BluetoothScanner.addScanCallback(scanCallback)
        return BluetoothScanner.startLeScan()
    }

    private fun stopScan() {
        BluetoothScanner.removeScanCallback(scanCallback)
        BluetoothScanner.stopLeScan()
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        Logger.debug { "disconnect mac: $address" }
        checkMainThread()

        connectionRequest?.finish()
        mainHandler.removeCallbacks(refreshBluetoothDeviceTimeoutRunnable)
        refreshDeviceCache()
        bluetoothGatt?.disconnect()
        stopScan()
    }

    @Loggable
    private fun notifyDeviceConnected() {
        connectionRequest?.finish()
        onConnected()
        notifyConnectionState(true)
    }

    private fun notifyDeviceDisconnected() {
        onDisconnected()
        notifyConnectionState(false)
    }

    fun refreshDeviceCache(): Boolean {
        var result = false
        try {
            val refreshMethod = bluetoothGatt?.javaClass?.getMethod("refresh")
            result = refreshMethod?.invoke(bluetoothGatt, *arrayOfNulls(0)) as? Boolean ?: false
            Logger.debug { "refreshDeviceCache $result" }
        } catch (localException: Exception) {
            Logger.error { "An exception occurred while refreshing device" }
        }
        return result
    }

    private var advertisementData: ByteArray? = null
    override fun getAdvertisementData() = advertisementData

    override fun refreshGattServices(refreshGattServicesCallback: RefreshGattServicesCallback) {
        if (refreshDeviceCache()) {
            discoverServices(object : RequestCallback {
                override fun success() {
                    refreshGattServicesCallback.onSuccess()
                }

                override fun failure() {
                    refreshGattServicesCallback.onFail()
                    disconnect()
                }
            })
        } else {
            refreshGattServicesCallback.onFail()
        }
    }

    override fun getServiceData(service: UUID?): ByteArray? {
        return service?.let { scanResult.scanRecord?.getServiceData(ParcelUuid(it)) }
    }

    @Loggable
    override fun hasService(service: UUID?): Boolean {
        return if (bluetoothGatt?.services?.isNotEmpty() == true) {
            bluetoothGatt!!.getService(service) != null
        } else {
            scanResult.scanRecord?.serviceUuids?.contains(ParcelUuid(service))
                    ?: false
        }
    }

    override fun writeData(service: UUID?, characteristic: UUID?, data: ByteArray?, connectableDeviceWriteCallback: ConnectableDeviceWriteCallback) {
        checkMainThread()

        try {
            tryToWriteData(service, characteristic, data)
            connectableDeviceWriteCallback.onWrite(service, characteristic)
        } catch (e: Exception) {
            Logger.warn { "writeData error: ${e.message}" }
            connectableDeviceWriteCallback.onFailed(service, characteristic)
        }
    }

    private fun tryToWriteData(service: UUID?, characteristic: UUID?, data: ByteArray?) {
        val bluetoothGattCharacteristic = getBluetoothGattCharacteristic(service, characteristic)
        setCharacteristicValueAndWriteType(bluetoothGattCharacteristic, data)
        writeCharacteristic(bluetoothGattCharacteristic)
    }

    private fun getBluetoothGattCharacteristic(service: UUID?, characteristic: UUID?): BluetoothGattCharacteristic {
        return bluetoothGatt!!.getService(service)!!.getCharacteristic(characteristic)
    }

    private fun setCharacteristicValueAndWriteType(characteristic: BluetoothGattCharacteristic, data: ByteArray?) {
        characteristic.value = data
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic) {
        if (!bluetoothGatt!!.writeCharacteristic(characteristic)) {
            throw Exception("Writing to characteristic failed")
        }
    }

    @Loggable
    override fun subscribe(service: UUID?, characteristic: UUID?, connectableDeviceSubscriptionCallback: ConnectableDeviceSubscriptionCallback) {
        checkMainThread()

        try {
            Logger.debug { "available services=" + bluetoothGatt?.services?.map { it.uuid } }
            tryToSubscribe(service, characteristic)
            connectableDeviceSubscriptionCallback.onSuccess(service, characteristic)
        } catch (e: Exception) {
            e.message?.let { Logger.error { "subscribe error: $it" } } ?: e.printStackTrace()
            connectableDeviceSubscriptionCallback.onFail(service, characteristic)
        }
    }

    @Loggable
    override fun unsubscribe(service: UUID?, characteristic: UUID?, capableDeviceUnsubscriptionCallback: ConnectableDeviceUnsubscriptionCallback) {
        checkMainThread()

        try {
            Logger.debug { "available services=${bluetoothGatt?.services?.map { it.uuid }}" }
            tryToUnsubscribe(service, characteristic)
            capableDeviceUnsubscriptionCallback.onSuccess(service, characteristic)
        } catch (e: Exception) {
            e.message?.let { Logger.error { "subscribe error: $it" } } ?: e.printStackTrace()
            capableDeviceUnsubscriptionCallback.onFail(service, characteristic)
        }
    }

    private fun tryToSubscribe(service: UUID?, characteristic: UUID?) {
        val bluetoothGattCharacteristic =
                try {
                    getBluetoothGattCharacteristic(service, characteristic)
                } catch (e: NullPointerException) {
                    throw NullPointerException("Service not available")
                }
        setCharacteristicNotification(bluetoothGattCharacteristic, true)
        val bluetoothGattDescriptor = getBluetoothGattDescriptor(bluetoothGattCharacteristic)
                .apply { value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE }
        writeDescriptor(bluetoothGattDescriptor)
    }

    private fun tryToUnsubscribe(service: UUID?, characteristic: UUID?) {
        val bluetoothGattCharacteristic =
                try {
                    getBluetoothGattCharacteristic(service, characteristic)
                } catch (e: NullPointerException) {
                    throw NullPointerException("Service not available")
                }
        setCharacteristicNotification(bluetoothGattCharacteristic, false)
        val bluetoothGattDescriptor = getBluetoothGattDescriptor(bluetoothGattCharacteristic)
                .apply { value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE }
        writeDescriptor(bluetoothGattDescriptor)
    }

    @SuppressLint("MissingPermission")
    private fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        if (!bluetoothGatt!!.setCharacteristicNotification(characteristic, enable)) {
            throw Exception("Set characteristic notification failed: characteristic=$characteristic enable=$enable")
        }
    }

    private fun getBluetoothGattDescriptor(characteristic: BluetoothGattCharacteristic): BluetoothGattDescriptor {
        return characteristic.descriptors.takeIf { it.size == 1 }?.first()
                ?: throw Exception("Descriptors size (${characteristic.descriptors.size}) different than expected: 1")
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptor(descriptor: BluetoothGattDescriptor) {
        if (!bluetoothGatt!!.writeDescriptor(descriptor)) {
            throw Exception("Writing to descriptor failed")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BluetoothConnectableDevice

        return (scanResult == other.scanResult)
    }

    override fun hashCode(): Int {
        return scanResult.hashCode()
    }

    fun checkMainThread() {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw RuntimeException("Not on the main thread.")
        }
    }

    interface DeviceConnectionCallback {
        fun onConnectedToDevice()
        fun onDisconnectedFromDevice()
    }
}
