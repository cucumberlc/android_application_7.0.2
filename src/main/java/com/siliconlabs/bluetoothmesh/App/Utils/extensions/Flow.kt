/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield

fun <T> MutableSharedFlow<T>.hasActiveSubscriptionsFlow() =
    subscriptionCount
        .dropWhile { it == 0 }
        .map { count -> count > 0 }
        .distinctUntilChanged()

/**
 * Combine [conflate] + [collectLatest] and prevent collecting values that are already outdated
 * which sometime happens with [collectLatest].
 */
suspend fun <T> Flow<T>.conflatedCollectLatest(action: suspend (T) -> Unit) =
    conflate().collectLatest {
        yield()
        action(it)
    }

/** Retry collection of this flow if [predicate] returns true. */
fun <T> Flow<T>.retryIf(predicate: suspend (T) -> Boolean) =
    onEach {
        if (predicate(it)) {
            throw RetryRequestException
        }
    }.retry {
        it is RetryRequestException
    }

/** Collect this flow as long as [collector] returns true or restart this flow entirely. */
suspend fun <T> Flow<T>.collectOrRetry(collector: suspend (T) -> Boolean) =
    retryIf { !collector(it) }.collect()

private object RetryRequestException : Exception()