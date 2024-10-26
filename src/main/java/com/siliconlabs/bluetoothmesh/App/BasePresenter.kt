/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App

import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import kotlin.properties.ReadOnlyProperty

// this is just a workaround until presenters migrate to ViewModels by implementing state emitting logic
// this is done because Hilt provides constructor injection and component handling for ViewModel classes
abstract class BasePresenter<VIEW : PresenterView> : ViewModel() {
    protected var view: VIEW? = null
        private set

    fun installView(view: VIEW, lifecycleOwner: LifecycleOwner) {
        this.view = view
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
    }

    @CallSuper
    override fun onCleared() {
        view = null
    }

    protected open fun onPause() {}
    protected open fun onResume() {}

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> onResume()
            Lifecycle.Event.ON_PAUSE -> onPause()
            Lifecycle.Event.ON_DESTROY -> view = null
            else -> {}
        }
    }
}

interface PresenterView

// custom delegate to use to ensure fragments inject their views
@MainThread
inline fun <reified V : PresenterView, reified VM : BasePresenter<V>> Fragment.presenters(): ReadOnlyProperty<Fragment, VM> {
    require(this is V) { "${this.javaClass} must implement presenters view : PresenterView${V::class.java}" }
    val injectionDelegate = lazy {
        viewModels<VM>().value.also {
            it.installView(this, this)
        }
    }
    // enforce creation so view is injected
    lifecycleScope.launch {
        withCreated { injectionDelegate.value }
    }

    return ReadOnlyProperty { _, _ -> injectionDelegate.value }
}

// lint so by viewModels is not used accidentally
@Suppress("unused")
@Deprecated(
    "This will not inject view and lifecycle into presenter.",
    ReplaceWith("presenters()"),
    DeprecationLevel.ERROR
)
inline fun <reified VM : BasePresenter<*>> Fragment.viewModels(): Lazy<VM> {
    throw IllegalArgumentException()
}