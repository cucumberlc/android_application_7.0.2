/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions

import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.data_model.model.SigModel
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode

fun MeshNode.findSigModel(modelIdentifier: ModelIdentifier): SigModel? =
    this.node.findSigModel(modelIdentifier)

fun Node.findSigModel(modelIdentifier: ModelIdentifier): SigModel? =
    this.elements.flatMap { it!!.sigModels }
        .find { it.modelIdentifier == modelIdentifier }
