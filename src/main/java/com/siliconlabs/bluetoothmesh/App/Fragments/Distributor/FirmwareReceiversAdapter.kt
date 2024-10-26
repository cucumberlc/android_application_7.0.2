/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.siliconlab.bluetoothmesh.adk.functionality_control.blob_transfer.ServerState
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatePhase
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatingNode
import com.siliconlab.bluetoothmesh.adk.functionality_control.firmware_distribution.UpdatingNodeStatus
import com.siliconlabs.bluetoothmesh.R

class FirmwareReceiversAdapter(
        private val updatingNodes: List<UpdatingNode>
) : RecyclerView.Adapter<FirmwareReceiversAdapter.FirmwareReceiversViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirmwareReceiversViewHolder {
        return FirmwareReceiversViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: FirmwareReceiversViewHolder, position: Int) {
        val updatingNode = updatingNodes[position]
        holder.bind(updatingNode)
    }

    override fun getItemCount(): Int =
            updatingNodes.size

    class FirmwareReceiversViewHolder private constructor(private val itemNode: View) : RecyclerView.ViewHolder(itemNode) {
        private val checkBoxNodeToUpdate =
            itemNode.findViewById<CheckBox>(R.id.checkbox_node_to_update)
        private val textViewNodeName = itemNode.findViewById<TextView>(R.id.tv_node_name)
        private val textViewNodeUpdateProgress =
            itemNode.findViewById<TextView>(R.id.tv_node_update_progress)

        private val colorBlue =
            ContextCompat.getColor(itemNode.context, android.R.color.holo_blue_bright)
        private val colorGreen =
            ContextCompat.getColor(itemNode.context, android.R.color.holo_green_light)
        private val colorRed =
            ContextCompat.getColor(itemNode.context, android.R.color.holo_red_light)
        private val colorWhite = ContextCompat.getColor(itemNode.context, android.R.color.white)

        fun bind(updatingNode: UpdatingNode) {
            textViewNodeName.text = updatingNode.node?.name
            checkBoxNodeToUpdate.apply {
                isChecked = true
                isEnabled = false
            }
            textViewNodeUpdateProgress.isVisible = true
            setUpdateProgress(updatingNode)
        }

        private fun setUpdateProgress(updatingNode: UpdatingNode) {
            when (updatingNode.phase) {
                UpdatePhase.IDLE -> setProgressStateAndColor(
                    "${updatingNode.transferProgress}%",
                    colorGreen
                )

                UpdatePhase.TRANSFER_IN_PROGRESS -> setProgressStateAndColor(
                    "${updatingNode.transferProgress}%",
                    colorBlue
                )

                UpdatePhase.TRANSFER_ERROR -> {
                    val blobTransferError =
                        convertTransferCodeIntoString(updatingNode.blobTransferStatus)
                    setProgressStateAndColor(blobTransferError, colorRed)
                }

                UpdatePhase.TRANSFER_CANCELLED -> setProgressStateAndColor(
                    R.string.distribution_cancelled,
                    colorRed
                )

                UpdatePhase.APPLY_FAILED -> {
                    val updateTransferError = convertUpdateCodeIntoString(
                        updatingNode.updatingNodeStatus,
                        R.string.apply_failed
                    )
                    setProgressStateAndColor(updateTransferError, colorRed)
                }

                UpdatePhase.APPLY_IN_PROGRESS -> setProgressStateAndColor(
                    R.string.operation_in_progress,
                    colorBlue
                )

                UpdatePhase.APPLY_SUCCESS -> setProgressStateAndColor(
                    R.string.operation_success,
                    colorGreen
                )

                UpdatePhase.VERIFICATION_IN_PROGRESS -> setProgressStateAndColor(
                    R.string.verifying_update,
                    colorBlue
                )

                UpdatePhase.VERIFICATION_FAILED -> {
                    val updateTransferError = convertUpdateCodeIntoString(
                        updatingNode.updatingNodeStatus,
                        R.string.verification_failed
                    )
                    setProgressStateAndColor(updateTransferError, colorRed)
                }

                UpdatePhase.VERIFICATION_SUCCEEDED -> setProgressStateAndColor(
                    R.string.verification_succeeded,
                    colorGreen
                )

                UpdatePhase.UNKNOWN -> setProgressStateAndColor(R.string.unknown_phase, colorWhite)
                else -> setProgressStateAndColor("", colorWhite)
            }
        }

        private fun convertTransferCodeIntoString(transferCode: ServerState): String {
            return when (transferCode) {
                ServerState.SUCCESS -> itemNode.context.getString(R.string.operation_success)
                ServerState.INVALID_BLOCK_NUMBER -> itemNode.context.getString(R.string.transfer_error_invalid_block_number)
                ServerState.INVALID_BLOCK_SIZE -> itemNode.context.getString(R.string.transfer_error_invalid_block_size)
                ServerState.INVALID_CHUNK_SIZE -> itemNode.context.getString(R.string.transfer_error_invalid_chunk_size)
                ServerState.WRONG_PHASE -> itemNode.context.getString(R.string.transfer_error_wrong_phase)
                ServerState.INVALID_PARAMETER -> itemNode.context.getString(R.string.transfer_error_invalid_parameter)
                ServerState.WRONG_BLOB_ID -> itemNode.context.getString(R.string.transfer_error_wrong_blob_id)
                ServerState.BLOB_TOO_LARGE -> itemNode.context.getString(R.string.transfer_error_blob_too_large)
                ServerState.UNSUPPORTED_TRANSFER_MODE -> itemNode.context.getString(R.string.transfer_error_unsupported_transfer_mode)
                ServerState.INTERNAL_ERROR -> itemNode.context.getString(R.string.transfer_error_internal_error)
                ServerState.INFORMATION_UNAVAILABLE -> itemNode.context.getString(R.string.transfer_error_information_unavailable)
            }
        }

        private fun convertUpdateCodeIntoString(
            updateCode: UpdatingNodeStatus,
            @StringRes successMessage: Int
        ): String {
            return when (updateCode) {
                UpdatingNodeStatus.SUCCESS -> itemNode.context.getString(successMessage)
                UpdatingNodeStatus.OUT_OF_RESOURCES -> itemNode.context.getString(R.string.update_error_out_of_resources)
                UpdatingNodeStatus.INVALID_PHASE -> itemNode.context.getString(R.string.update_error_invalid_phase)
                UpdatingNodeStatus.INTERNAL_ERROR -> itemNode.context.getString(R.string.update_error_internal_error)
                UpdatingNodeStatus.INVALID_FIRMWARE_INDEX -> itemNode.context.getString(R.string.update_error_invalid_firmware_index)
                UpdatingNodeStatus.METADATA_CHECK_FAILED -> itemNode.context.getString(R.string.update_error_metadata_check_failed)
                UpdatingNodeStatus.TEMPORARILY_UNABLE -> itemNode.context.getString(R.string.update_error_temporarily_unable)
                UpdatingNodeStatus.BLOB_TRANSFER_BUSY -> itemNode.context.getString(R.string.update_error_blob_transfer_busy)
            }
        }

        private fun setProgressStateAndColor(phase: String, color: Int) {
            textViewNodeUpdateProgress.apply {
                text = phase
                setTextColor(color)
            }
        }

        private fun setProgressStateAndColor(@StringRes phaseRes: Int, color: Int) {
            textViewNodeUpdateProgress.apply {
                text = itemNode.context.getString(phaseRes)
                setTextColor(color)
            }
        }

        companion object {
            fun from(parent: ViewGroup): FirmwareReceiversViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)

                val itemNode = layoutInflater.inflate(R.layout.item_node, parent, false)
                return FirmwareReceiversViewHolder(itemNode)
            }
        }
    }
}