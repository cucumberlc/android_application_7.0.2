/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.errors.StackError
import com.siliconlab.bluetoothmesh.adk.functionality_control.scene.Status
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.DeviceListAdapterLogic
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Client.CTLClientViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Client.LevelClientViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Client.LightnessClientViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Client.OnOffClientViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.CTLViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.DistributionViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.LevelViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.LightLCViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.LightnessViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.OnOffViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.SceneViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.TimeSchedulerViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.Server.TimeViewHolder
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.UnknownViewHolder
import com.siliconlabs.bluetoothmesh.App.Logic.NetworkConnectionLogic
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.DeviceFunctionality
import com.siliconlabs.bluetoothmesh.App.Utils.Message
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.SwipeRecyclerAdapter
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterCtlBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterDefaultBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterDistributionBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterLightLcBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterLightnessBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterSceneBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterTimeBinding
import com.siliconlabs.bluetoothmesh.databinding.DevicesAdapterTimeSchedulerBinding
import kotlinx.coroutines.CoroutineScope

class DeviceListAdapter(
    val context: Context,
    deviceListAdapterListener: DeviceListAdapterListener,
    private val networkConnectionLogic: NetworkConnectionLogic,
    coroutineScope: CoroutineScope,
) : DeviceListAdapterLogic.DeviceListAdapterLogicListener, SwipeRecyclerAdapter<MeshNode, DeviceViewHolderBase>(DeviceInfoComparator()) {

    private val deviceListLogic = DeviceListAdapterLogic(this, deviceListAdapterListener, coroutineScope)
    var listener: View.OnClickListener? = null

    override fun getItemViewType(position: Int): Int {
        return getItem(position).functionality.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolderBase {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (DeviceFunctionality.FUNCTIONALITY.values()[viewType]) {
            // server
            DeviceFunctionality.FUNCTIONALITY.OnOff -> {
                val layout = DevicesAdapterDefaultBinding.inflate(layoutInflater, parent, false)
                OnOffViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.Level -> {
                val layout = DevicesAdapterLightnessBinding.inflate(layoutInflater, parent, false)
                LevelViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.Lightness -> {
                val layout = DevicesAdapterLightnessBinding.inflate(layoutInflater, parent, false)
                LightnessViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.CTL -> {
                val layout = DevicesAdapterCtlBinding.inflate(layoutInflater, parent, false)
                CTLViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.LightLCServer -> {
                val layout = DevicesAdapterLightLcBinding.inflate(layoutInflater, parent, false)
                LightLCViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.SceneServer -> {
                val layout = DevicesAdapterSceneBinding.inflate(layoutInflater, parent, false)
                SceneViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.Scheduler -> {
                val view = DevicesAdapterTimeSchedulerBinding.inflate(layoutInflater, parent, false)
                TimeSchedulerViewHolder(view, deviceListLogic)
            }
            // client
            DeviceFunctionality.FUNCTIONALITY.OnOffClient -> {
                val layout = DevicesAdapterDefaultBinding.inflate(layoutInflater, parent, false)
                OnOffClientViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.LevelClient -> {
                val layout = DevicesAdapterDefaultBinding.inflate(layoutInflater, parent, false)
                LevelClientViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.LightnessClient -> {
                val layout = DevicesAdapterDefaultBinding.inflate(layoutInflater, parent, false)
                LightnessClientViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.CTLClient -> {
                val layout = DevicesAdapterDefaultBinding.inflate(layoutInflater, parent, false)
                CTLClientViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.TimeServer -> {
                val layout = DevicesAdapterTimeBinding.inflate(layoutInflater, parent, false)
                TimeViewHolder(layout, deviceListLogic)
            }
            DeviceFunctionality.FUNCTIONALITY.DistributorServer -> {
                val layout =
                    DevicesAdapterDistributionBinding.inflate(layoutInflater, parent, false)
                DistributionViewHolder(layout, deviceListLogic)
            }
            else -> {
                val layout = DevicesAdapterDefaultBinding.inflate(layoutInflater, parent, false)
                UnknownViewHolder(layout, deviceListLogic)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: DeviceViewHolderBase, position: Int) {
        val device = getItem(position)
        viewHolder.bindView(device, networkConnectionLogic.isConnected())
        mItemManger.bindView(viewHolder.view, position)
    }

    interface DeviceListAdapterListener {
        fun onDeleteClicked(node: Node)
        fun onConfigureClicked(meshNode: MeshNode)
        fun onFunctionalityClicked(
            meshNode: MeshNode,
            functionality: DeviceFunctionality.FUNCTIONALITY
        )

        fun onUpdateFirmwareClick(node: Node)
        fun onRemoteProvisionClick(node: Node)
    }

    // Comparator

    class DeviceInfoComparator : Comparator<MeshNode> {

        override fun compare(o1: MeshNode, o2: MeshNode): Int {
            return o1.node.devKey.index.compareTo(o2.node.devKey.index)
        }
    }

    //DeviceListAdapterLogicListener

    fun showToast(@StringRes messageId: Int) {
        MeshToast.show(context, messageId)
    }

    fun showToast(@StringRes messageId: Int, arg1: String, arg2: String) {
        val messageString = context.getString(messageId, arg1, arg2)
        MeshToast.show(context, messageString)
    }

    fun showToast(message: String) {
        MeshToast.show(context, message)
    }

    fun showToast(error: StackError) {
        MeshToast.show(context, error.toString())
    }

    override fun showToast(message : Message){
        MeshToast.show(context, message.message)
    }

    override fun showToast(sceneStatusCode: Status) {
        val stringResource = when (sceneStatusCode) {
            Status.Success -> R.string.device_adapter_scenes_success_status
            Status.NotFound -> R.string.device_adapter_scenes_not_found_status
            Status.Full -> R.string.device_adapter_scenes_register_full_status
            else -> R.string.device_adapter_scenes_wrong_status
        }
        MeshToast.show(context, stringResource)
    }
}