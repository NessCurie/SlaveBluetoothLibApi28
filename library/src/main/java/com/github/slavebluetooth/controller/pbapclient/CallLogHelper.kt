package com.github.slavebluetooth.controller.pbapclient

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.provider.CallLog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.TextView
import com.github.recyclerviewutils.HFRefreshLayout
import com.github.recyclerviewutils.MViewHolder
import com.github.recyclerviewutils.SimpleAdapter
import com.github.slavebluetooth.controller.CoreController
import com.github.slavebluetooth.controller.hfpclient.InCallPresenter
import com.github.slavebluetooth.model.BluetoothCallLog
import java.text.SimpleDateFormat

class CallLogHelper(private val context: Context) {

    companion object {
        private const val EXTRA_TIME_PREF_24_HOUR_FORMAT = "android.intent.extra.TIME_PREF_24_HOUR_FORMAT"
        private const val EXTRA_TIME_PREF_VALUE_USE_24_HOUR = 1
    }

    private val list = ArrayList<BluetoothCallLog>()
    private var refreshLayout: HFRefreshLayout? = null
    private var listRecyclerView: RecyclerView? = null
    private var itemLayoutId = 0
    private var itemNameShowViewId = 0
    private var itemTimeShowViewId = 0
    private var itemLineId = -1
    private var incomingDrawable: Drawable? = null
    private var outgoingDrawable: Drawable? = null
    private var missedDrawable: Drawable? = null
    private var timeShowFormat: SimpleDateFormat? = null
    private var temLineDrawable: Drawable? = null

    private var onListItemDataSetListener: ((holder: MViewHolder, callLog: BluetoothCallLog,
                                             position: Int) -> Unit)? = null
    private var onTimeZoneChangedListener: (() -> Unit)? = null
    private var onTimeSetChangedListener: ((is24h: Boolean) -> Unit)? = null

