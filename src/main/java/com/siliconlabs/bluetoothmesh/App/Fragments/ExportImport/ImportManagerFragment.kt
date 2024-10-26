/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportJsonObject.JsonMesh
import com.siliconlabs.bluetoothmesh.App.Logic.ExportImport.JsonImporter
import com.siliconlabs.bluetoothmesh.databinding.ExportLayoutFragmentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class ImportManagerFragment : Fragment() {
    private lateinit var binding: ExportLayoutFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = ExportLayoutFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnOperation.text = "Import"
        binding.btnOperation.setOnClickListener {
            initiateImportJson()
        }
    }

    private fun initiateImportJson() {
        pickJsonFile()
    }

    private fun pickJsonFile() {
        val jsonIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"

        }
        pickFileLauncher.launch(jsonIntent)
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { results ->
        if (results.resultCode == Activity.RESULT_OK) {
            val selectedFileUri = results.data?.data
            selectedFileUri?.let { uriResult ->
                readAndParseJsonFromUri(uriResult)
                Toast.makeText(requireContext(), "Import Initiated Success", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readAndParseJsonFromUri(uri: Uri) {
        GlobalScope.launch(Dispatchers.Main) {
            val jsonContent = readJsonFromUri(uri)
            jsonContent?.let { json ->
                val jsonObject = JSONObject(json)
                val jsonString = jsonObject.toString()
                val json = Gson().fromJson<JsonMesh>(jsonString, JsonMesh::class.java)
                JsonImporter(json).import()
            }
        }
    }

    private suspend fun readJsonFromUri(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            val contentResolver = requireContext().contentResolver
            var jsonContent: String? = null

            contentResolver.openInputStream(uri).use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                jsonContent = reader.readText()
            }

            jsonContent
        }
    }
}