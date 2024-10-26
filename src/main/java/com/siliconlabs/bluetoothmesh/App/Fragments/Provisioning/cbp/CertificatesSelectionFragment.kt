/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbp

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.launch
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.CertificateViewModel
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ProvisioningFragmentBase
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbp.CertificatesSelectionViewModel.CertificateType
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbp.CertificatesSelectionViewModel.ProvisioningState
import com.siliconlabs.bluetoothmesh.App.Utils.ActivityResultContractsExt
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateFileUtils.CertificateFile
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.showIf
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentCertificatesSelectionBinding
import com.siliconlabs.bluetoothmesh.databinding.ItemFileSelectionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.tinylog.Logger

@AndroidEntryPoint
class CertificatesSelectionFragment : ProvisioningFragmentBase(R.layout.fragment_certificates_selection) {
    private val layout by viewBinding(FragmentCertificatesSelectionBinding::bind)

    private val viewModel: CertificatesSelectionViewModel by viewModels()

    private val selectRootCertificate = registerForActivityResult(
            ActivityResultContractsExt.CertificateFilePickerContract()) { result ->
        onCertificateFileSelected(result, CertificateType.ROOT)
    }

    private val selectDeviceCertificate = registerForActivityResult(
            ActivityResultContractsExt.CertificateFilePickerContract()) { result ->
        onCertificateFileSelected(result, CertificateType.DEVICE)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(deviceToProvisionIsValid()) {
            setupBackPressedBehaviour()
            collectMessages(viewModel.messageFlow)
            setupCertificateSelection()
            setupProvisionButton()
            setupProvisioningState()
        }
    }

    override fun isProvisioningActive() =
            viewModel.provisioningState.value >= ProvisioningState.ACTIVE

    private fun setupCertificateSelection() {
        setupCertificateSelectionLayout(
                layout.layoutRootCertificate,
                R.string.cbp_root_certificate,
                selectRootCertificate,
                viewModel.rootCertificateState
        )

        setupCertificateSelectionLayout(
                layout.layoutDeviceCertificate,
                R.string.cbp_device_certificate,
                selectDeviceCertificate,
                viewModel.deviceCertificateState
        )
    }

    private fun setupCertificateSelectionLayout(
            fileSelectionLayout: ItemFileSelectionBinding,
            @StringRes certDisplayName: Int,
            onClickLauncher: ActivityResultLauncher<Unit>,
            fileStateFlow: Flow<CertificateViewModel.CertificateState?>
    ) {
        fileSelectionLayout.apply {
            tvFileTitle.setText(certDisplayName)
            tvFileName.setText(R.string.cbp_select_certificate)
            wrapperFilePicker.setOnClickListener {
                onClickLauncher.launch()
            }
        }

        viewLifecycleScope.launch {
            fileStateFlow.collectLatest { state ->
                fileSelectionLayout.apply {
                    tvFileName.text = state?.name ?: getString(R.string.cbp_select_certificate)
                    progressFileCircular.showIf(state?.isLoading == true)
                }
            }
        }
    }

    private fun setupProvisionButton() {
        layout.buttonProvision.setOnClickListener {
            viewModel.startProvisioning()
        }
        viewLifecycleScope.launch {
            viewModel.canProvisionStart.collectLatest {
                layout.buttonProvision.isEnabled = it
            }
        }
    }

    private fun setupProvisioningState() {
        viewLifecycleScope.launch {
            viewModel.provisioningState.collectLatest { state ->
                if (state == ProvisioningState.SUCCESS) {
                    onProvisioningComplete()
                    return@collectLatest
                }

                val isProvisioning = state == ProvisioningState.ACTIVE
                layout.apply {
                    layoutRootCertificate.wrapperFilePicker.isEnabled = !isProvisioning
                    layoutDeviceCertificate.wrapperFilePicker.isEnabled = !isProvisioning
                    progressIndicatorProvisioning.isInvisible = !isProvisioning
                }
            }
        }
    }

    private fun onProvisioningComplete() {
        MeshToast.show(requireContext(), getString(R.string.provisioning_completed))
        val deviceFragment = DeviceFragment.firstConfigurationInstance(viewModel.provisionedDevice)
        requireMainActivity().showFragment(deviceFragment)
    }

    private fun onCertificateFileSelected(result: Result<CertificateFile>?, type: CertificateType) {
        result?.fold(
                onSuccess = { viewModel.addCertificateFile(it, type) },
                onFailure = {
                    Logger.error(it) { it.message }
                    lifecycleScope.launch {
                        withResumed {
                            showErrorDialog(Message.error(R.string.error_process_selected_file))
                        }
                    }
                }
        )
    }
}