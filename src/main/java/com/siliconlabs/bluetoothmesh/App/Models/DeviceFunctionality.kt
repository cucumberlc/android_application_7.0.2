/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.model.SigModel
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node

class DeviceFunctionality {

    enum class FUNCTIONALITY(vararg val model: ModelIdentifier) {
        Unknown,

        //onoff
        OnOff(ModelIdentifier.GenericOnOffServer),
        OnOffClient(ModelIdentifier.GenericOnOffClient),

        //level
        Level(ModelIdentifier.GenericLevelServer),
        LevelClient(ModelIdentifier.GenericLevelClient),

        //lightness
        Lightness(ModelIdentifier.LightLightnessServer),
        LightnessClient(ModelIdentifier.LightLightnessClient),

        //ctl
        CTL(ModelIdentifier.LightCTLServer),
        CTLClient(ModelIdentifier.LightCTLClient),

        //sensor
        SensorServer(ModelIdentifier.SensorServer),
        SensorSetupServer(ModelIdentifier.SensorSetupServer),
        SensorClient(ModelIdentifier.SensorClient),

        //time
        TimeServer(ModelIdentifier.TimeServer),
        TimeClient(ModelIdentifier.TimeClient),

        //lc
        LightLCServer(ModelIdentifier.LightLCServer),
        LightLCClient(ModelIdentifier.LightLCClient),

        //scene
        SceneServer(ModelIdentifier.SceneServer),
        SceneClient(ModelIdentifier.SceneClient),

        //scheduler
        Scheduler(ModelIdentifier.SchedulerServer),

        //distributor
        DistributorServer(ModelIdentifier.DeviceFirmwareUpdateDistributorServer);

        private fun getAdditionalModels(): Set<ModelIdentifier> {
            val additionalModels: Set<ModelIdentifier> = when (this) {
                //onoff
                OnOff -> setOf(ModelIdentifier.LightLightnessServer, ModelIdentifier.SceneServer, ModelIdentifier.SceneSetupServer)
                OnOffClient -> setOf(ModelIdentifier.LightLightnessClient, ModelIdentifier.SceneClient)
                //level
                LevelClient -> setOf(ModelIdentifier.GenericOnOffClient, ModelIdentifier.SceneClient)
                //lightness
                Lightness -> setOf(ModelIdentifier.GenericOnOffServer)
                LightnessClient -> setOf(ModelIdentifier.GenericOnOffClient, ModelIdentifier.SceneClient)
                //ctl
                CTL -> setOf(ModelIdentifier.LightCTLTemperatureServer,
                        ModelIdentifier.GenericOnOffServer, ModelIdentifier.LightLightnessServer)
                CTLClient -> setOf(ModelIdentifier.GenericOnOffClient,
                        ModelIdentifier.LightLightnessClient, ModelIdentifier.SceneClient)
                //lc
                LightLCServer -> setOf(ModelIdentifier.GenericOnOffServer, ModelIdentifier.GenericLevelServer, ModelIdentifier.LightLCSetupServer, ModelIdentifier.SceneServer, ModelIdentifier.SceneSetupServer)
                LightLCClient -> setOf(ModelIdentifier.SceneClient)
                //scheduler
                Scheduler -> setOf(ModelIdentifier.SchedulerSetupServer, ModelIdentifier.TimeServer,
                        ModelIdentifier.TimeSetupServer)
                //time
                TimeServer -> setOf(ModelIdentifier.TimeSetupServer)
                //distributor
                DistributorServer -> setOf(ModelIdentifier.BlobTransferServer,
                        ModelIdentifier.BlobTransferClient,
                        ModelIdentifier.DeviceFirmwareUpdateClient)
                else -> emptySet()
            }
            val sceneModels = setOf(ModelIdentifier.SceneServer, ModelIdentifier.SceneSetupServer)
            val updatableNodeModels = setOf(ModelIdentifier.DeviceFirmwareUpdateServer,
                    ModelIdentifier.BlobTransferServer)

            return when (this) {
                Unknown -> {
                    additionalModels.plus(updatableNodeModels)
                }
                else -> {
                    additionalModels.plus(sceneModels).plus(updatableNodeModels)
                }
            }
        }

