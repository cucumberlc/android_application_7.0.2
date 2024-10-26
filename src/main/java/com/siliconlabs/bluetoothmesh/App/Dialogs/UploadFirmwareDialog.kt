/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Dialogs

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogUploadFirmwareBinding

class UploadFirmwareDialog : DialogFragment(R.layout.dialog_upload_firmware) {
    lateinit var uploadFirmwareListener: UploadFirmwareListener
    var uploadFirmwareDoneListener: OnUploadFirmwareDoneListener? = null

    private val layout by viewBinding(DialogUploadFirmwareBinding::bind)

    private val colorBlue: Int by lazy {
        ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
    }
    private val colorGreen: Int by lazy {
        ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
    }
    private val colorRed: Int by lazy {
        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
    }

    interface OnUploadFirmwareDoneListener {
        fun onUploadFirmwareDone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialog?.apply {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.WRAP_CONTENT
            window?.setLayout(width, height)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpDoneAndCancelButtonListeners()
        uploadFirmwareListener.startUploadProcess()
    }

    private fun setUpDoneAndCancelButtonListeners() {
        layout.apply {
            buttonCancelUpload.setOnClickListener {
                uploadFirmwareListener.cancelUploadProcess()
            }
            buttonUploadDone.setOnClickListener {
                uploadFirmwareDoneListener?.onUploadFirmwareDone()
                dismiss()
            }
        }
    }

    fun uploadInProgress(progress: Int) {
        layout.apply {
            tvUploadProgress.apply {
                text = "$progress%"
                setTextColor(colorBlue)
            }
            tvUploadErrorName.isGone = true
            buttonCancelUpload.text = getString(R.string.dialog_negative_cancel)
        }
    }

    fun uploadFailed(error: String) {
        layout.apply {
            tvUploadProgress.apply {
                text = getString(R.string.upload_failed)
                setTextColor(colorRed)
            }
            tvUploadErrorName.apply {
                isVisible = true
                text = error
            }
            buttonCancelUpload.text = getString(R.string.upload_failed_back_to_previous_fragment)
        }
    }

    fun uploadSuccess() {
        val hundredPercent = "100%"
        layout.apply {
            tvUploadProgress.apply {
                text = hundredPercent
                setTextColor(colorGreen)
            }
            tvUploadErrorName.isGone = true
            buttonCancelUpload.isGone = true
            buttonUploadDone.isVisible = true
        }
    }

    fun handleCancelResult(cancelled: Boolean) {
        if (!cancelled)
            notifyCancelFailed()
        dismiss()
    }

    private fun notifyCancelFailed() {
        MeshToast.show(
            requireContext(),
            R.string.upload_cancel_failed,
            duration = Toast.LENGTH_LONG
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uploadFirmwareListener.cleanup()
    }

    interface UploadFirmwareListener {
        fun startUploadProcess()
        fun cancelUploadProcess()
        fun cleanup()
    }
}