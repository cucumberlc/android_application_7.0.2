/*
 * Copyright © 2024 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.VendorModelBuilder

import com.siliconlab.bluetoothmesh.adk.data_model.address.Address
import com.siliconlab.bluetoothmesh.adk.data_model.group.Group
import com.siliconlab.bluetoothmesh.adk.data_model.model.Model
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.errors.NodeControlError
import com.siliconlab.bluetoothmesh.adk.functionality_binder.FunctionalityBinder
import com.siliconlab.bluetoothmesh.adk.functionality_binder.FunctionalityBinderCallback
import com.siliconlab.bluetoothmesh.adk.functionality_control.LocalSubscription
import com.siliconlab.bluetoothmesh.adk.functionality_control.publication.Credentials
import com.siliconlab.bluetoothmesh.adk.functionality_control.publication.Publication
import com.siliconlab.bluetoothmesh.adk.functionality_control.subscription.Subscription
import com.siliconlab.bluetoothmesh.adk.functionality_control.vendor_model.LocalVendorModel
import com.siliconlab.bluetoothmesh.adk.internal.data_model.model.AddressImpl
import com.siliconlab.bluetoothmesh.adk.internal.data_model.model.PublishImpl
import com.siliconlab.bluetoothmesh.adk.internal.data_model.model.RetransmitImpl
import com.siliconlab.bluetoothmesh.adk.internal.util.Utils
import com.siliconlab.bluetoothmesh.adk.isSuccess
import com.siliconlab.bluetoothmesh.adk.notification_control.settings.SubscriptionSettings
import com.siliconlab.bluetoothmesh.adk.onFailure
import com.siliconlab.bluetoothmesh.adk.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VendorModelHelper() {
    lateinit var group : Group
    lateinit var model : Model
    lateinit var appKey : AppKey
    private val taskList = mutableListOf<Runnable>()
    private var currentTask = Runnable { }

    constructor(appKey: AppKey, model: Model, group: Group) : this() {
        this.group = group
        this.model = model
        this.appKey = appKey
    }

    /**
     * Get the list of supported list of vendor models and use when mesh initilization.
     * **/
    fun getSupportedVendorModelsList(): Set<LocalVendorModel> {
        return setOf(
            LocalVendorModel(0,
                0x02ff,
                0x1111
            ), LocalVendorModel(0,
                0x02ff,
                0x2222
            )
        )
    }

    /**
     * Bind Vendor Model with APP Key
     * **/
    fun bindModelToAppKey() {
        val functionalityBinder = FunctionalityBinder(appKey)
        functionalityBinder.bindModel(model, VendorModelFunctionalityBinderCallback())
    }

    /**
     * Register Vendor Model with opp codes
     * **/
    fun registerVendorModel(localVendorModel : LocalVendorModel) = Runnable{
        var opCodes: ByteArray = byteArrayOf(0xC1.toByte(), 0xC2.toByte(), 0xC3.toByte())
        localVendorModel.register(opCodes).onSuccess {
            println("Vendor Model Registration success")
            takeNextTask()
        }.onFailure {
            println("Vendor Model Registration Failure : ${it.copy()}")
        }
        incomingMessageHandler()
    }

    /**
     * Handling incoming message
     * **/
    fun incomingMessageHandler(){
        GlobalScope.launch {
            LocalVendorModel.vendorModelRegistrationResponse.collect{
                println(
                    "Message offset 3 = ${Utils.convertUint32ToInt(it.message, 3)}")
            }
        }
    }

    /**
     * Add publish
     * **/
    fun addPublication() = Runnable{
        val period: UByte = 0u
        val defaultTtl: UByte = 5u
        model.modelSettings.publish = PublishImpl(
            AddressImpl(group.address.value),
            defaultTtl.toInt(),
            period.toInt(),
            Credentials.MASTER_SECURITY_MATERIALS,
            RetransmitImpl(0, 0),
            appKey.index
        )

        Publication.set(
            model = model,
            publicationAddress = group.address,
            appKey = appKey,
            ttl = defaultTtl,
            period = period,
            retransmissionIntervalSteps = 0,
            retransmissionCount = 0,
            credentials = Credentials.MASTER_SECURITY_MATERIALS,
        ).onFailure {
            println("Publication Failed when set : ${it.reason}" )
        }.onSuccess {
            println("Publication onSuccess when set")
        }

        GlobalScope.launch {
            Publication.publicationResponse.first()
                .onSuccess {
                    println("Publication Success")
                    withContext(Dispatchers.Main) {
                        takeNextTask()
                    }
                }
                .onFailure {
                    println("Publication Failure")
                }
        }
    }


    /**
     * Add subscription
     * **/
    fun addSubscriptionSettings() = Runnable{
        if (model.modelSettings != null) {
            model.modelSettings.addSubscription(SubscriptionSettings(group))
        }

        Subscription.add(model, group.address).onFailure {
            println("Subscription set Failure with : ${it.reason}")
        }.onSuccess {
            println("Subscription set success")
        }

        GlobalScope.launch {
            val response = Subscription.subscriptionResponse.first()
            if (response.status.isSuccess()){
                println("Subscription success")
                withContext(Dispatchers.Main) {
                    takeNextTask()
                }
            }
            else{
                println("Subscription failure")
            }
        }
    }

    /**
     * Subscribe to get notification to mobile
     * **/
    fun registerForNotification() = Runnable{
        LocalSubscription.subscribe(group.address, model)
    }

    /**
     * Sending message to vendor model
     * **/
    fun sendData(appKey: AppKey, localVendorModel : LocalVendorModel, address: Address, ) {
        localVendorModel.send(appKey, address, createMessage(localVendorModel, byteArrayOf(0xC1.toByte())), 0).onSuccess {
            println("Send message success")
        }.onFailure {
            println("Send to server failure : ${it.reason}")
        }
    }

    /**
     * Creating the message to send to vendor model
     * **/
    private fun createMessage(loaclVendorModel: LocalVendorModel, opCode: ByteArray): ByteArray {
        val companyId = loaclVendorModel.companyIdentifier
        val first = (companyId shr 8) and 0x00ff
        val second = companyId and 0x00ff
        var data = opCode
        data = data.plus(second.toByte())
        data = data.plus(first.toByte())
        data = data.plus("message".toByteArray())
        println("data" +
                data.toString())
        return data
    }

    private fun startTasks() {
        if (taskList.size > 0) {
            takeNextTask()
        }
    }

    private fun takeNextTask() {
        if (taskList.isNotEmpty()) {
            currentTask = taskList.first()
            taskList.remove(currentTask)
            currentTask.run()
        }
    }

    /**
     * Callback for all binding the model to appKey. Once the binding is success, register, publish, subscribe
     * and register for vendor model is added to list and called in sequence.
     * **/
    inner class VendorModelFunctionalityBinderCallback : FunctionalityBinderCallback {
        override fun succeed(succeededModels: MutableList<Model>, appKey: AppKey) {
            println("Binding appKey to model success")
            taskList.add(registerVendorModel(LocalVendorModel(0,
                model.element.vendorModels.first().vendorCompanyIdentifier,
                model.element.vendorModels.first().vendorAssignedModelIdentifier)))
            taskList.add(addPublication())
            taskList.add(addSubscriptionSettings())
            taskList.add(registerForNotification())
            startTasks()
        }

        override fun error(
            failedModels: MutableList<Model>,
            appKey: AppKey?,
            error: NodeControlError,
        ) {
            println("Binding appKey to model failure")
        }
    }

}