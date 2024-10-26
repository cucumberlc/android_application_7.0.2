/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import android.os.*
import java.util.*

fun UUID.toParcelUuid() = ParcelUuid(this)

// compat impl
inline fun <reified T: Parcelable> Parcel.readParcelable() = readParcelableCompat(T::class.java)

@Suppress("DEPRECATION")
@PublishedApi
internal fun <T>Parcel.readParcelableCompat(clazz: Class<T>) : T? {
    return if(Build.VERSION.SDK_INT >= 33){
        readParcelable(clazz.classLoader, clazz)
    } else {
        readParcelable(clazz.classLoader) as T?
    }
}