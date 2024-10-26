/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Device.Info

import android.os.Bundle
import android.view.View
import android.widget.TableRow
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.model.SigModel
import com.siliconlab.bluetoothmesh.adk.data_model.model.VendorModel
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.DevicePresenter
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.encodeHex
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.viewLifecycleScope
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.FragmentDeviceInfoBinding
import com.siliconlabs.bluetoothmesh.databinding.TableItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceInfoFragment : Fragment(R.layout.fragment_device_info) {
    private val layout by viewBinding(FragmentDeviceInfoBinding::bind)

    // just hook into DeviceFragments viewModel because this fragment doesn't have its own...
    private val deviceViewModel by viewModels<DevicePresenter>(
        ownerProducer = { requireParentFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val chosenNode = deviceViewModel.meshNode
        setupView(chosenNode.node)
        fillModelsTable(chosenNode)
    }

    private fun setupView(node: Node) {
        layout.apply {
            textViewDeviceName.text = node.name
            textViewDeviceAddress.text = node.primaryElementAddress.toString()
            textViewDeviceUuid.text = node.uuid.toString()

            val subnet = node.subnets.first()
            textViewNetkeyIndex.text = subnet.netKey.index.toString()
            textViewNetKey.text = subnet.netKey.key.encodeHex(separator = " ")
            if (node.boundAppKeys.isNotEmpty()) {
                textViewAppKey.text = node.boundAppKeys.first().key.encodeHex(separator = " ")
            }
            textViewDevKey.text = node.devKey.key.encodeHex(separator = " ")
        }
    }

    private fun fillModelsTable(deviceInfo: MeshNode) {
        var tableIndex = 0
        viewLifecycleScope.launch(Dispatchers.Default) {
            deviceInfo.node.elements.forEachIndexed { elementIndex, element ->
                val models = mutableListOf<Model>()
                models.addAll(element!!.sigModels)
                models.addAll(element!!.vendorModels)

                models.forEach { model ->
                    val modelName = model.name

                    var modelType = "SIG"
                    var modelId = "0x0000"

                when (model) {
                    is SigModel -> {
                        modelType = "SIG"
                        modelId = model.identifier.toShort().encodeHex()
                    }
                    is VendorModel -> {
                        modelType = model.vendorAssignedModelIdentifier.toShort().encodeHex()
                        modelId = model.vendorCompanyIdentifier.toShort().encodeHex()
                    }
                }

                    val modelInfo = DeviceModelInfo(elementIndex, modelType, modelId, modelName)
                    val rowElement = createRowElement(modelInfo, tableIndex % 2 == 1)
                    tableIndex++
                    launch(Dispatchers.Main) {
                        layout.tableElements.addView(rowElement)
                    }
                }
            }
        }
    }

    private fun createRowElement(modelInfo: DeviceModelInfo, lightBackground: Boolean): TableRow {
        val tableItem = TableItemBinding.inflate(layoutInflater)
        tableItem.apply {
            if (lightBackground) {
                root.background = AppCompatResources.getDrawable(
                    requireContext(),
                    R.color.dialog_device_config_table_light_background
                )
            }

            rowElement.text = modelInfo.elementIndex.toString()
            rowVendor.text = modelInfo.modelType
            rowId.text = modelInfo.modelId
            rowDescription.text = modelInfo.modelName
            rowDescription.isSelected = true
        }

        return tableItem.root
    }

    private data class DeviceModelInfo(
        val elementIndex: Int,
        val modelType: String,
        val modelId: String,
        val modelName: String,
    )
}