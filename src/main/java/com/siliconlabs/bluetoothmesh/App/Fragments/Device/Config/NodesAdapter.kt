package com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.R

internal class NodesAdapter(context: Context, nodes: List<Node>) : ArrayAdapter<Node>(context, R.layout.spinner_item_dark, nodes) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getRowView(position, convertView)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getRowView(position, convertView)
    }

    private fun getRowView(position: Int, convertView: View?): View {
        val rowTextView = convertView ?: LayoutInflater.from(context).inflate(R.layout.spinner_item_dark, null)
        return (rowTextView as TextView).apply { text = getItem(position)!!.name }
    }
}