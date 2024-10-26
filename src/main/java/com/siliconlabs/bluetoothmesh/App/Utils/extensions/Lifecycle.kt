/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

val Fragment.viewLifecycleScope
    get() = viewLifecycleOwner.lifecycleScope

fun LifecycleOwner.launchAndRepeatOnLifecycle(
    state: Lifecycle.State,
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    action: suspend CoroutineScope.() -> Unit
) = lifecycleScope.launch(context, start) { repeatOnLifecycle(state, action) }

/**
 * Collapsed call of
 * ```kotlin
 * lifecycleOwner.lifecycleScope.launch {
 *      lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
 *          action()
 *      }
 * }
 * ```
 * */
fun LifecycleOwner.launchAndRepeatWhenResumed(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    action: suspend CoroutineScope.() -> Unit
) = launchAndRepeatOnLifecycle(Lifecycle.State.RESUMED, context, start, action)