/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.TimeControl

import com.siliconlab.bluetoothmesh.adk.errors.StackError
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.PresenterView

interface TimeControlView : PresenterView {
    fun showToast(message: String)
    fun showToast(stackError: StackError)
    fun notifyItemChanged(item: MeshNode)
}