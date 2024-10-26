/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments

import androidx.lifecycle.SavedStateHandle
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlabs.bluetoothmesh.App.ModelView.MeshNode
import com.siliconlabs.bluetoothmesh.App.Models.MeshNodeManager
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppDestination
import com.siliconlabs.bluetoothmesh.App.Navigation.MeshAppNavigationData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.*

@InstallIn(ViewModelComponent::class)
@Module
class FragmentsModule {
    companion object {
        const val NAV_KEY = "NAV_KEY"
    }
    // note: this module relies on fragments containing navigation arguments that are set by using
    // extensions from FragmentNavigationExt

    @Provides
    @ViewModelScoped
    fun provideMeshAppNavigationDestination(savedStateHandle: SavedStateHandle): MeshAppDestination? {
        return savedStateHandle.get<MeshAppDestination>(NAV_KEY)
    }

    @Provides
    @ViewModelScoped
    fun provideMeshAppNavigationData(destination: MeshAppDestination?): MeshAppNavigationData {
        checkNotNull(destination) {
            "Navigation arguments do not exist, ensure proper action was used or " +
                    "in case of optional arguments fallback to parse nullable " +
                    "MeshAppDestination instead."
        }
        return destination.data
    }

    @Provides
    @ViewModelScoped
    fun provideSubnet(data: MeshAppNavigationData) = data.subnet!!

    @Provides
    @ViewModelScoped
    fun provideAppKey(data: MeshAppNavigationData) = data.appKey!!

    @Provides
    @ViewModelScoped
    fun provideNode(data: MeshAppNavigationData) = data.node!!

    @Provides
    @ViewModelScoped
    fun provideMeshNode(node: Node): MeshNode {
        return MeshNodeManager.getMeshNode(node)
    }
}