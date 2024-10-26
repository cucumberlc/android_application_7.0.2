/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App

import com.siliconlabs.bluetoothmesh.App.Logic.*
import com.siliconlabs.bluetoothmesh.App.Logic.provision.ProvisioningLogic
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AppModule {
    @Provides
    @Singleton
    fun provideNetworkConnectionLogic(): NetworkConnectionLogic {
        return NetworkConnectionLogic()
    }

    @Provides
    @Singleton
    fun provideProvisioningLogic(networkConnectionLogic: NetworkConnectionLogic) =
        ProvisioningLogic(networkConnectionLogic)
}