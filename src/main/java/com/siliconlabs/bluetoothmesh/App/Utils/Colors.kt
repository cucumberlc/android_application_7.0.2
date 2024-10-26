/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

import androidx.core.content.ContextCompat
import com.siliconlabs.bluetoothmesh.App.MeshApplication

object Colors {
    val blue by lazy { ContextCompat.getColor(MeshApplication.appContext, android.R.color.holo_blue_bright) }
    val green by lazy { ContextCompat.getColor(MeshApplication.appContext, android.R.color.holo_green_light) }
    val red by lazy { ContextCompat.getColor(MeshApplication.appContext, android.R.color.holo_red_light) }
    val white by lazy { ContextCompat.getColor(MeshApplication.appContext, android.R.color.white) }
    val gray by lazy { ContextCompat.getColor(MeshApplication.appContext, android.R.color.darker_gray) }
}