/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbpRecords

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.launch
import androidx.core.view.isInvisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DeviceFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.ProvisioningFragmentBase
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbpRecords.ProvisioningRecordsViewModel.ProvisioningState
import com.siliconlabs.bluetoothmesh.App.Fragments.Provisioning.cbpRecords.ProvisioningRecordsViewModel.StartOrContinue
import com.siliconlabs.bluetoothmesh.App.Utils.ActivityResultContractsExt
import com.siliconlabs.bluetoothmesh.App.Utils.CertificateFileUtils.CertificateFile
import com.siliconlabs.bluetoothmesh.App.Utils.Colors
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.showIf
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentProvisioningRecordsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.tinylog.Logger

@AndroidEntryPoint
class ProvisioningRecordsFragment : ProvisioningFragmentBase(R.layout.fragment_provisioning_records) {
    private val layout by viewBinding(FragmentProvisioningRecordsBinding::bind)

    private val viewModel by viewModels<ProvisioningRecordsViewModel>()

    private val selectRootCertificate = registerForActivityResult(
            ActivityResultContractsExt.CertificateFilePickerContract()) { result ->
        onCertificateFileSelected(result)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if(deviceToProvisionIsValid()) {
            viewModel.startCbpProvisioning()
            collectMessages(viewModel.messageFlow)
            setupBackPressedBehaviour()
            setupCertificateSelection()
            setupContinueProvisioningButton()
            setupRecordsDisplay()
            setupProvisioningState()
        }
    }

    override fun isProvisioningActive() =
            viewModel.provisioningState.value >= ProvisioningState.ACTIVE

    private fun setupCertificateSelection() {
        layout.layoutRootCertificate.apply {
            tvFileTitle.setText(R.string.cbp_root_certificate)

            wrapperFilePicker.setOnClickListener {
                selectRootCertificate.launch()
            }
        }

        viewLifecycleScope.launch {
            viewModel.rootCertificateState.collectLatest { state ->
                layout.layoutRootCertificate.apply {
                    tvFileName.text = state?.name ?: getString(R.string.cbp_select_certificate)
                    progressFileCircular.showIf(state?.isLoading == true)
                }
            }
        }
    }

    private fun setupContinueProvisioningButton() {
        layout.buttonContinueProvisioning.setOnClickListener {
            viewModel.startOrContinueCbpProvisioning()
        }

        viewLifecycleScope.launch {
            viewModel.canStartOrContinueProvisioning.collectLatest {
                layout.buttonContinueProvisioning.apply {
                    isEnabled = it != StartOrContinue.DISABLED

                    if (it == StartOrContinue.START) {
                        setText(R.string.provisioning_records_get_records)
                    } else {
                        setText(R.string.scanner_adapter_provision)
                    }
                }
            }
        }
    }

    private fun setupRecordsDisplay() {
        viewLifecycleScope.launch {
            viewModel.records.collectLatest {
                layout.apply {
                    setAvailability(certificateDeviceAvailability, it?.deviceCertificate != null)
                    setAvailability(certificateUriAvailability, it?.uriExists == true)
                    setAvailability(certificateIntermediateAvailability,
                            it?.intermediateCertificatesExist == true
                    )

                    if (it != null && it.deviceCertificate == null) {
                        if (it.uriExists)
                            remark.setText(R.string.provisioning_records_certificate_uri_advice)
                        else
                            remark.setText(R.string.provisioning_records_missing_certificate_advice)
                    }
                }
            }
        }
    }

    private fun setAvailability(textView: TextView, isAvailable: Boolean) {
        textView.apply {
            if (isAvailable) {
                setText(R.string.provisioning_records_certificate_available)
                setTextColor(Colors.green)
            } else {
                setText(R.string.provisioning_records_certificate_unavailable)
                setTextColor(Colors.gray)
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

                val isLoading = state == ProvisioningState.PREPARING ||
                        state == ProvisioningState.ACTIVE
                layout.apply {
                    layoutRootCertificate.wrapperFilePicker.isEnabled = state <= ProvisioningState.PAUSED
                    progressIndicatorProvisioning.isInvisible = !isLoading
                }
            }
        }
    }

    private fun onProvisioningComplete() {
        MeshToast.show(requireContext(), getString(R.string.provisioning_completed))
        val deviceFragment = DeviceFragment.firstConfigurationInstance(viewModel.provisionedDevice)
        requireMainActivity().showFragment(deviceFragment)
    }

    private fun onCertificateFileSelected(result: Result<CertificateFile>?) {
        result?.fold(
                onFailure = {
                    Logger.error(it) { it.message }
                    lifecycleScope.launch {
                        withResumed {
                            showErrorDialog(Message.error(R.string.error_process_selected_file))
                        }
                    }
                },
                onSuccess = { viewModel.setRootCertificateFile(it) }
        )
    }
}