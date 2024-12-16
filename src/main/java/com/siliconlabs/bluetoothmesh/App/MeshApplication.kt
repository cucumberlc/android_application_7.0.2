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
        var printedNetworkInfo = false
        if(DeviceFunctionalityDb.isFirstTimeLaunch()) {
            MeshNetworkManager.createDefaultStructure()
            val b = 0
            /*val jsonString = getExportedString()
            val exportedJson = Gson().fromJson(jsonString, JsonMesh::class.java)
            JsonImporter(exportedJson).import()*/
        }
        else if(BluetoothMesh.network.subnets.flatMap { x -> x.nodes }.isNotEmpty())
        {
            val meshNode = MeshNodeManager.getMeshNode(
                BluetoothMesh.network.subnets.flatMap { x -> x.nodes }.first())
            val jsonString = JsonExporter().exportJson()
            val exportedJson = Gson().fromJson(jsonString, JsonMesh::class.java)
            if(meshNode.functionality == DeviceFunctionality.FUNCTIONALITY.LightLCServer)
            {
                Log.i(ExportImportTestFilter, "network info before export / import test:")
                printNetworkInfo()
                JsonImporter(exportedJson).import()
                exportAndImportDb = true
                Log.i(ExportImportTestFilter, "network info after export / import test:")
                printNetworkInfo()
                printedNetworkInfo = true
            }
        }
        Log.i(ExportImportTestFilter, "exported and imported mesh database = $exportAndImportDb")
        if(!printedNetworkInfo)
        {
            printNetworkInfo()
        }
    }

    private fun getExportedString() : String
    {
        return "{\"appKeys\":[{\"boundNetKey\":0,\"index\":0,\"key\":\"6a5305668b9943d89b380207723fa20c\"},{\"boundNetKey\":1,\"index\":1,\"key\":\"3bb6e7664e3b4778a3e188af71c85c99\"}],\"groups\":[{\"address\":\"c000\",\"name\":\"\"}],\"id\":\"https://www.bluetooth.com/specifications/assigned-numbers/mesh-profile/cdb-schema.json#\",\"meshName\":\"Mesh Test\",\"meshUUID\":\"34b5fa6a642e4325b6f898f2b43ef10c\",\"netKeys\":[{\"index\":0,\"key\":\"0a9e5d4b2fdb4bdcbfdd96ba823a1662\",\"name\":\"\",\"phase\":0,\"timestamp\":\"1970-01-01T00:00:00Z\"},{\"index\":1,\"key\":\"ec6ca350596e4523b7ee0eda0090e6eb\",\"name\":\"\",\"phase\":0,\"timestamp\":\"1970-01-01T00:00:00Z\"}],\"nodes\":[],\"provisioners\":[],\"scenes\":[],\"\$schema\":\"http://json-schema.org/draft-04/schema#\",\"timestamp\":\"2024-12-16T09:09:42Z\",\"version\":\"7.0.2.0\"}"
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

    private fun printNetworkInfo()
    {
        try
        {
            Log.i(ExportImportTestFilter, "network key refresh phase = ${BluetoothMesh.network.subnetSecurity.keyRefreshPhase}")
            Log.i(ExportImportTestFilter, "network key refresh timestamp = ${BluetoothMesh.network.subnetSecurity.keyRefreshTimestamp}")
        }
        catch (ex: Exception)
        {
            Log.i(ExportImportTestFilter, "network error -> ${ex.message}")
        }
        Log.i(ExportImportTestFilter, "network version = ${BluetoothMesh.network.version}")
        Log.i(ExportImportTestFilter, "network uuid = ${BluetoothMesh.network.uuid}")
        Log.i(ExportImportTestFilter, "network name = ${BluetoothMesh.network.name}")
        BluetoothMesh.network.subnets.forEach { subnet ->
            Log.i(ExportImportTestFilter, "no of groups in subnet ${subnet.name} = ${BluetoothMesh.network.groups.size}")
        }
        BluetoothMesh.network.groups.forEach { group ->
            Log.i(ExportImportTestFilter, "group -> address = ${group.address.value}, name = ${group.name}")// is bound to subnet ${group.subnet.name}")
        }
        BluetoothMesh.network.subnets.forEach { subnet ->
            var msg = "subnet -> name = ${subnet.name}, network key = ${subnet.netKey.key.contentToString()}, app keys:"
            subnet.appKeys.forEach { appKey ->
                msg += "\n\tapp key = ${appKey.key.contentToString()}"
            }
            subnet.nodes.forEach { node ->
                msg += "\n\tnode -> name = ${node.name}, uuid = ${node.uuid}"
                msg += "\n\tgroups bound to node information:"
                for (group in node.groups) {
                    msg += "\n\t\tgroup -> name = ${group.name}, sig models bound to group:"
                    val modelsBoundToGroup = group.sigModel
                    modelsBoundToGroup.forEach { sigModel ->
                        msg += "\n\t\t\t${sigModel.name} -> id = ${sigModel.identifier.toInt()}, "
                    }
                    if(modelsBoundToGroup.isEmpty())
                    {
                        msg += "\n\t\t\t(no sig models found bound to the group)"
                    }
                }
                msg += "\n\tbound app keys information:"
                for(appKey in node.boundAppKeys)
                {
                    msg += "\n\t\tapp key = ${appKey.key.contentToString()}"
                }
                msg += "\n\telements information:"
                node.elements.forEach { element ->
                    msg += "\n\t\telement info: index = ${element?.index}, address = ${element?.address?.value}, name = ${element?.name}," +
                            " bound node = ${element?.node?.name}, sig model info:"
                    element?.sigModels?.forEach { sigModel ->
                        msg += "\n\t\t\tsigModel -> name = ${sigModel.name}, id = ${sigModel.modelIdentifier.id}, " +
                                "bound app key = ${sigModel.boundAppKeys.firstOrNull()?.key?.contentToString()}, bound groups:"
                        sigModel.boundGroup.forEach { group ->
                            msg += "\n\t\t\t\tgroup -> name = ${group.name}, address = ${group.address.value}, virtual label = ${group.address.virtualLabel}"
                        }
                        if(sigModel.modelSettings.publish?.address != null)
                        {
                            msg += "\n\t\t\t\tpublish setting: ${sigModel.modelSettings.publish?.address?.value}"
                        }
                        if(sigModel.modelSettings.subscriptions.any())
                        {
                            msg += "\n\t\t\t\tsubscribe settings: "
                            sigModel.modelSettings.subscriptions.forEach { subs ->
                                msg += "\n\t\t\t\t\tsubscription address = ${subs.value}"
                            }
                        }
                    }
                }
            }
            Log.i(ExportImportTestFilter, msg)
        }
    }
}