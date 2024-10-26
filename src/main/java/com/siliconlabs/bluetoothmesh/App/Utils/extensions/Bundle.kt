/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Utils.extensions

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

// compat impl
inline fun <reified T: Parcelable> Bundle.getParcelableCompat(key : String) =
    getParcelableCompat(key, T::class.java)

@Suppress("DEPRECATION")
@PublishedApi
internal fun <T : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<T>) : T? {
    return if(Build.VERSION.SDK_INT >= 33){
        getParcelable(key, clazz)
    } else {
        getParcelable(key)
    }
}