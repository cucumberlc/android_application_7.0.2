/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Subnet

import com.siliconlab.bluetoothmesh.adk.errors.ConnectionError
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface SubnetView: PresenterView {
    fun showErrorToast(connectionError: ConnectionError)

    fun setMeshIconState(iconState: MeshIconState)

    fun setActionBarTitle(title: String)

    enum class MeshIconState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}