        fun getAllModels(): Set<ModelIdentifier> {
            val models = mutableSetOf<ModelIdentifier>()
            models.addAll(model)
            models.addAll(getAdditionalModels())

            return models
        }

        companion object {
            fun fromId(id: Int): FUNCTIONALITY? {
                return ModelIdentifier.values().find { it.id == id }?.let { modelIdentifier ->
                    values().find { it.model.contains(modelIdentifier) }
                }
            }
        }
    }

    data class FunctionalityNamed(val functionality: FUNCTIONALITY, val functionalityName: String)

    companion object {

        fun getFunctionalitiesNamed(node: Node): Set<FunctionalityNamed> {
            return mutableSetOf(
                    FunctionalityNamed(FUNCTIONALITY.Unknown, "")
            ).apply {
                addAll(node.elements.flatMap { it!!.sigModels }
                        .mapNotNull { sigModel ->
                            FUNCTIONALITY.fromId(sigModel.modelIdentifier.id)?.let {
                                FunctionalityNamed(it, sigModel.name)
                            }
                        })
            }
        }

        fun getFunctionalitiesNameDistributor(node: Node): List<FunctionalityNamed> {
            return mutableListOf(
                FunctionalityNamed(FUNCTIONALITY.Unknown, "")
            ).apply {
                addAll(node.elements.flatMap { it!!.sigModels }
                    .mapNotNull { sigModel ->
                        FUNCTIONALITY.fromId(sigModel.modelIdentifier.id)?.let {
                            FunctionalityNamed(it, sigModel.name)
                        }
                    })
            }
        }

        /*fun getSigModels(node: Node, functionality: FUNCTIONALITY): Set<SigModel> {
            val supportedModelIds = functionality.getAllModels()

            return node.elements.flatMap { it.sigModels }
                .filter { sigModel -> supportedModelIds.any { it.id == sigModel.modelIdentifier.id } }
                    .toSet()
        }*/

        private fun getSigModelsOldVersion(node: Node, functionality: FUNCTIONALITY): Set<SigModel> {
            val supportedModelIds = functionality.getAllModels()

            val res = node.elements.flatMap { it!!.sigModels }
                .filter { sigModel -> supportedModelIds.any { it.id == sigModel.modelIdentifier.id } }
                .toSet()
            for (sigModel in res) {
                // Accessing properties of the SigModel
                val modelId = sigModel.modelIdentifier.id
                val modelName = sigModel.name
                val elementAddress = sigModel.element.address
                println("DFU: $modelId, Model Name: $modelName , Address : $elementAddress")
            }
            return res
        }


        private fun getSigModelsNewVersion(node: Node, functionality: FUNCTIONALITY): List<SigModel> {
            val supportedModelIds = functionality.getAllModels()
            println("DFU: $supportedModelIds")

            val filterNodes = node.elements.flatMap { it!!.sigModels }
                .filter { sigModel -> supportedModelIds.any { it.id == sigModel.modelIdentifier.id } }

            val filterNode = mutableListOf<SigModel>()
            filterNode.addAll(filterNodes)

            println("DFU 1 ==${filterNode}")
            // filterNode.removeAt(0)

            println("DFU 1 after==${filterNode}")
            for (sigModel in filterNode) {
                // Accessing properties of the SigModel
                val modelId = sigModel.modelIdentifier.id
                val modelName = sigModel.name
                val elementAddress = sigModel.element.address

                // Print information about each SigModel
                println("DFU: $modelId, Model Name: $modelName, Address: $elementAddress")
            }
            return filterNode
        }

        fun getSigModels(node: Node, functionality: FUNCTIONALITY): Collection<SigModel> {
            if (node.elements.size > 1 && functionality == FUNCTIONALITY.DistributorServer) {
                return getSigModelsNewVersion(node, functionality)
            } else {
                return getSigModelsOldVersion(node, functionality)
            }
        }
    }
}