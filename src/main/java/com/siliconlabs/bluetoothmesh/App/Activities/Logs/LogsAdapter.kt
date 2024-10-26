package com.siliconlabs.bluetoothmesh.App.Activities.Logs

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.siliconlabs.bluetoothmesh.App.Activities.Logs.LogsAdapter.LogsViewHolder
import com.siliconlabs.bluetoothmesh.databinding.LogsAdapterBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LogsAdapter(private val onLogsClicked: (File, View) -> Unit) : ListAdapter<File, LogsViewHolder>(LogsDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogsViewHolder {
        return LogsViewHolder.from(parent)
    }

    override fun onBindViewHolder(holder: LogsViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem)
        holder.itemView.setOnClickListener {
            onLogsClicked(currentItem, holder.itemView)
        }
    }

    class LogsViewHolder private constructor(private val layout: LogsAdapterBinding) : ViewHolder(layout.root) {
        fun bind(file: File) {
            val modifiedDate = Date(file.lastModified())

            layout.apply {
                tvName.text = file.nameWithoutExtension
                tvSize.text = Formatter.formatFileSize(layout.root.context, file.length())
                tvModified.text = dateFormat.format(modifiedDate)
            }
        }

        companion object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            fun from(parent: ViewGroup): LogsViewHolder {
                val binding = LogsAdapterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return LogsViewHolder(binding)
            }
        }
    }

    private object LogsDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }
}
