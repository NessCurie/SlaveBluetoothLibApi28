package com.github.slavebluetoothsample.ui.controller

import android.content.Context
import android.view.View
import com.github.slavebluetooth.controller.pbapclient.CallLogHelper
import com.github.slavebluetooth.utils.resettableLazy
import com.github.slavebluetooth.utils.resettableManager
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.layout_list_empty.view.*
import kotlinx.android.synthetic.main.layout_record.view.*
import java.text.SimpleDateFormat
import java.util.*

class PhoneRecordController(private val context: Context) {

    val content: View by lazy { View.inflate(context, R.layout.layout_record, null) }
    private val callLogHelper = CallLogHelper(context)

    private val resettableLazyManager = resettableManager()
    private val showSdf: SimpleDateFormat by resettableLazy(resettableLazyManager) {
        if (android.text.format.DateFormat.is24HourFormat(context)) {
            SimpleDateFormat(context.getString(R.string.sdf_record_list_24h), Locale.getDefault())
        } else {
            SimpleDateFormat(context.getString(R.string.sdf_record_list_12h), Locale.getDefault())
        }
    }

    init {
        val listEmpty = View.inflate(context, R.layout.layout_list_empty, null)
        listEmpty.tvEmptyHint.text = context.getString(R.string.no_data)
        content.hfRlRecord.run {
            setPullLoadEnable(false)
            emptyView = listEmpty
            setRefreshOnStateNormalHint(context.getString(R.string.pull_to_sync))
            setRefreshOnStateReadyHint(context.getString(R.string.ready_to_sync))
            setRefreshOnStateRefreshingHint(context.getString(R.string.syncing))
            setRefreshOnStateSuccessHint(context.getString(R.string.sync_success))
        }
        callLogHelper.setCallLogListView(content.rvRecord, R.layout.item_reocrd)
                .setDevicesListItem(R.id.tvName, R.id.tvTime)
                .setRefreshView( content.hfRlRecord)
                .setTimeShowFormat(showSdf)
                .setCallLogIcon(context.getDrawable(R.drawable.ic_call_in)!!,
                        context.getDrawable(R.drawable.ic_call_out)!!,
                        context.getDrawable(R.drawable.ic_missed_call)!!)
                .setOnTimeZoneChangedListener {
                    showSdf.timeZone = TimeZone.getDefault()
                    callLogHelper.refreshCallLogList()
                }
                .setOnTimeSetChangedListener {
                    resettableLazyManager.reset()
                    callLogHelper.setTimeShowFormat(showSdf)
                }
                .init()
    }

    fun release() {
        callLogHelper.release()
    }
}