    private val timeFormatChangedReceiverLazy = lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIMEZONE_CHANGED -> onTimeZoneChangedListener?.invoke()
                    Intent.ACTION_TIME_CHANGED -> {
                        val use24Hour = intent.getIntExtra(EXTRA_TIME_PREF_24_HOUR_FORMAT,
                                EXTRA_TIME_PREF_VALUE_USE_24_HOUR)
                        onTimeSetChangedListener?.invoke(use24Hour == EXTRA_TIME_PREF_VALUE_USE_24_HOUR)
                    }
                }
            }
        }
    }
    private val timeFormatChangedReceiver: BroadcastReceiver by timeFormatChangedReceiverLazy

    private val listAdapter: SimpleAdapter<BluetoothCallLog> by lazy {
        object : SimpleAdapter<BluetoothCallLog>(context, itemLayoutId, list) {
            override fun setItemData(holder: MViewHolder, callLog: BluetoothCallLog, position: Int) {
                val tvName = holder.getView<TextView>(itemNameShowViewId)
                val tvTime = holder.getView<TextView>(itemTimeShowViewId)

                tvName.text = callLog.name
                timeShowFormat?.run {
                    tvTime.text = format(callLog.time)
                }

                if (itemLineId != -1) {
                    holder.setBackground(itemLineId, temLineDrawable)
                }
                val incomingDrawable = this@CallLogHelper.incomingDrawable
                val outgoingDrawable = this@CallLogHelper.outgoingDrawable
                val missedDrawable = this@CallLogHelper.missedDrawable
                if (missedDrawable != null && incomingDrawable != null && outgoingDrawable != null) {
                    when (callLog.type) {
                        CallLog.Calls.INCOMING_TYPE -> {
                            tvTime.setCompoundDrawables(incomingDrawable, null, null, null)
                        }
                        CallLog.Calls.OUTGOING_TYPE -> {
                            tvTime.setCompoundDrawables(outgoingDrawable, null, null, null)
                        }
                        CallLog.Calls.MISSED_TYPE -> {
                            tvTime.setCompoundDrawables(missedDrawable, null, null, null)
                        }
                    }
                }
                onListItemDataSetListener?.invoke(holder, callLog, position)
            }
        }
    }

    private val onBluetoothCallLogPullStartListener: ((BluetoothDevice) -> Unit) = {
        list.clear()
        listAdapter.notifyDataSetChanged()
        refreshLayout?.run {
            if (!isRefreshing) {
                showRefreshWithoutInvoke()
            }
        }
    }

    private val onBluetoothCallLogPullFinishListener:
            ((BluetoothDevice, List<BluetoothCallLog>) -> Unit) = { _, list ->
        showList(list)
    }

    fun init() {
        PbapClientController.addOnCallLogPullStartListener(onBluetoothCallLogPullStartListener)
        PbapClientController.addOnCallLogPullFinishListener(onBluetoothCallLogPullFinishListener)
        listRecyclerView?.run {
            layoutManager = LinearLayoutManager(context)
            adapter = listAdapter

            listAdapter.setOnItemClickListener { _, _, position ->
                InCallPresenter.dial(list[position].number)
            }
        }
        val device = CoreController.getHfpClientConnectedDevice()
        if (device != null) {
            if (PbapClientController.isCallLogPulling(device)) {
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
                showList(PbapClientController.getCallLog(device))
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED)
        context.registerReceiver(timeFormatChangedReceiver, intentFilter)
    }

    fun release() {
        onListItemDataSetListener = null
        onTimeZoneChangedListener = null
        onTimeSetChangedListener = null
        if (timeFormatChangedReceiverLazy.isInitialized()) {
            context.unregisterReceiver(timeFormatChangedReceiver)
        }
        PbapClientController.removeOnCallLogPullStartListener(onBluetoothCallLogPullStartListener)
        PbapClientController.removeOnCallLogPullFinishListener(onBluetoothCallLogPullFinishListener)
    }

    fun setCallLogListView(listRecyclerView: RecyclerView, itemLayoutId: Int,
                           listener: ((holder: MViewHolder, callLog: BluetoothCallLog,
                                       position: Int) -> Unit)? = null): CallLogHelper {

        this.listRecyclerView = listRecyclerView
        this.itemLayoutId = itemLayoutId
        this.onListItemDataSetListener = listener
        return this
    }

    fun setDevicesListItem(itemNameShowViewId: Int, itemTimeShowViewId: Int,
                           lineId: Int = -1, lineDrawable: Drawable? = null): CallLogHelper {
        this.itemNameShowViewId = itemNameShowViewId
        this.itemTimeShowViewId = itemTimeShowViewId
        this.itemLineId = lineId
        this.temLineDrawable = lineDrawable
        return this
    }

    fun setCallLogIcon(incomingDrawable: Drawable, outgoingDrawable: Drawable,
                       missedDrawable: Drawable): CallLogHelper {
        incomingDrawable.setBounds(0, 0, incomingDrawable.minimumWidth, incomingDrawable.minimumHeight)
        outgoingDrawable.setBounds(0, 0, outgoingDrawable.minimumWidth, outgoingDrawable.minimumHeight)
        missedDrawable.setBounds(0, 0, missedDrawable.minimumWidth, missedDrawable.minimumHeight)
        this.incomingDrawable = incomingDrawable
        this.outgoingDrawable = outgoingDrawable
        this.missedDrawable = missedDrawable
        return this
    }

    fun setTimeShowFormat(timeShowFormat: SimpleDateFormat): CallLogHelper {
        this.timeShowFormat = timeShowFormat
        listRecyclerView?.run {
            adapter?.notifyDataSetChanged()
        }
        return this
    }

    /**
     * 下拉刷新式的刷新
     */
    fun setRefreshView(refreshLayout: HFRefreshLayout): CallLogHelper {
        this.refreshLayout = refreshLayout
        refreshLayout.run {
            setOnRefreshListener {
                val device = CoreController.getHfpClientConnectedDevice()
                if (device != null) {
                    PbapClientController.pullCallLog(device)
                }
            }
        }
        return this
    }

    fun setOnTimeZoneChangedListener(listener: (() -> Unit)?): CallLogHelper {
        onTimeZoneChangedListener = listener
        return this
    }

    fun setOnTimeSetChangedListener(listener: ((is24h: Boolean) -> Unit)?): CallLogHelper {
        onTimeSetChangedListener = listener
        return this
    }

    fun refreshCallLogList() {
        listRecyclerView?.run {
            adapter?.notifyDataSetChanged()
        }
    }

    private fun showList(list: List<BluetoothCallLog>) {
        this.list.clear()
        if (list.isNotEmpty()) {
            refreshLayout?.hideEmptyView()
            this.list.addAll(list)
        } else {
            refreshLayout?.showEmptyView()
        }
        refreshLayout?.run {
            if (isRefreshing) onRefreshFinished(1000)
        }
        listAdapter.notifyDataSetChanged()
    }
}
