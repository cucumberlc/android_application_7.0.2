/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Navigation

import android.os.Parcelable
import androidx.annotation.Keep
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

// wrappers for destinations so parcelable objects can be provided into navigation actions as arguments

@Keep
sealed interface MeshAppDestination : Parcelable {
    val data: MeshAppNavigationData

    @Parcelize
    object OfNetwork : MeshAppDestination {
        @IgnoredOnParcel
        override val data = MeshAppNavigationData.NULL
    }

    @Keep
    @Parcelize
    class OfSubnet private constructor(override val data: MeshAppNavigationData) :
        MeshAppDestination {
        constructor(subnet: Subnet) : this(MeshAppNavigationData(subnet))

        val subnet
            get() = data.subnet!!
    }

    @Keep
    @Parcelize
    class OfAppKey private constructor(override val data: MeshAppNavigationData) :
        MeshAppDestination {
        constructor(appKey: AppKey) : this(MeshAppNavigationData(appKey))

        val appKey
            get() = data.appKey!!
        val subnet
            get() = data.subnet!!
    }

    @Keep
    @Parcelize
    class OfNode private constructor(override val data: MeshAppNavigationData) :
        MeshAppDestination {
        constructor(node: Node, subnet: Subnet) : this(MeshAppNavigationData(node, subnet))

        val node
            get() = data.node!!
        val subnet
            get() = data.subnet!!
    }

    @Keep
    @Parcelize
    class OfNodeWithAppKey private constructor(override val data: MeshAppNavigationData) :
        MeshAppDestination {
        constructor(node: Node, appKey: AppKey) : this(MeshAppNavigationData(node, appKey))

        val node
            get() = data.node!!
        val appKey
            get() = data.appKey!!
        val subnet
            get() = data.subnet!!
    }
}

fun Subnet.toNavArg() = MeshAppDestination.OfSubnet(this)
fun AppKey.toNavArg() = MeshAppDestination.OfAppKey(this)
fun Node.toNavArg(subnet: Subnet = subnets.first()) = MeshAppDestination.OfNode(this, subnet)
fun Node.toNavArg(appKey: AppKey) = MeshAppDestination.OfNodeWithAppKey(this, appKey)