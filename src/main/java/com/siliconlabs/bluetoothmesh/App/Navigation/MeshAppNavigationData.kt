/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Navigation

import android.os.Parcel
import android.os.ParcelUuid
import android.os.Parcelable
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.data_model.network.Network
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.readParcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.util.UUID

@Parcelize
class MeshAppNavigationData private constructor(
    val subnet: Subnet?,
    val appKey: AppKey?,
    val node: Node?,
) : Parcelable {
    constructor(subnet: Subnet) : this(subnet, null, null)
    constructor(appKey: AppKey) : this(appKey.subnet, appKey, null)
    constructor(node: Node, subnet: Subnet) : this(subnet, null, node)
    constructor(node: Node, appKey: AppKey) : this(appKey.subnet, appKey, node)

    companion object : Parceler<MeshAppNavigationData> {
        val NULL = MeshAppNavigationData(null, null, null)
        override fun create(parcel: Parcel): MeshAppNavigationData {
            return parcel.readParcelable<ParcelImpl>()!!.run {
                val subnet = getSubnet(BluetoothMesh.network)
                val group = getAppKey(subnet)
                val node = getNode(subnet)
                MeshAppNavigationData(subnet, group, node)
            }
        }

        override fun MeshAppNavigationData.write(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(ParcelImpl(this), flags)
        }

        private fun Subnet.navKey() = netKey.index
        private fun AppKey.navKey() = index
        private fun Node.navKey() = uuid

        @Parcelize
        @TypeParceler<UUID, UUIDToParcelParceler>
        private class ParcelImpl(
            val subnetKeyIndex: Int?,
            val groupAddress: Int?,
            val nodeUUID: UUID?,
        ) : Parcelable {
            constructor(data: MeshAppNavigationData) : this(
                data.subnet?.navKey(),
                data.appKey?.navKey(),
                data.node?.navKey()
            ) {
                // see if navkeys succeeded
                assertNavKeys(data.subnet, subnetKeyIndex)
                assertNavKeys(data.appKey, groupAddress)
                assertNavKeys(data.node, nodeUUID)
            }

            fun getSubnet(network: Network?) = network?.run {
                if (subnetKeyIndex == null) return@run null
                subnets.first { it.navKey() == subnetKeyIndex }
            }

            fun getAppKey(subnet: Subnet?) = subnet?.run {
                if (groupAddress == null) return@run null
                appKeys.first { it.navKey() == groupAddress }
            }

            fun getNode(subnet: Subnet?) = subnet?.run {
                if (nodeUUID == null) return@run null
                nodes.first { it.navKey() == nodeUUID }
            }

            private fun assertNavKeys(src: Any?, key: Any?) {
                assert(src != null && key != null || src == key) { "navKey creation failed: $this" }
            }
        }
    }

    override fun toString(): String {
        return """
            ${MeshAppNavigationData::class.java.name} @ ${System.identityHashCode(this)}:
            subnet : $subnet
            appKey : $appKey
            node   : $node
        """.trimIndent()
    }

    object UUIDToParcelParceler : Parceler<UUID> {
        override fun create(parcel: Parcel): UUID {
            return parcel.readParcelable<ParcelUuid>()!!.uuid
        }

        override fun UUID.write(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(ParcelUuid(this), flags)
        }
    }
}