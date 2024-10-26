/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.MainFragment

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import by.kirich1409.viewbindingdelegate.viewBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.siliconlabs.bluetoothmesh.App.Activities.ExportImport.ExportImportActivity
import com.siliconlabs.bluetoothmesh.App.Activities.Logs.LogsActivity
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.Common.exportDataHandler
import com.siliconlabs.bluetoothmesh.App.Fragments.InteroperabilityTests.InteroperabilityTestsFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Network.NetworkFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Network.NetworkFragmentNLC
import com.siliconlabs.bluetoothmesh.App.Fragments.Scanner.ScannerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppDestination
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.launchAndRepeatWhenResumed
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogAboutBinding
import com.siliconlabs.bluetoothmesh.databinding.DialogExportKeysBinding
import com.siliconlabs.bluetoothmesh.databinding.MainScreenBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import org.tinylog.kotlin.Logger
import java.time.Instant
import java.time.ZoneId
import com.siliconlab.bluetoothmesh.adk.BuildConfig as AdkBuildConfig
import com.siliconlabs.bluetoothmesh.BuildConfig as AppBuildConfig

@AndroidEntryPoint
class MainFragment
@Deprecated(
    "use newInstance()",
    replaceWith = ReplaceWith("MainFragment.newInstance()")
)
constructor() : Fragment(R.layout.main_screen), MenuProvider {
    companion object {
        fun newInstance() =
            @Suppress("DEPRECATION")
            MainFragment().withMeshNavArg(MeshAppDestination.OfNetwork)

        private const val NLC_TAB_POSITION = 1
        private const val TOTAL_TAB = 3
    }

    private val layout by viewBinding(MainScreenBinding::bind)
    private val viewModel: MainFragmentViewModel by viewModels()

    private val exportDataHandler = exportDataHandler { viewModel.exportDataProvider }
    private var displayedDialog: Dialog? = null

    private val coarseLocationRequestCode = 1
    private var showedLocationAlertDialog = false
    private var requestedLocationPermission = false
    private var locationManager: LocationManager? = null

    private val contract = ActivityResultContracts.StartActivityForResult()
    private val enableBluetoothLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        contract
    ) { result ->
        if (result.resultCode == Activity.RESULT_CANCELED) {
            MeshToast.show(requireContext(), R.string.main_fragment_toast_ble_deny)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager = context?.getSystemService(LOCATION_SERVICE) as LocationManager?
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        requireMainActivity().setActionBar()
        setupSelectedTab()
        setupFunctionalityButtons()
        setupView()
    }

    private fun setupSelectedTab() {
        if (DeviceFunctionalityDb.getTab()) {
            navigateToSelectedTab(NLC_TAB_POSITION)
        } else {
            navigateToSelectedTab(layout.tabLayout.selectedTabPosition)
        }
    }

    private fun navigateToSelectedTab(position: Int) = with(layout.tabLayout) {
        post { getTabAt(position)!!.select() }
    }

    private fun setupView() {
        setupTabLayout()

        checkBTAdapter()
        checkGPS()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_main_screen_toolbar, menu)
    }

    override fun onMenuItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.about_bluetooth_mesh -> showAboutDialog()
            R.id.credits -> showCreditsDialog()
            R.id.export_keys -> showExportKeysDialog()
            R.id.export_logs -> openLogsActivity()
            R.id.export_import -> exportImportActivity()
            else -> null
        } != null

    private fun setupFunctionalityButtons() {
        layout.bluetoothEnableBtn.setOnClickListener {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }

        layout.locationEnableBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                coarseLocationRequestCode
            )
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewModel.isBluetoothEnabled.collectLatest {
                layout.bluetoothEnable.isGone = it
            }
        }

        viewLifecycleOwner.launchAndRepeatWhenResumed {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLocationEnabled.collectLatest {
                    layout.locationEnable.isGone = it
                }
            }
        }
    }

    private fun exportImportActivity() {
        println("IMPEXP--------")
        val intent = Intent(context, ExportImportActivity::class.java)
        requireContext().startActivity(intent)
    }

    private fun openLogsActivity() {
        val intent = Intent(context, LogsActivity::class.java)
        startActivity(intent)
    }

    private fun showAboutDialog() {
        val dialogLayout = DialogAboutBinding.inflate(layoutInflater).apply {
            val appVersionName =
                StringBuilder(getString(R.string.dialog_about_app_version).format(AppBuildConfig.VERSION_NAME))
            val adkVersionName =
                getString(R.string.dialog_about_adk_version).format(AdkBuildConfig.ADK_VERSION)
            val copyrightNotice = getString(R.string.dialog_about_copyright).let {
                val copyrightEndYear = Instant.ofEpochSecond(AppBuildConfig.BUILD_TIMESTAMP)
                    .atZone(ZoneId.systemDefault()).year
                it.format(copyrightEndYear)
            }
            tvAppVersion.text = appVersionName
            tvAdkVersion.text = adkVersionName
            tvCopyrightNotice.text = copyrightNotice
        }

        displayedDialog?.dismiss()
        displayedDialog = AlertDialog.Builder(requireContext())
            .setView(dialogLayout.root)
            .setPositiveButton(R.string.dialog_positive_ok) { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    private fun showCreditsDialog() {
        val licences = StringBuilder(getString(R.string.menu_credits_icons))
            .append("\n\n\n")
            .append(getString(R.string.menu_credits_dagger_license))
            .append(getString(R.string.menu_credits_gson_license))
            .append("\n\n\n")
            .append(getString(R.string.menu_credits_rxjava_license))
            .append("\n\n\n")
            .append(getString(R.string.menu_credits_swipe_layout_license))
            .append("\n\n\n")
            .append(getString(R.string.menu_credits_commons_compress_license))
            .append("\n\n\n")
            .append(getString(R.string.menu_credits_tink_license))
            .append("\n\n\n")
            .append(getString(R.string.licence_kotlin_xml_builder))

        displayedDialog?.dismiss()
        displayedDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_licenses)
            .setMessage(licences)
            .setPositiveButton(R.string.dialog_positive_ok, null)
            .show()
    }

    private fun showExportKeysDialog() {
        val dialogLayout = DialogExportKeysBinding.inflate(LayoutInflater.from(context)).apply {
            btnShareKeys.setOnClickListener {
                exportDataHandler.showShareSheet(requireContext())
            }

            btnSaveKeys.setOnClickListener {
                exportDataHandler.selectSaveLocation()
            }
        }

        displayedDialog?.dismiss()
        displayedDialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.menu_export_cryptographic_keys)
            .setMessage("\n" + getString(R.string.menu_export_cryptographic_keys_message))
            .setView(dialogLayout.root)
            .show()
    }

    // Permission callback

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray,
    ) {
        when (requestCode) {
            coarseLocationRequestCode -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    if (showedLocationAlertDialog) {
                        return
                    }
                    showedLocationAlertDialog = true

                    AlertDialog.Builder(requireContext())
                        .setCancelable(false)
                        .setTitle(
                            getString(
                                R.string.main_activity_dialog_location_permission_not_granted_title
                            )
                        )
                        .setMessage(
                            getString(
                                R.string.main_activity_dialog_location_permission_not_granted_message
                            )
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            showedLocationAlertDialog = false
                        }.show()
                } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Logger.debug { "coarse location permission granted" }

                    setupView()
                }
            }

            else -> Unit
        }
    }

    private fun setupTabLayout() {
        layout.apply {
            viewPager.adapter = MainFragmentPageAdapter()
            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.main_activity_non_nlc_page_title)
                   // 1 -> getString(R.string.main_activity_provision_page_title)
                    1 -> getString(R.string.main_activity_nlc_page_title)
                    else -> getString(R.string.test_title_iop_test)
                }
            }.attach()
        }
    }

    private fun checkGPS() {
        if (requestedLocationPermission) {
            return
        }
        requestedLocationPermission = true

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                coarseLocationRequestCode
            )
        }
    }

    private fun checkBTAdapter() {
        if (!requireActivity().packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE
            )
        ) {
            AlertDialog.Builder(requireContext())
                .setCancelable(false)
                .setTitle(getString(R.string.main_activity_dialog_not_support_ble_title))
                .setMessage(getString(R.string.main_activity_dialog_not_support_ble_message))
                .setPositiveButton(
                    getString(
                        R.string.main_activity_dialog_not_support_ble_positive_button
                    )
                ) { _, _ ->
                    requireActivity().finish()
                }
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        displayedDialog?.dismiss()
    }

    private inner class MainFragmentPageAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = TOTAL_TAB

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> NetworkFragment()
                //1 -> ScannerFragment.newDirectInstance()
                1 -> NetworkFragmentNLC()
                else -> InteroperabilityTestsFragment()
            }
        }
    }
}