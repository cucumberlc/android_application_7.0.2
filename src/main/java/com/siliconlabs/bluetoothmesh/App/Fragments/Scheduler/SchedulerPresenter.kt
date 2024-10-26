/*
 * Copyright © 2021 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.Scheduler

import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.functionality_control.scheduler.Action
import com.siliconlab.bluetoothmesh.adk.functionality_control.scheduler.SchedulerClient
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.Presenters.Scheduler.SchedulerParams
import com.siliconlabs.bluetoothmesh.App.Fragments.DeviceList.ViewHolders.DeviceViewHolderBase
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.findSigModel
import com.siliconlabs.bluetoothmesh.App.Utils.defaultTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.responseTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.tinylog.kotlin.Logger
import javax.inject.Inject

@HiltViewModel
class SchedulerPresenter @Inject constructor(
    val meshNode: MeshNode
) : BasePresenter<SchedulerView>() {
    private val listener
        get() = view

    fun refreshScheduleRegister(refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener) {
        val schedulerServer = meshNode.findSigModel(ModelIdentifier.SchedulerServer)

        schedulerServer?.let { model ->
            SchedulerClient.get(
                model.element.address,
                model.boundAppKeys.first(),
            ).onFailure {
                listener?.showToast(it)
                return
            }
            refreshNodeListener.startRefresh()

            viewModelScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SchedulerClient.schedulerResponse.first()
                }
                Logger.debug { "refreshScheduleRegister $response" }
                refreshNodeListener.stopRefresh()
                response?.let {
                    meshNode.schedules = response.schedules.toMutableList()
                    listener?.notifyItemChanged(meshNode)
                } ?: run {
                    listener?.showToast(responseTimeout)
                    listener?.notifyItemChanged(meshNode)
                }
            }
        }
    }

    fun refreshSchedulerAction(
        index: Int,
        refreshNodeListener: DeviceViewHolderBase.RefreshNodeListener
    ) {
        val schedulerServer = meshNode.findSigModel(ModelIdentifier.SchedulerServer)

        schedulerServer?.let { model ->
            SchedulerClient.getAction(
                model.element.address,
                model.boundAppKeys.first(),
                index,
            ).onFailure {
                listener?.showToast(it)
                return
            }
            refreshNodeListener.startRefresh()

            viewModelScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SchedulerClient.schedulerActionResponse.first()
                }
                Logger.debug { "refreshSchedulerAction $response" }
                refreshNodeListener.stopRefresh()
                response?.let {
                    meshNode.scheduleRegister[response.index] = response
                    meshNode.schedules[response.index.toInt()] = response.action != Action.NoAction
                    listener?.notifyItemChanged(meshNode)
                } ?: listener?.showToast(responseTimeout)
            }
        }
    }

    fun onSchedulerSetButtonClick(schedulerParams: SchedulerParams) {
        schedulerParams.validate()?.let {
            listener?.showToast(it)
            return
        }
        val schedulerServer = meshNode.findSigModel(ModelIdentifier.SchedulerServer)
        schedulerServer?.let { model ->
            SchedulerClient.setAction(
                model.element.address,
                model.boundAppKeys.first(),
                true,
                schedulerParams.index,
                schedulerParams.year,
                schedulerParams.months,
                schedulerParams.day,
                schedulerParams.hour,
                schedulerParams.minute,
                schedulerParams.second,
                schedulerParams.daysOfWeek,
                schedulerParams.action,
                schedulerParams.transitionTime,
                schedulerParams.scene,
            ).onFailure {
                listener?.showToast(it)
                return
            }
            viewModelScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    SchedulerClient.schedulerActionResponse.first()
                }
                Logger.debug { "onSchedulerSetButtonClick $response" }

                response?.let {
                    listener?.showToast("Success setting action")
                    meshNode.scheduleRegister[response.index] = response
                    meshNode.schedules[response.index.toInt()] = response.action != Action.NoAction
                    listener?.notifyItemChanged(meshNode)
                } ?: listener?.showToast(responseTimeout)
            }
        }
    }
}
