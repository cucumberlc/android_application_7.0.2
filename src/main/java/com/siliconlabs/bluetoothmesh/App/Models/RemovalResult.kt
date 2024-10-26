/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Models

import com.siliconlab.bluetoothmesh.adk.data_model.node.Node

sealed interface RemovalResult {
    object Success : RemovalResult
    class Failure(val failedNodes: List<Node>) : RemovalResult
}