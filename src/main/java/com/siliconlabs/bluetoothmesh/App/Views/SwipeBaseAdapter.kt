/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Views

import com.daimajia.swipe.adapters.BaseSwipeAdapter
import com.siliconlabs.bluetoothmesh.R

abstract class SwipeBaseAdapter<T> : BaseSwipeAdapter() {

    private val items: MutableList<T> = mutableListOf()

    override fun getItem(position: Int): T {
        return items[position]
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.swipe
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
        closeAllItems()
    }

    fun setItems(collection: Collection<T>) {
        items.clear()
        items.addAll(collection)
    }

    interface ItemListener<T> {
        fun onDeleteClick(item: T)
        fun onItemClick(item: T)
    }
}
