/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App

import android.app.Application
import android.content.Context
import android.os.Environment
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
import java.io.File

@HiltAndroidApp
class MeshApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        FileLogger.setup(applicationContext)
        appContext = applicationContext
        initializeMesh()
    }

    private fun initializeMesh() {
        /*Log.i(ExportImportTestFilter, "cache files before initialising mesh:\n")
        applicationContext.cacheDir.listFiles().forEach { x ->
            Log.i(ExportImportTestFilter, x.name + "\n")
        }
        Log.i(ExportImportTestFilter, "data directory files before initialising mesh:\n")
        for (listFile in applicationContext.dataDir.listFiles()) {
            Log.i(ExportImportTestFilter, listFile.name + "\n")
        }
        val dbList1 = applicationContext.databaseList()
        Log.i(ExportImportTestFilter, "db files before initialising mesh:\n")
        dbList1.forEach { x ->
            Log.i(ExportImportTestFilter, x.toString() + "\n")
        }*/
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
        /*Log.i(ExportImportTestFilter, "cache files after initialising mesh:\n")
        applicationContext.cacheDir.listFiles()?.let { printFiles(it) }
        Log.i(ExportImportTestFilter, "data directory files after initialising mesh:\n")
        applicationContext.dataDir.listFiles()?.let { printFiles(it) }
        val dbList = applicationContext.databaseList()
        Log.i(ExportImportTestFilter, "db files after initialising mesh:\n")
        dbList.forEach { x ->
            Log.i(ExportImportTestFilter, x.toString() + "\n")
        }*/
        /*Log.i(ExportImportTestFilter, "environment -> storage directory:\n")
        Environment.getStorageDirectory(). .listFiles()?.forEach { x ->
            Log.i(ExportImportTestFilter, x.path + ", name = ${x.name}, is directory ${x.isDirectory}" + "\n")
        }*/
        LocalModelTransmission.setPduMaxSize(CURRENTLY_SUPPORTED_EFR_MAXIMUM_NETWORK_PDU_SIZE)
        var exportAndImportDb = false
        if(DeviceFunctionalityDb.isFirstTimeLaunch()) {
            MeshNetworkManager.createDefaultStructure()
        }
        else if(BluetoothMesh.network.subnets.flatMap { x -> x.nodes }.isNotEmpty())
        {
            applicationContext.dataDir.listFiles()
                ?.let { deleteMatchingFile(it, "storage", "databases") }
            applicationContext.dataDir.listFiles()
                ?.let { deleteMatchingFile( it, "database.json", "files")}
            applicationContext.cacheDir.delete()
            BluetoothMesh.database.loadDatabase()
            val jsonString = getJsonString()
            val exportedJson = Gson().fromJson(jsonString, JsonMesh::class.java)
            JsonImporter(exportedJson).import()
            exportAndImportDb = true
            val meshNode = MeshNodeManager.getMeshNode(
                BluetoothMesh.network.subnets.flatMap { x -> x.nodes }.first())
            if(meshNode.functionality == DeviceFunctionality.FUNCTIONALITY.LightLCServer)
            {
                val exportedJson = Gson().fromJson(jsonString, JsonMesh::class.java)
                //JsonImporter(exportedJson).import()
                //exportAndImportDb = true
            }
        }
        else
        {
            applicationContext.dataDir.listFiles()
                ?.let { deleteMatchingFile(it, "storage", "databases") }
            applicationContext.dataDir.listFiles()
                ?.let { deleteMatchingFile( it, "database.json", "files")}
            applicationContext.cacheDir.delete()
            BluetoothMesh.initialize(applicationContext, configuration).onFailure {
                Log.i(ExportImportTestFilter, "failed to initialize network")
            }
            val jsonString = getJsonString()
            val exportedJson = Gson().fromJson(jsonString, JsonMesh::class.java)
            JsonImporter(exportedJson).import()
            exportAndImportDb = true
        }
        Log.i(ExportImportTestFilter, "exported and imported mesh database = $exportAndImportDb")
    }

    private fun printFiles(fileArray: Array<File>)
    {
        fileArray.forEach { x ->
            Log.i(ExportImportTestFilter, x.path + ", name = ${x.name}, is directory ${x.isDirectory}" + "\n")
            if(x.isDirectory)
            {
                x.listFiles()?.let { it1 -> printFiles(it1) }
            }
        }
    }

    private fun deleteMatchingFile(fileArray: Array<File>, fileName: String, parentName: String)
    {
        fileArray.forEach { x ->
            if(x.isDirectory)
            {
                x.listFiles()?.let { deleteMatchingFile(it, fileName, parentName) }
            }
            else
            {
                deleteFile(x, fileName, parentName)
            }
        }
    }

    private fun deleteFile(x: File, fileName: String, parentName: String)
    {
        if(!x.isDirectory && x.name == fileName)
        {
            if(x.parentFile?.name == parentName)
            {
                Log.i(ExportImportTestFilter, "deleting file: '$fileName', parent name = ${x.parent}")
                x.delete()
            }
            else
            {
                Log.i(ExportImportTestFilter, "found file to delete: '$fileName' but parent name incorrect -> ${x.parentFile?.name}")
            }
        }
    }

    private fun getJsonString(): String
    {
        return "{\"appKeys\":[{\"boundNetKey\":0,\"index\":0,\"key\":\"12ce235ba8c947acada5c96f0b2494e6\"},{\"boundNetKey\":1,\"index\":1,\"key\":\"92a98392a0cd4960a1f1668ac12a324a\"}],\"groups\":[{\"address\":\"c000\",\"name\":\"\"}],\"id\":\"https://www.bluetooth.com/specifications/assigned-numbers/mesh-profile/cdb-schema.json#\",\"meshName\":\"Mesh Test\",\"meshUUID\":\"d2bef8d67de2433cb748df8e4a767b8b\",\"netKeys\":[{\"index\":0,\"key\":\"fc5aebc4e0004e4194cda2051cb76088\",\"name\":\"\",\"phase\":0,\"timestamp\":\"1970-01-01T00:00:00Z\"},{\"index\":1,\"key\":\"690b8c1f0dc345d5a475f84ffd6cf03c\",\"name\":\"\",\"phase\":0,\"timestamp\":\"1970-01-01T00:00:00Z\"}],\"nodes\":[{\"UUID\":\"4b1fd810b0394d589500f50985158a35\",\"appKeys\":[{\"index\":1,\"updated\":false}],\"blacklisted\":false,\"cid\":\"0CB7\",\"configComplete\":false,\"crpl\":\"0190\",\"defaultTTL\":5,\"deviceKey\":\"28f022e1dcfbbaebf54281ee1e994a6b\",\"elements\":[{\"index\":0,\"location\":\"2002\",\"models\":[{\"bind\":[],\"modelId\":\"0000\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"0002\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1001\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1302\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1000\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1002\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1004\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1006\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1007\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1300\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1301\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1102\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1100\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1101\",\"subscribe\":[]}],\"name\":\"\"},{\"index\":1,\"location\":\"2003\",\"models\":[{\"bind\":[],\"modelId\":\"1000\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1100\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1101\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1102\",\"subscribe\":[]}],\"name\":\"\"},{\"index\":2,\"location\":\"2004\",\"models\":[{\"bind\":[],\"modelId\":\"1000\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"130F\",\"subscribe\":[]},{\"bind\":[],\"modelId\":\"1310\",\"subscribe\":[]}],\"name\":\"\"}],\"features\":{\"lowPower\":2},\"knownAddresses\":[],\"name\":\"S12BTX\",\"netKeys\":[{\"index\":1,\"updated\":false}],\"pid\":\"0001\",\"security\":\"LOW\",\"unicastAddress\":\"2002\",\"vid\":\"0001\"}],\"provisioners\":[],\"scenes\":[],\"\$schema\":\"http://json-schema.org/draft-04/schema#\",\"timestamp\":\"2025-03-01T15:55:47Z\",\"version\":\"7.0.2.0\"}"
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