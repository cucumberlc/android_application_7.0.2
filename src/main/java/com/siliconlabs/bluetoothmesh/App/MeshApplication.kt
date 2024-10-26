/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.google.gson.Gson
import com.siliconlab.bluetoothmesh.adk.BluetoothMesh
import com.siliconlab.bluetoothmesh.adk.configuration.BluetoothMeshConfiguration
import com.siliconlab.bluetoothmesh.adk.errors.BluetoothMeshInitializationError
import com.siliconlab.bluetoothmesh.adk.model_control.LocalModelTransmission
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonMesh
import com.siliconlabs.bluetoothmesh.App.Logic.ExportImport.JsonExporter
import com.siliconlabs.bluetoothmesh.App.Logic.ExportImport.JsonImporter
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Models.MeshNetworkManager
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Utils.ExportImportTestFilter
import com.siliconlabs.bluetoothmesh.App.Utils.FileLogger
import com.siliconlabs.bluetoothmesh.App.VendorModelBuilder.VendorModelHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import org.tinylog.kotlin.Logger

@HiltAndroidApp
class MeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        FileLogger.setup(applicationContext)
        appContext = applicationContext
        initializeMesh()
    }

    private fun initializeMesh() {
        BluetoothMesh.initialize(
            applicationContext,
            configuration,
        ).onFailure {
            if (it is BluetoothMeshInitializationError.DatabaseCorrupted) {
                println("----***********************-------")
                println("----***********************-------")
                println("----***********************-------")
                BluetoothMesh.deinitialize().onFailure {
                    Logger.error { it.toString() }
                    return
                }
                BluetoothMesh.initialize(applicationContext, configuration).onFailure {
                    Logger.error { it.toString() }
                    return
                }
            } else {
                Logger.error { it.toString() }
                return
            }
        }
        LocalModelTransmission.setPduMaxSize(CURRENTLY_SUPPORTED_EFR_MAXIMUM_NETWORK_PDU_SIZE)
        var exportAndImportDb = false
        if(DeviceFunctionalityDb.isFirstTimeLaunch()) {
            MeshNetworkManager.createDefaultStructure()
        }
        else if(BluetoothMesh.network.subnets.flatMap { x -> x.nodes }.isNotEmpty())
        {
            val meshNode = MeshNodeManager.getMeshNode(
                BluetoothMesh.network.subnets.flatMap { x -> x.nodes }.first())
            if(meshNode.functionality == DeviceFunctionality.FUNCTIONALITY.LightLCServer)
            {
                val jsonString = JsonExporter().exportJson()
                val exportedJson = Gson().fromJson(jsonString, JsonMesh::class.java)
                JsonImporter(exportedJson).import()
                exportAndImportDb = true
            }
        }
        Log.i(ExportImportTestFilter, "exported and imported mesh database = $exportAndImportDb")
    }

    companion object {
        lateinit var appContext: Context
            @VisibleForTesting
            internal set

        private const val CURRENTLY_SUPPORTED_EFR_MAXIMUM_NETWORK_PDU_SIZE = 227
        val configuration = BluetoothMeshConfiguration()

        //Vendor Model initialization
        //val configuration = BluetoothMeshConfiguration(provisionerVendorModels = VendorModelHelper().getSupportedVendorModelsList())

        val mainScope = MainScope()
    }
}