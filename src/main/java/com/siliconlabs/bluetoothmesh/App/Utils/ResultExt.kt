/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils

inline fun <R, T> Result<T>.flatMap(transform: (value: T) -> Result<R>): Result<R> {
    val value = getOrElse { return Result.failure(it) }
    return transform(value)
}

infix fun <A, B> Result<A>.with(other: Result<B>): Result<Pair<A, B>> {
    val first = this.getOrElse { return Result.failure(it) }
    val second = other.getOrElse { return Result.failure(it) }

    return Result.success(Pair(first, second))
}

infix fun <A, B, C> Result<Pair<A, B>>.and(other: Result<C>): Result<Triple<A, B, C>> {
    val pair = this.getOrElse { return Result.failure(it) }
    val third = other.getOrElse { return Result.failure(it) }

    return Result.success(Triple(pair.first, pair.second, third))
}