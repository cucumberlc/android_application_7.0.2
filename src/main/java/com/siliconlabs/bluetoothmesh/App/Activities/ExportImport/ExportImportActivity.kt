/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Activities.ExportImport

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ExportFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.ExportImport.ImportManagerFragment
import com.siliconlabs.bluetoothmesh.databinding.ActivityExportImportBinding

class ExportImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportImportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fragExportLoader()
        fragImportManger()
    }

    private fun fragExportLoader() {
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(binding.frameExportContainer.id, ExportFragment()).commit()
    }

    private fun fragImportManger() {
        val ft = supportFragmentManager.beginTransaction()
        ft.replace(binding.frameImportContainer.id, ImportManagerFragment())
            .commit()
    }
}