/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Views

import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.siliconlabs.bluetoothmesh.R
import java.util.*

abstract class SwipeRecyclerAdapter<T, VH : RecyclerView.ViewHolder?>(private var comparator: Comparator<T>) : RecyclerSwipeAdapter<VH>() {

    private val items: MutableList<T> = mutableListOf()

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.swipe
    }

    override fun getItemCount(): Int {
        return items.size
    }

    fun getItem(position: Int): T {
        return items[position]
    }

    open fun notifyItemChanged(item: T) {
        val index = items.indexOf(item)
        if (index >= 0) {
            notifyItemChanged(index)
        }
    }

    fun setItems(collection: Collection<T>) {
        items.clear()
        items.addAll(collection)
        Collections.sort(items, comparator)
    }
}