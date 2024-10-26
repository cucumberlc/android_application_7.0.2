/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Distributor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.formattedFirmwareId
import com.siliconlabs.bluetoothmesh.databinding.ItemNodeBinding

class UpdatableNodesAdapter(
    updatableNodes: List<Node>,
) : RecyclerView.Adapter<UpdatableNodesAdapter.UpdatableNodesViewHolder>() {
    private val updatableNodes = updatableNodes.map {
        SelectableNode(it, false)
    }

    var listener: OnSelectedNodeChangeListener? = null

    fun clearSelection() {
        updatableNodes.forEach { it.isSelected = false }
    }

    interface OnSelectedNodeChangeListener {
        fun onSelectNode(node: Node)
        fun onUnselectNode(node: Node)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UpdatableNodesViewHolder {
        return UpdatableNodesViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: UpdatableNodesViewHolder, position: Int) {
        val nodeToBind = updatableNodes[position]
        holder.bind(nodeToBind) { node, isSelected ->
            if (isSelected)
                listener?.onSelectNode(node.node)
            else
                listener?.onUnselectNode(node.node)
        }
    }

    override fun getItemCount(): Int = updatableNodes.size

    class UpdatableNodesViewHolder private constructor(private val layout: ItemNodeBinding) :
        RecyclerView.ViewHolder(layout.root) {

        fun bind(node: SelectableNode, onNodeSelectionChange: (SelectableNode, Boolean) -> Unit) {
            bindNodeData(node)
            bindFirmwareDetails(node.node)
            bindNodeToUpdateListener(onNodeSelectionChange, node)
        }

        private fun bindNodeData(node: SelectableNode) = with(layout) {
            tvNodeName.text = node.node.name
            checkboxNodeToUpdate.setOnCheckedChangeListener(null)
            checkboxNodeToUpdate.isChecked = node.isSelected
        }

        private fun bindFirmwareDetails(node: Node) {
            node.deviceCompositionData?.let { dcd ->
                layout.tvFirmwareDetails.apply {
                    isVisible = true
                    text = dcd.formattedFirmwareId()
                }
            }
        }

        private fun bindNodeToUpdateListener(
            onNodeSelectionChange: (SelectableNode, Boolean) -> Unit,
            node: SelectableNode,
        ) {
            itemView.setOnClickListener {
                layout.checkboxNodeToUpdate.toggle()
                node.isSelected = layout.checkboxNodeToUpdate.isChecked
            }
            layout.checkboxNodeToUpdate.setOnCheckedChangeListener { _, checked ->
                onNodeSelectionChange.invoke(node, checked)
            }
        }

        companion object {
            fun from(parent: ViewGroup): UpdatableNodesViewHolder {
                val layoutInflater = LayoutInflater.from(parent.context)
                return UpdatableNodesViewHolder(
                    ItemNodeBinding.inflate(layoutInflater, parent, false)
                )
            }
        }
    }

    data class SelectableNode(
        val node: Node,
        var isSelected: Boolean,
    )
}