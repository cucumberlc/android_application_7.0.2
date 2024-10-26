/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.siliconlabs.bluetoothmesh.App.Logic.ExportImport.JsonExporter
import com.siliconlabs.bluetoothmesh.databinding.ExportLayoutFragmentBinding
import java.text.SimpleDateFormat
import java.util.Date

class ExportFragment : Fragment() {

    private lateinit var binding: ExportLayoutFragmentBinding
    private var fileName:String = ""
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = ExportLayoutFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnOperation.text = "Export"
        binding.btnOperation.setOnClickListener {
            generateAndExportJson()

        }
    }

    private fun generateAndExportJson() {
        val myJson = JsonExporter()
        val dataJson = myJson.exportJson()
        saveJsonToDownloadFolder(dataJson)
    }

    private fun getDateTime(): String? {
        val fileName = SimpleDateFormat("_yyyyMMddHHmm").format(Date())
        return fileName
    }

    private fun saveJsonToDownloadFolder(jsonContent: String) {
        fileName = "Json" + getDateTime() + ".json"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS
            )
        }

        val resolver = requireContext().contentResolver
        val uri: Uri? = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        )

        uri?.let { documentUri ->
            try {
                    resolver.openOutputStream(documentUri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                    outputStream.flush()
                    Toast.makeText(requireContext(), "Export Initiated Success \n$fileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}