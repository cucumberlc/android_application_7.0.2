/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Database

import android.content.Context
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality.FUNCTIONALITY
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality.FUNCTIONALITY.Unknown
import java.util.UUID

object DeviceFunctionalityDb {
    private val sharedPreferences = MeshApplication.appContext.getSharedPreferences("nodeFunctionality_v2", Context.MODE_PRIVATE)

    fun get(node: Node) = FUNCTIONALITY.values().find {
        it.name == sharedPreferences.getString(node.uuid.toString(), Unknown.name)
    } ?: Unknown

    fun save(meshNode: MeshNode) {
        sharedPreferences.edit().let {
            it.putString(meshNode.node.uuid.toString(), meshNode.functionality.name)
            it.apply()
        }
    }

    fun remove(meshNode: MeshNode) {
        sharedPreferences.edit().let {
            it.remove(meshNode.node.uuid.toString())
            it.apply()
        }
    }

    fun remove(nodeUUID: UUID){
        sharedPreferences.edit().let {
            it.remove(nodeUUID.toString())
            it.apply()
        }
    }

    fun saveTab(isNLCTab: Boolean) {
        sharedPreferences.edit().let {
            it.putBoolean("NLC_TAB_SELECTED", isNLCTab)
            it.apply()
        }
    }
    fun getTab() : Boolean{
        return sharedPreferences.getBoolean("NLC_TAB_SELECTED", false)
    }

    fun saveFirstTimeLaunch(isFirstLaunch: Boolean) {
        sharedPreferences.edit().let {
            it.putBoolean("FIRST_LAUNCH", isFirstLaunch)
            it.apply()
        }
    }
    fun isFirstTimeLaunch() : Boolean {
        return sharedPreferences.getBoolean("FIRST_LAUNCH", true)
    }
}