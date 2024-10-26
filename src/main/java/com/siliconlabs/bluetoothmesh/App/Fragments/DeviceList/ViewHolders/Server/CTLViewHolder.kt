/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server

import android.view.View
import android.widget.SeekBar
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.ControlConverters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterCtlBinding

class CTLViewHolder(private val layout: DevicesAdapterCtlBinding, deviceListLogic: DeviceListAdapterLogic)
    : DeviceViewHolderBase(
        layout.root,
        layout.devicesAdapterBaseHeader,
        layout.devicesAdapterBaseScene,
        layout.devicesAdapterBaseSwipeMenu,
        layout.devicesAdapterBaseRemoteProvisioning,
        deviceListLogic,
) {

    override fun bindView(meshNode: MeshNode, isNetworkConnected: Boolean) {
        super.bindView(meshNode, isNetworkConnected)

        bindCTL(meshNode)
        bindBaseScene()
        bindBaseHeader(meshNode, isNetworkConnected)

        setEnabledControls(isNetworkConnected)
    }

    private fun bindCTL(meshNode: MeshNode) {
        layout.apply {
            swipe.setup(meshNode)
            sbLevelControl.progress = meshNode.lightnessPercentage
            tvLevelValue.text = view.context.getString(R.string.device_adapter_lightness_value).format(sbLevelControl.progress)

            sbTemperatureControl.progress = meshNode.temperature
            tvTemperatureValue.text = view.context.getString(R.string.device_adapter_temperature_value).format(meshNode.temperature)

            sbUvControl.progress = meshNode.deltaUvPercentage
            tvUvValue.text = view.context.getString(R.string.device_adapter_delta_uv_value)
                .format(ControlConverters.getDeltaUvToShow(meshNode.deltaUvPercentage))

            sbLevelControl.setOnSeekBarChangeListener(CTLControlChangeListener(meshNode, sbLevelControl, sbTemperatureControl, sbUvControl))
            sbTemperatureControl.setOnSeekBarChangeListener(CTLControlChangeListener(meshNode, sbLevelControl, sbTemperatureControl, sbUvControl))
            sbUvControl.setOnSeekBarChangeListener(CTLControlChangeListener(meshNode, sbLevelControl, sbTemperatureControl, sbUvControl))
        }
    }

    private fun bindBaseScene() {
        layout.devicesAdapterBaseScene.apply {
            scenesLayout.visibility = View.VISIBLE
        }
    }

    private fun bindBaseHeader(meshNode: MeshNode, isNetworkConnected: Boolean) {
        layout.devicesAdapterBaseHeader.apply {
            ivRefresh.visibility = View.VISIBLE
            ivDeviceImage.setOnClickListener(ClickDeviceImageListener(meshNode))
            ivRefresh.setOnClickListener(ClickRefreshListener(meshNode, ivRefresh))

            if (!isNetworkConnected) {
                ivDeviceImage.setImageResource(R.drawable.lamp_disabled)
            } else if (meshNode.lightnessPercentage > 0) {
                ivDeviceImage.setImageResource(R.drawable.lamp_on)
            } else {
                ivDeviceImage.setImageResource(R.drawable.lamp_off)
            }
        }
    }

    inner class CTLControlChangeListener(private val deviceInfo: MeshNode, private val levelSeekBar: SeekBar,
                                         private val temperatureSeekBar: SeekBar, private val uvSeekBar: SeekBar) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.let {
                deviceListLogic.onSeekBarChange(deviceInfo, levelSeekBar.progress, temperatureSeekBar.progress, uvSeekBar.progress)
            }
        }
    }
}