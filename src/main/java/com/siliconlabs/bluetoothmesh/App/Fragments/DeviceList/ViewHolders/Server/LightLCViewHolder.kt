/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.LC.LightLCProperty
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterLightLcBinding

class LightLCViewHolder(private val layout: DevicesAdapterLightLcBinding, deviceListLogic: DeviceListAdapterLogic)
    : DeviceViewHolderBase(
        layout.root,
        layout.devicesAdapterBaseHeader,
        layout.devicesAdapterBaseScene,
        layout.devicesAdapterBaseSwipeMenu,
        layout.devicesAdapterBaseRemoteProvisioning,
        deviceListLogic,
) {

    private val spinnerAdapter = ArrayAdapter(view.context, R.layout.spinner_item_dark, LightLCProperty.values())
    override fun bindView(meshNode: MeshNode, isNetworkConnected: Boolean) {
        super.bindView(meshNode, isNetworkConnected)
        bindLightLC(meshNode, isNetworkConnected)
        bindBaseScene()
        bindBaseHeader()
    }

    private fun bindLightLC(meshNode: MeshNode, isNetworkConnected: Boolean) {
        layout.apply {
            swipe.setup(meshNode)

            swLcMode.setOnCheckedChangeListener(null)
            swLcOccupancyMode.setOnCheckedChangeListener(null)
            swLcOnOff.setOnCheckedChangeListener(null)
            swLcMode.isChecked = meshNode.lcMode
            swLcOccupancyMode.isChecked = meshNode.lcOccupancyMode
            swLcOnOff.isChecked = meshNode.lcOnOff
            tvLcPropertyValue.text = meshNode.lcPropertyValue
            spPropertyId.adapter = spinnerAdapter

            spPropertyId.setSelection(meshNode.lcProperty.ordinal,false)

            spPropertyId.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {
                }

                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                    val selectedProperty = LightLCProperty.values()[position]
                    if (meshNode.lcProperty == selectedProperty)
                        return

                    etLcPropertyData.setText("")
                    tvLcPropertyValue.text = "---"
                    meshNode.lcPropertyValue = "---"
                    meshNode.lcProperty = selectedProperty

                    when (selectedProperty.characteristic) {
                        LightLCProperty.Characteristic.Illuminance -> {
                            etLcPropertyData.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            tvLcPropertyUnit.text = view?.context?.getString(R.string.device_adapter_lc_illuminance_unit)
                        }
                        LightLCProperty.Characteristic.PerceivedLightness -> {
                            etLcPropertyData.inputType = InputType.TYPE_CLASS_NUMBER
                            tvLcPropertyUnit.text = ""
                        }
                        LightLCProperty.Characteristic.Percentage8 -> {
                            etLcPropertyData.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            tvLcPropertyUnit.text = view?.context?.getString(R.string.device_adapter_lc_percentage_unit)
                        }
                        LightLCProperty.Characteristic.Coefficient -> {
                            etLcPropertyData.inputType =
                                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                            tvLcPropertyUnit.text = ""
                        }
                        LightLCProperty.Characteristic.TimeMillisecond24 -> {
                            etLcPropertyData.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            tvLcPropertyUnit.text = view?.context?.getString(R.string.device_adapter_lc_time_unit)
                        }
                    }
                }
            }

            //mode
            ivLcModeRefresh.setOnClickListener {
                deviceListLogic.lcLogic.refreshLCMode(meshNode, RefreshNodeListener(ivLcModeRefresh))
            }
            swLcMode.setOnCheckedChangeListener { _, isChecked ->
                deviceListLogic.lcLogic.setLCMode(meshNode, isChecked)
            }
            //occupancy mode
            ivLcOccupancyModeRefresh.setOnClickListener {
                deviceListLogic.lcLogic.refreshLCOccupancyMode(meshNode, RefreshNodeListener(ivLcOccupancyModeRefresh))
            }
            swLcOccupancyMode.setOnCheckedChangeListener { _, isChecked ->
                deviceListLogic.lcLogic.setLCOccupancyMode(meshNode, isChecked)
            }
            //on off
            ivLcOnOffRefresh.setOnClickListener {
                deviceListLogic.lcLogic.refreshLCLightOnOff(meshNode, RefreshNodeListener(ivLcOnOffRefresh))
            }
            swLcOnOff.setOnCheckedChangeListener { _, isChecked ->
                deviceListLogic.lcLogic.setLCOnOff(meshNode, isChecked)
            }
            //property
            ivLcPropertyRefresh.setOnClickListener {
                deviceListLogic.lcLogic.refreshLCProperty(meshNode, RefreshNodeListener(ivLcPropertyRefresh))
            }
            btnLcPropertySend.setOnClickListener {
                deviceListLogic.lcLogic.setLCProperty(meshNode, etLcPropertyData.text.toString())
            }

            setEnabledControls(isNetworkConnected)
        }
    }

    private fun bindBaseScene() {
        layout.devicesAdapterBaseScene.apply {
            scenesLayout.visibility = View.VISIBLE
        }
    }

    private fun bindBaseHeader() {
        layout.devicesAdapterBaseHeader.apply {
            ivDeviceImage.visibility = View.GONE
        }
    }
}