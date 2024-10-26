/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppDestination
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppNavigationData
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.getParcelableCompat

val Fragment.meshNavArgs : MeshAppNavigationData?
    get() = arguments?.getParcelableCompat<MeshAppDestination>(FragmentsModule.NAV_KEY)?.data

fun <T : Fragment> T.withMeshNavArg(data: MeshAppDestination): T {
    check(lifecycle.currentState == Lifecycle.State.INITIALIZED) {
        "Arguments can only be set while initializing"
    }
    arguments = (arguments ?: Bundle()).apply {
        putParcelable(FragmentsModule.NAV_KEY, data)
    }
    return this
}