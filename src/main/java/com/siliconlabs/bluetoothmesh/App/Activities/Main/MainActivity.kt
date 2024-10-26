/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Activities.Main

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.Window
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.Database.DeviceFunctionalityDb
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragmentNLC
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.DistributionIdle.DistributionIdleFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.Update.UpdateNodesFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Distributor.UpdateStatusChecker.UpdateStatusCheckerFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.MainFragment.MainFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.meshNavArgs
import com.siliconlabs.bluetoothmesh.App.Utils.PermissionsService
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.tinylog.kotlin.Logger

@AndroidEntryPoint
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    companion object {
        const val PERMISSIONS_REQUEST_CODE: Int = 12
    }

    private val layout by viewBinding(ActivityMainBinding::bind)

    @VisibleForTesting
    internal val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        DeviceFunctionalityDb.saveTab(false)
        setActionBar()
        if (savedInstanceState == null) {
            showFragment(
                MainFragment.newInstance(),
                addToBackStack = false
            )
        }

        checkPermissions()
        collectCurrentSubnet()
        observeBackStack()
    }

    private fun collectCurrentSubnet() {
        // collect is empty but subscription state determines whether network should connect
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.currentSubnet.collect { }
            }
        }
    }

    private fun checkPermissions() {
        val permissionsNotGranted = PermissionsService.requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNotGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissionsNotGranted.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    @SuppressLint("MissingSuperCall")
    //Calling super interferes with the fragment's onRequestPermissionsResult.
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            permissions.zip(grantResults.toTypedArray()).forEach {
                if (it.second != PackageManager.PERMISSION_GRANTED) {
                    Logger.error { "PERMISSION ${it.first} IS MISSING, CLOSING APP" }
                    finish()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            if (item.itemId == android.R.id.home) {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun observeBackStack() {
        invalidateToolbarOnBackstackChange()
        supportFragmentManager.addOnBackStackChangedListener {
            invalidateToolbarOnBackstackChange()
            // ----- This picks up requested subnet from current fragment and connects to it  ------
            supportFragmentManager.findFragmentById(R.id.fragment_container)?.let { current ->
                current.meshNavArgs?.let {
                    viewModel.setCurrentSubnet(it.subnet)
                }
                // Check the fragment name
                val fragmentName = current.javaClass.simpleName
                println("----FragmentName:$fragmentName")
                when (fragmentName) {
                    "ProvisioningFragment" -> {
                        hideBackImagePressOnFragments()
                    }

                    "Provisioning_nlc" -> {
                        hideBackImagePressOnFragments()
                    }

                    "DeviceFragment" -> {
                        hideBackImagePressOnFragments()
                    }

                    "DeviceFragmentNLC" -> {
                        hideBackImagePressOnFragments()
                    }

                    else -> {
                    }
                }
            }
        }
    }

    private fun invalidateToolbarOnBackstackChange() {
        supportFragmentManager.apply {
            val hasFragments = backStackEntryCount > 0
            supportActionBar?.setDisplayHomeAsUpEnabled(hasFragments)
            layout.toolbar.logoActionBar.isVisible = !hasFragments
        }
    }

    fun showFragment(
        fragment: Fragment, addToBackStack: Boolean = true,
        fragmentName: String? = null, tag: String? = null,
    ) {
        supportFragmentManager.commit(true) {
            setCustomAnimations(
                R.anim.enter,
                R.anim.exit,
                R.anim.pop_enter,
                R.anim.pop_exit
            )
            if (addToBackStack) {
                addToBackStack(fragmentName)
            }
            replace(R.id.fragment_container, fragment, tag)
        }
    }

    fun invalidateSubnetConnection() {
        viewModel.invalidateSubnet()
    }

    fun popWholeBackStack() {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    fun navigateToUpdateNodesFragment(distributor: Node) {
        showFragment(UpdateNodesFragment.newInstance(distributor))
    }

    fun navigateToDistributionIdleFragment(distributor: Node) {
        showFragment(
            DistributionIdleFragment.newInstance(distributor),
            tag = DistributionIdleFragment.backstackTAG
        )
    }

    fun navigateToDistributionFragment(distributor: Node) {
        showFragment(
            UpdateStatusCheckerFragment.newInstance(distributor),
            fragmentName = UpdateStatusCheckerFragment.backstackTAG
        )
    }

    fun exitDeviceFirmwareUpdate() {
        supportFragmentManager.popBackStack(
            UpdateStatusCheckerFragment.backstackTAG,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

    fun setActionBar(title: String? = null) {
        layout.apply {
            setSupportActionBar(toolbar.toolbar)
            if (title == null) {
                supportActionBar?.setDisplayShowTitleEnabled(false)
            } else {
                supportActionBar?.setDisplayShowTitleEnabled(true)
                supportActionBar?.setTitle(title)
            }
        }
    }

    fun hideBackImagePressOnFragments() {
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setDisplayShowHomeEnabled(false)
    }

    fun returnToMainScreenFromDeviceConfigNLCFragment() {
        val deviceFragment = supportFragmentManager.fragments
            .filterIsInstance<DeviceFragmentNLC>()
            .firstOrNull()
        deviceFragment?.setUpBackPressWithoutLifeCycleAware()
    }

    fun returnTOMainScreenFromDeviceConfigNonNLCFragment() {
        val deviceFragment = supportFragmentManager.fragments
            .filterIsInstance<DeviceFragment>()
            .firstOrNull()
        deviceFragment?.setUpBackPressWithoutLifeCycleAware()
    }
}