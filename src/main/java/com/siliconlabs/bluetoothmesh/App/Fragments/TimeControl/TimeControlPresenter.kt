/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.TimeControl

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.siliconlab.bluetoothmesh.adk.data_model.model.ModelIdentifier
import com.siliconlab.bluetoothmesh.adk.functionality_control.time.Role
import com.siliconlab.bluetoothmesh.adk.functionality_control.time.TimeClient
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlabs.bluetoothmesh.App.BasePresenter
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Utils.apiExtensions.findSigModel
import com.siliconlabs.bluetoothmesh.App.Utils.defaultTimeout
import com.siliconlabs.bluetoothmesh.App.Utils.responseTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class TimeControlPresenter @Inject constructor(
    val meshNode: MeshNode
) : BasePresenter<TimeControlView>() {

    fun getTimeRole() {
        val timeServer = meshNode.findSigModel(ModelIdentifier.TimeServer)
        timeServer?.let { model ->
            TimeClient.getTimeRole(
                model.element.address,
                model.boundAppKeys.first(),
            ).onFailure {
                view?.showToast(it)
                return
            }
            viewModelScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    TimeClient.roleResponse.first()
                }

                response?.let {
                    meshNode.timeRole = response.role.toString()
                    view?.notifyItemChanged(meshNode)
                    view?.showToast("Success getting time role")
                } ?: view?.showToast(responseTimeout)
            }
        }
    }

    fun setTimeRole(role: Role) {
        val timeServer = meshNode.findSigModel(ModelIdentifier.TimeServer)
        timeServer?.let { model ->
            TimeClient.setTimeRole(
                model.element.address,
                model.boundAppKeys.first(),
                role,
            ).onFailure {
                view?.showToast(it)
                return
            }
            viewModelScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    TimeClient.roleResponse.first()
                }

                response?.let {
                    meshNode.timeRole = response.role.toString()
                    view?.showToast("Success setting time role")
                    view?.notifyItemChanged(meshNode)
                } ?: view?.showToast(responseTimeout)
            }
        }
    }

    fun getTime() {
        val timeServer = meshNode.findSigModel(ModelIdentifier.TimeServer)
        timeServer?.let { model ->
            TimeClient.getTime(
                model.element.address,
                model.boundAppKeys.first(),
            ).onFailure {
                view?.showToast(it)
                return
            }
            viewModelScope.launch {
                val response = withTimeoutOrNull(defaultTimeout) {
                    TimeClient.timeResponse.first()
                }

                response?.let {
                    view?.showToast("Success getting time")
                    meshNode.taiSeconds = response.taiSeconds
                    meshNode.subsecond = response.subsecond
                    meshNode.uncertainty = response.uncertainty
                    meshNode.timeAuthority = response.timeAuthority
                    meshNode.taiUtcDelta = response.taiUtcDelta
                    meshNode.timeZoneOffset = response.timeZoneOffset
                    view?.notifyItemChanged(meshNode)
                } ?: view?.showToast(responseTimeout)
            }
        }
    }

    fun setTime(
        timeValuesMap: Map<TimeParams.ParameterType, String>,
        context: Context,
    ) {
        try {
            val taiSeconds = timeValuesMap[TimeParams.ParameterType.TAI_SECONDS]!!.toLong()
            val subsecond = timeValuesMap[TimeParams.ParameterType.SUBSECOND]!!.toInt()
            val uncertainty = timeValuesMap[TimeParams.ParameterType.UNCERTAINTY]!!.toInt()
            val timeAuthority = timeValuesMap[TimeParams.ParameterType.TIME_AUTHORITY]!!.toInt()
            val taiUtcDelta = timeValuesMap[TimeParams.ParameterType.TAI_UTC_DELTA]!!.toInt()
            val timeZoneOffset =
                timeValuesMap[TimeParams.ParameterType.TIME_ZONE_OFFSET]!!.toShort()

            val timeParams = TimeParams(
                taiSeconds,
                subsecond,
                uncertainty,
                timeAuthority,
                taiUtcDelta,
                timeZoneOffset,
            )
            val validationInfo = timeParams.validate(context)
            if (validationInfo.isNotEmpty()) {
                view?.showToast(validationInfo)
                return
            }
            val timeServer = meshNode.findSigModel(ModelIdentifier.TimeServer)
            timeServer?.let { model ->
                TimeClient.setTime(
                    model.element.address,
                    model.boundAppKeys.first(),
                    taiSeconds,
                    subsecond,
                    uncertainty,
                    timeAuthority == 1,
                    taiUtcDelta,
                    timeZoneOffset,
                ).onFailure {
                    view?.showToast(it)
                    return
                }
                viewModelScope.launch {
                    val response = withTimeoutOrNull(defaultTimeout) {
                        TimeClient.timeResponse.first()
                    }

                    response?.let {
                        view?.showToast("Success setting time")
                        meshNode.taiSeconds = response.taiSeconds
                        meshNode.subsecond = response.subsecond
                        meshNode.uncertainty = response.uncertainty
                        meshNode.timeAuthority = response.timeAuthority
                        meshNode.taiUtcDelta = response.taiUtcDelta
                        meshNode.timeZoneOffset = response.timeZoneOffset
                        view?.notifyItemChanged(meshNode)
                    } ?: view?.showToast(responseTimeout)
                }
            }
        } catch (e: Exception) {
            view?.showToast("Invalid input")
        }
    }
}
