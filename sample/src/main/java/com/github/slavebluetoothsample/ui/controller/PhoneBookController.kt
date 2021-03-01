package com.github.slavebluetoothsample.ui.controller

import android.content.Context
import android.view.View
import com.github.slavebluetooth.controller.pbapclient.PhonebookHelper
import com.github.slavebluetoothsample.R
import kotlinx.android.synthetic.main.layout_list_empty.view.*
import kotlinx.android.synthetic.main.layout_phone_book.view.*

class PhoneBookController(private val context: Context) {

    val content: View by lazy { View.inflate(context, R.layout.layout_phone_book, null) }
    private val phonebookHelper = PhonebookHelper(context)

    init {
        val listEmpty = View.inflate(context, R.layout.layout_list_empty, null)
        listEmpty.tvEmptyHint.text = context.getString(R.string.no_data)
        content.hfRlPhoneBook.run {
            setPullLoadEnable(false)
            emptyView = listEmpty
            setRefreshOnStateNormalHint(context.getString(R.string.pull_to_sync))
            setRefreshOnStateReadyHint(context.getString(R.string.ready_to_sync))
            setRefreshOnStateRefreshingHint(context.getString(R.string.syncing))
            setRefreshOnStateSuccessHint(context.getString(R.string.sync_success))
        }

        phonebookHelper.setPhonebookListView(content.rvPhoneBook, R.layout.item_phone_book)
                .setDevicesListItem(R.id.tvName, R.id.tvNumber)
                .setRefreshView(content.hfRlPhoneBook)
                .setQuickSideBarView(content.quickSideBarView,
                        content.flSideBarTips, content.tvTips, 195)
                .init()
    }

    fun release() {
        phonebookHelper.release()
    }
}