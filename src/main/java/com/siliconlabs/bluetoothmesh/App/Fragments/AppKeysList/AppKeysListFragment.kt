/*
 * Copyright © 2019 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Fragments.AppKeysList

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AbsListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.daimajia.swipe.util.Attributes
import com.siliconlab.bluetoothmesh.adk.data_model.node.Node
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.AppKey
import com.siliconlab.bluetoothmesh.adk.data_model.subnet.Subnet
import com.siliconlabs.bluetoothmesh.App.Fragments.ControlGroup.ControlGroupFragment
import com.siliconlabs.bluetoothmesh.App.Fragments.Device.Config.DeviceConfigFragment.Companion.KEY_IS_NLC_CONTROL
import com.siliconlabs.bluetoothmesh.App.Fragments.withMeshNavArg
import com.siliconlabs.bluetoothmesh.App.Navigation.toNavArg
import com.siliconlabs.bluetoothmesh.App.Utils.extensions.requireMainActivity
import com.siliconlabs.bluetoothmesh.App.Views.CustomAlertDialogBuilder
import com.siliconlabs.bluetoothmesh.App.Views.MeshToast
import com.siliconlabs.bluetoothmesh.App.Views.SwipeBaseAdapter
import com.siliconlabs.bluetoothmesh.App.presenters
import com.siliconlabs.bluetoothmesh.R
import com.siliconlabs.bluetoothmesh.databinding.DialogLoadingBinding
import com.siliconlabs.bluetoothmesh.databinding.FragmentAppkeysListBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppKeysListFragment
@Deprecated(
    "use newInstance(Subnet)",
    replaceWith = ReplaceWith("AppKeysListFragment.newInstance(subnet)")
)
constructor() : Fragment(R.layout.fragment_appkeys_list), AppKeysListView,
    SwipeBaseAdapter.ItemListener<AppKey> {
    companion object {
        fun newInstance(subnet: Subnet, isNLC: Boolean) =
            @Suppress("DEPRECATION")
            AppKeysListFragment().withMeshNavArg(subnet.toNavArg()).apply {
                arguments!!.putBoolean(KEY_IS_NLC_CONTROL, isNLC)
            }
    }

    private val layout by viewBinding(FragmentAppkeysListBinding::bind)
    private val appKeysListPresenter: AppKeysListPresenter by presenters()

    private var appKeysAdapter: AppKeysAdapter? = null

    private var loadingDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpView()
        refreshList()
    }

    private fun setUpView() {
        appKeysAdapter = AppKeysAdapter(this).apply {
            mode = Attributes.Mode.Single
        }

        layout.apply {
            listViewAppKeys.adapter = appKeysAdapter
            listViewAppKeys.emptyView = layout.placeholder
            listViewAppKeys.setOnScrollListener(object : AbsListView.OnScrollListener {
                private var lastFirstVisibleItem: Int = 0

                override fun onScroll(
                    view: AbsListView?,
                    firstVisibleItem: Int,
                    visibleItemCount: Int,
                    totalItemCount: Int,
                ) {
                    if (lastFirstVisibleItem < firstVisibleItem) {
                        fabAddAppkey.hide()
                    } else if (lastFirstVisibleItem > firstVisibleItem) {
                        fabAddAppkey.show()
                    }

                    lastFirstVisibleItem = firstVisibleItem
                }

                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                }
            })
            listViewAppKeys.setOnItemClickListener { _, _, position, _ ->
                onItemClick(appKeysAdapter!!.getItem(position))
            }
            fabAddAppkey.setOnClickListener {
                appKeysListPresenter.addAppKey()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        appKeysListPresenter.refreshList()
    }

    override fun onPause() {
        super.onPause()
        appKeysAdapter?.closeAllItems()
    }

    override fun showLoadingDialog() {
        activity?.runOnUiThread {
            val builder = CustomAlertDialogBuilder(requireContext())
            builder.apply {
                setView(DialogLoadingBinding.inflate(layoutInflater).root)
                setCancelable(false)
                setPositiveButton(this@AppKeysListFragment.getString(R.string.dialog_positive_ok)) { _, _ ->
                }
            }

            loadingDialog = builder.create()
            loadingDialog?.apply {
                window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                show()

                getButton(AlertDialog.BUTTON_POSITIVE).visibility = View.GONE
            }
        }
    }

    override fun setRemovingAppKeyMessage(appKeyName: String) {
        activity?.runOnUiThread {
            loadingDialog?.apply {
                if (!isShowing) {
                    return@runOnUiThread
                }

                setMessage(
                    context.getString(
                        R.string.appkey_dialog_loading_text_removing_appkey,
                        appKeyName
                    )
                )
            }
        }
    }

    override fun dismissLoadingDialog() {
        activity?.runOnUiThread {
            loadingDialog?.apply {
                dismiss()
            }
            loadingDialog = null
        }
    }

    override fun refreshAppKeys(appKeys: Set<AppKey>) {
        activity?.runOnUiThread {
            appKeysAdapter?.setItems(appKeys)
            appKeysAdapter?.notifyDataSetChanged()
        }
    }

    override fun showToast(message: String) {
        activity?.runOnUiThread {
            MeshToast.show(requireContext(), message)
        }
    }

    override fun onDeleteClick(item: AppKey) {
        showDeleteAppKeyDialog(item)
    }

    private fun showDeleteAppKeyDialog(appKey: AppKey) {
        activity?.runOnUiThread {
            val builder = AlertDialog.Builder(requireContext())
            builder.apply {
                setTitle(getString(R.string.appkey_dialog_delete_title))
                setPositiveButton(getString(R.string.dialog_positive_delete)) { dialog, _ ->
                    appKeysListPresenter.deleteAppKey(appKey)
                    appKeysAdapter?.closeAllItems()
                    dialog.dismiss()
                }
                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
                    dialog.dismiss()
                }
            }

            val dialog = builder.create()
            dialog.apply {
                show()
            }
        }
    }

    override fun onItemClick(item: AppKey) {
        showControlAppKeyFragment(item)
    }

    private fun showControlAppKeyFragment(appKey: AppKey) {
        val isNLC = requireArguments().getBoolean(KEY_IS_NLC_CONTROL)
        val fragment = ControlGroupFragment.newInstance(appKey, isNLC)
        requireMainActivity().showFragment(fragment)
    }

    override fun showDeleteAppKeyLocallyDialog(appKey: AppKey, failedNodes: List<Node>) {
        activity?.runOnUiThread {
            AlertDialog.Builder(requireContext()).apply {
                setTitle(R.string.appkey_dialog_delete_locally_title)

                setMessage(
                    getString(
                        R.string.appkey_dialog_delete_locally_message,
                        "$failedNodes",
                        appKey.index.toString()
                    )
                )

                setPositiveButton(context.getString(R.string.dialog_positive_delete)) { dialog, _ ->
                    appKeysListPresenter.deleteAppKeyLocally(appKey)
                    dialog.dismiss()
                }

                setNegativeButton(R.string.dialog_negative_cancel) { dialog, _ ->
                    dialog.dismiss()
                }

                create().show()
            }
        }
    }
}
