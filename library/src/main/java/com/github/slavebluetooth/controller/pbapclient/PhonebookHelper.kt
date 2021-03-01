package com.github.slavebluetooth.controller.pbapclient

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.github.recyclerviewutils.HFRefreshLayout
import com.github.recyclerviewutils.MViewHolder
import com.github.recyclerviewutils.QuickSideBarView
import com.github.recyclerviewutils.SimpleAdapter
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.hfpclient.InCallPresenter
import com.github.slavebluetooth.model.BluetoothPhonebook
import com.github.slavebluetooth.utils.HanziToPinyin
import kotlin.collections.ArrayList

class PhonebookHelper(private val context: Context) {

    private val list = ArrayList<BluetoothPhonebook>()
    private val hanziToPinyin: HanziToPinyin by lazy { HanziToPinyin.getInstance() }
    private val listLayoutManager = LinearLayoutManager(context)
    private var refreshLayout: HFRefreshLayout? = null
    private var listRecyclerView: RecyclerView? = null
    private var itemLayoutId = 0
    private var itemNameShowViewId = 0
    private var itemNumberShowViewId = 0
    private var itemLineId = -1
    private var temLineDrawable: Drawable? = null
    private var quickSideBarView: QuickSideBarView? = null

    private var onListItemDataSetListener: ((holder: MViewHolder, phonebook: BluetoothPhonebook,
                                             position: Int) -> Unit)? = null

    private val listAdapter: SimpleAdapter<BluetoothPhonebook> by lazy {
        object : SimpleAdapter<BluetoothPhonebook>(context, itemLayoutId, list) {
            override fun setItemData(holder: MViewHolder, phonebook: BluetoothPhonebook, position: Int) {
                val tvName = holder.getView<TextView>(itemNameShowViewId)
                val tvNumber = holder.getView<TextView>(itemNumberShowViewId)
                tvName.text = phonebook.name
                tvNumber.text = phonebook.number
                if (itemLineId != -1) {
                    holder.setBackground(itemLineId, temLineDrawable)
                }
                onListItemDataSetListener?.invoke(holder, phonebook, position)
            }
        }
    }

    private val onBluetoothPhonebookPullStartListener: ((BluetoothDevice) -> Unit) = {
        list.clear()
        listAdapter.notifyDataSetChanged()
        listRecyclerView?.setOnScrollChangeListener(null)
        quickSideBarView?.visibility = View.GONE
        refreshLayout?.run {
            if (!isRefreshing) {
                showRefreshWithoutInvoke()
            }
        }
    }

    private val onBluetoothPhonebookPullFinishListener:
            ((BluetoothDevice, List<BluetoothPhonebook>) -> Unit) = { _, list ->
        showList(list)
    }

    fun init() {
        PbapClientController.addOnPhonebookPullStartListener(onBluetoothPhonebookPullStartListener)
        PbapClientController.addOnPhonebookPullFinishListener(onBluetoothPhonebookPullFinishListener)
        listRecyclerView?.run {
            layoutManager = listLayoutManager
            adapter = listAdapter

            listAdapter.setOnItemClickListener { _, _, position ->
                InCallPresenter.dial(list[position].number)
            }
        }
        val device = CoreController.getHfpClientConnectedDevice()
        if (device != null) {
            if (PbapClientController.isPhonebookPulling(device)) {
                refreshLayout?.run {
                    if (isInitialized) {
                        refreshLayout?.showRefreshWithoutInvoke()
                    } else {
                        setOnInitializedListener {
                            showRefreshWithoutInvoke()
                        }
                    }
                }
            } else {
                showList(PbapClientController.getPhonebook(device))
            }
        }
    }

    fun release() {
        onListItemDataSetListener = null
        PbapClientController.removeOnPhonebookPullStartListener(onBluetoothPhonebookPullStartListener)
        PbapClientController.removeOnPhonebookPullFinishListener(onBluetoothPhonebookPullFinishListener)
    }

    fun setPhonebookListView(listRecyclerView: RecyclerView, itemLayoutId: Int,
                             listener: ((holder: MViewHolder, phonebook: BluetoothPhonebook,
                                         position: Int) -> Unit)? = null): PhonebookHelper {
        this.listRecyclerView = listRecyclerView
        this.itemLayoutId = itemLayoutId
        this.onListItemDataSetListener = listener
        return this
    }

    fun setDevicesListItem(itemNameShowViewId: Int, itemNumberShowViewId: Int,
                           lineId: Int = -1, lineDrawable: Drawable? = null): PhonebookHelper {
        this.itemNameShowViewId = itemNameShowViewId
        this.itemNumberShowViewId = itemNumberShowViewId
        this.itemLineId = lineId
        this.temLineDrawable = lineDrawable
        return this
    }

    /**
     * 下拉刷新式的刷新
     */
    fun setRefreshView(refreshLayout: HFRefreshLayout): PhonebookHelper {
        this.refreshLayout = refreshLayout
        refreshLayout.run {
            setOnRefreshListener {
                val device = CoreController.getHfpClientConnectedDevice()
                if (device != null) {
                    PbapClientController.pullPhonebook(device)
                }
            }
        }
        return this
    }

    /**
     * @param tipsViewWrapViewTopMargin tipsViewWrapViewTop距离顶部的距离
     */
    fun setQuickSideBarView(quickSideBarView: QuickSideBarView, tipsViewWrapView: FrameLayout,
                            tipsView: TextView, tipsViewWrapViewTopMargin: Int): PhonebookHelper {
        this.quickSideBarView = quickSideBarView
        quickSideBarView.setOnQuickSideBarTouchListener { needShowTips, letter, _, y ->
            tipsViewWrapView.visibility = if (needShowTips) View.VISIBLE else View.INVISIBLE
            if (needShowTips) {
                tipsView.text = letter
                val tvTipsParams = tipsView.layoutParams as FrameLayout.LayoutParams
                tvTipsParams.topMargin = (y - tipsViewWrapViewTopMargin - tipsView.height / 2f).toInt()
                tipsView.layoutParams = tvTipsParams

                val chose = letter[0].toUpperCase()
                for (i in 0 until list.size) {
                    val currentChar0 = hanziToPinyin.transliterate(list[i].name)[0].toUpperCase()
                    if (chose == '#') {
                        if (currentChar0 !in 'A'..'Z') {
                            listLayoutManager.scrollToPositionWithOffset(i, 0)
                            break
                        }
                    } else {
                        if (currentChar0 == chose) {
                            listLayoutManager.scrollToPositionWithOffset(i, 0)
                            break
                        }
                    }
                }
            }
        }
        return this
    }

    private fun showList(list: List<BluetoothPhonebook>) {
        this.list.clear()
        if (list.isNotEmpty()) {
            refreshLayout?.hideEmptyView()
            this.list.addAll(list)
            quickSideBarView?.visibility = View.VISIBLE
        } else {
            refreshLayout?.showEmptyView()
            quickSideBarView?.visibility = View.GONE
        }
        refreshLayout?.run {
            if (isRefreshing) onRefreshFinished(1000)
        }
        listAdapter.notifyDataSetChanged()
    }
}
