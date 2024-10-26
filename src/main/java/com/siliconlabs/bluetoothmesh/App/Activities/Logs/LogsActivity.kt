package com.siliconlabs.bluetoothmesh.App.Activities.Logs

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.exportDataHandler
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ActivityLogsBinding
import java.io.File

class LogsActivity : AppCompatActivity(R.layout.activity_logs) {
    private val viewModel by viewModels<LogsActivityViewModel>()

    private val exportDataHandler = exportDataHandler { viewModel.exportDataProvider }

    private val layout by viewBinding(ActivityLogsBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = "Export Logs"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        loadList()
    }

    private fun loadList() {
        val logsAdapter = LogsAdapter(::onLogsClicked)

        logsAdapter.submitList(viewModel.getLogs())

        layout.lvLogs.apply {
            adapter = logsAdapter
            layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        }
        val animator = layout.lvLogs.itemAnimator
        (animator as SimpleItemAnimator).apply {
            animator.supportsChangeAnimations = false
        }
    }

    private fun onLogsClicked(file: File, itemView: View) {
        viewModel.setSelectedLog(file)
        openShareOrSavePopup(itemView)
    }

    private fun openShareOrSavePopup(itemView: View) {
        PopupMenu(this, itemView, Gravity.END).apply {
            menuInflater.inflate(R.menu.menu_log_actions, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_log_actions_share -> {
                        exportDataHandler.showShareSheet(this@LogsActivity)
                        true
                    }
                    R.id.menu_log_actions_save -> {
                        exportDataHandler.selectSaveLocation()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }
}
