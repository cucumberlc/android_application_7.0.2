/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Views

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import androidx.appcompat.widget.AppCompatImageView
import com.siliconlabs.bluetoothmesh.R


class RefreshNodeButton : AppCompatImageView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    fun startRefresh() {
        post {
            val rotateAnimation = AnimationUtils.loadAnimation(context, R.anim.rotate)
            startAnimation(rotateAnimation)
            alpha = 0.5f
        }
    }

    fun stopRefresh() {
        post {
            clearAnimation()
            alpha = 1f
        }
    }

}