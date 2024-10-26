/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.data_model.address.IntegerAddress
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlab.bluetoothmesh.adk.provisioning.OobInformation
import java.util.UUID

sealed class DeviceToProvision {
    abstract val name: String
    abstract val subnet: Subnet
    abstract val uuid: UUID
    abstract val oobInformation: Set<OobInformation>

    class Direct(
            val device: BluetoothConnectableDevice,
            override var name: String,
            override var subnet: Subnet
    ) : DeviceToProvision() {
        override val uuid: UUID
            get() = device.uuid!!
        override val oobInformation: Set<OobInformation>
            get() = device.oobInformation

        override fun equals(other: Any?): Boolean {
            return (other as? Direct)?.uuid == uuid
        }

        override fun hashCode() = uuid.hashCode()
    }

    class Remote(
            val device: UnprovisionedDevice,
            override val subnet: Subnet,
            val serverAddress: IntegerAddress,
            val netKeyIndex: Int
    ) : DeviceToProvision() {
        override val name: String
            get() = "Device ${device.uuid}"
        override val uuid: UUID
            get() = device.uuid
        override val oobInformation: Set<OobInformation>
            get() = device.oobInformation

        override fun equals(other: Any?): Boolean {
            return (other as? Remote)?.uuid == uuid
        }

        override fun hashCode() = uuid.hashCode()
    }
}