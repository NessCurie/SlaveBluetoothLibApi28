/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.bluetooth.pbapclient;

import android.accounts.Account;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardUtils;
import com.github.slavebluetooth.model.BluetoothPhonebook;
import com.github.slavebluetooth.utils.HanziToPinyin;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class PhonebookPullRequest extends PullRequest {
    private static final int MAX_OPS = 250;
    private static final boolean VDBG = false;
    private static final String TAG = "PbapPhonebookPullRequest";

    private final Account mAccount;
    private final Context mContext;
    private ArrayList<BluetoothPhonebook> phoneBookList = new ArrayList<>();
    public boolean complete = false;

    public PhonebookPullRequest(Context context, Account account) {
        mContext = context;
        mAccount = account;
        path = PbapClientConnectionHandler.PB_PATH;
    }

    public ArrayList<BluetoothPhonebook> getPhoneBookList() {
        return phoneBookList;
    }

    @Override
    public void onPullComplete() {
        if (mEntries == null) {
            Log.e(TAG, "onPullComplete entries is null.");
            return;
        }
        if (VDBG) {
            Log.d(TAG, "onPullComplete with " + mEntries.size() + " count.");
        }
        for (VCardEntry entry : mEntries) {
            if (entry.getPhoneList() != null && entry.getPhoneList().size() != 0
                    && !TextUtils.isEmpty(entry.getDisplayName())) {
                for (VCardEntry.PhoneData phoneData : entry.getPhoneList()) {
                    if (!TextUtils.isEmpty(phoneData.getNumber())) {
                        String number = VCardUtils.PhoneNumberUtilsPort.formatNumber(phoneData.getNumber(), 0);
                        BluetoothPhonebook phoneBook = new BluetoothPhonebook(entry.getDisplayName(), number);
                        if (!phoneBookList.contains(phoneBook)) {
                            phoneBookList.add(phoneBook);
                        }
                    }
                }
            }
        }
        HanziToPinyin hanziToPinyin = HanziToPinyin.getInstance();
        Collator collator = Collator.getInstance(Locale.ENGLISH);
        Collections.sort(phoneBookList, (vCard1, vCard2) -> {
            int result = 0;
            if (vCard1 != null && vCard2 != null) {
                String vCard1Pinyin = hanziToPinyin.transliterate(vCard1.getName());
                String vCard2Pinyin = hanziToPinyin.transliterate(vCard2.getName());
                char vCard1Point0 = Character.toUpperCase(vCard1Pinyin.charAt(0));
                char vCard2Point0 = Character.toUpperCase(vCard2Pinyin.charAt(0));
                if (vCard1Point0 > 'A' && vCard1Point0 < 'Z') {
                    if (vCard2Point0 > 'A' && vCard2Point0 < 'Z') {
                        result = collator.compare(vCard1Pinyin, vCard2Pinyin);
                    } else {
                        result = -1;
                    }
                } else {
                    if (vCard2Point0 > 'A' && vCard2Point0 < 'Z') {
                        result = 1;
                    } else {
                        result = collator.compare(vCard1Pinyin, vCard2Pinyin);
                    }
                }
            }
            return result;
        });

        complete = true;
    }
}
