/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Standalone

import com.siliconlab.bluetoothmesh.adk.functionality_control.standaloneupdater.Phase
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.App.Utils.Colors
import com.siliconlabs.bluetoothmesh.R
import kotlin.math.roundToInt

class StandaloneUpdaterProgressSection {
    private val defaultText = MeshApplication.appContext.getString(R.string.standalone_progress_default)
    private var uploadText = ColoredText(defaultText, Colors.white)
    private var verificationText = ColoredText(defaultText, Colors.white)
    private var applyingText = ColoredText(defaultText, Colors.white)

    fun getColoredTexts(phase: Phase, lastPhase: Phase, progress: Double = 0.0): Triple<ColoredText, ColoredText, ColoredText> {
        when (phase) {
            Phase.Idle -> {
                uploadText = ColoredText(defaultText, Colors.white)
                verificationText = ColoredText(defaultText, Colors.white)
                applyingText = ColoredText(defaultText, Colors.white)
            }
            Phase.StartingUpdate -> {
                uploadText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_starting), Colors.green)
                verificationText = ColoredText(defaultText, Colors.white)
                applyingText = ColoredText(defaultText, Colors.white)
            }
            Phase.TransferringImage -> {
                val formattedProgress = ((1000 * progress).roundToInt() / 10.0)
                uploadText = ColoredText("${formattedProgress}%", Colors.blue)
                verificationText = ColoredText(defaultText, Colors.white)
                applyingText = ColoredText(defaultText, Colors.white)
            }
            Phase.CheckingVerification -> {
                uploadText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                verificationText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_in_progress), Colors.blue)
                applyingText = ColoredText(defaultText, Colors.white)
            }
            Phase.ApplyingUpdate -> {
                uploadText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                verificationText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                applyingText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_in_progress), Colors.blue)
            }
            Phase.CheckingUpdateResult -> {
                uploadText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                verificationText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                applyingText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_checking_result), Colors.blue)
            }
            Phase.Completed -> {
                uploadText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                verificationText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
                applyingText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_done), Colors.green)
            }
            Phase.Failed -> {
                when (lastPhase) {
                    Phase.Idle, Phase.StartingUpdate, Phase.TransferringImage -> {
                        uploadText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_failed), Colors.red)
                    }
                    Phase.CheckingVerification -> {
                        verificationText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_failed), Colors.red)
                    }
                    Phase.ApplyingUpdate, Phase.CheckingUpdateResult -> {
                        applyingText = ColoredText(MeshApplication.appContext.getString(R.string.standalone_progress_failed), Colors.red)
                    }
                    else -> {}
                }
            }
            else -> {}
        }
        return Triple(uploadText, verificationText, applyingText)
    }

    data class ColoredText(
            val text: String,
            val color: Int
    )
}