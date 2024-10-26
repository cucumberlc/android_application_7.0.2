/*
 * Copyright © 2022 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.exportSaveDataHandler
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestIdentity
import com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests.IOPTestSuite
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentInteroperabilityTestsBinding
import kotlinx.coroutines.launch

class InteroperabilityTestsFragment : Fragment(R.layout.fragment_interoperability_tests){
    private val binding by viewBinding(FragmentInteroperabilityTestsBinding::bind)
    private val viewModel: InteroperabilityTestsViewModel by viewModels()

    private val exportDataHandler = exportSaveDataHandler { viewModel.provider }

    private val adapter = InteroperabilityTestsAdapter()

    private val outputOOBDialog by lazy { OutputOOBDialog(requireContext()) }
    private val inputOOBDialog by lazy { InputOOBDialog(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupMenu()
        setupTestsList()
        setupBottomBar()
        observeState()
    }

    private fun setupMenu() {
        (requireActivity() as MenuHost).addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.menu_iop_tests_screen_toolbar, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    val isStopActionActive =
                        viewModel.suiteState.value is IOPTestSuite.State.InProgress
                    menu.findItem(R.id.action_start_execution).run {
                        isVisible = !isStopActionActive
                    }
                    menu.findItem(R.id.action_stop_execution).run {
                        isVisible = isStopActionActive
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem) =
                    when (menuItem.itemId) {
                        R.id.action_start_execution -> {
                            displayConfirmationDialog()
                            true
                        }
                        R.id.action_stop_execution -> {
                            viewModel.stopExecution()
                            true
                        }
                        else -> false
                    }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
    }

    private fun displayConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.iop_test_start_warning_title))
            .setMessage(getString(R.string.iop_test_start_warning_message))
            .setPositiveButton(R.string.dialog_positive_ok) { _, _ -> viewModel.startExecution() }
            .setNegativeButton(R.string.dialog_negative_cancel, null)
            .show()
    }

    private fun setupTestsList() {
        binding.testsList.apply {
            adapter = this@InteroperabilityTestsFragment.adapter
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun setupBottomBar() {
        binding.documentationButton.setOnClickListener {
            showDocumentation()
        }
        binding.shareButton.setOnClickListener {
            exportDataHandler.selectSaveLocation()
        }
    }

    private fun showDocumentation() {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.iop_test_documentation_uri))
            )
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeSuiteState() }
                launch { observeTestsState() }
                launch { observeOutputOOBValueCallback() }
                launch { observeInputOOBValueToDisplay() }
            }
        }
    }

    private suspend fun observeSuiteState() {
        viewModel.suiteState.collect {
            updateMenu()
            updateBottomBar(it)
            if (it is IOPTestSuite.State.InProgress) scrollList(it.currentTest)
        }
    }

    private fun updateMenu() {
        requireActivity().invalidateMenu()
    }

    private fun updateBottomBar(suiteState: IOPTestSuite.State) {
        binding.bottomBar.isVisible = suiteState !is IOPTestSuite.State.InProgress
        binding.shareButton.isEnabled = suiteState == IOPTestSuite.State.Finished
    }

    private fun scrollList(targetId: IOPTestIdentity) {
        val position = viewModel.testsState.value.indexOfFirst { it.id == targetId }
        binding.testsList.layoutManager?.scrollToPosition(position)
    }

    private suspend fun observeTestsState() {
        viewModel.testsState.collect { updateList(it) }
    }

    private fun updateList(items: List<IOPTestItemUiState>) {
        adapter.submitList(items)
    }

    private suspend fun observeOutputOOBValueCallback() {
        viewModel.onProvidedOutputOOBValue.collect { callback ->
            if (callback != null) outputOOBDialog.show(callback)
            else outputOOBDialog.hide()
        }
    }

    private suspend fun observeInputOOBValueToDisplay() {
        viewModel.inputOOBValueToDisplay.collect { value ->
            if (value != null) inputOOBDialog.show(value)
            else inputOOBDialog.hide()
        }
    }
}
