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
import android.provider.CallLog.Calls;
import android.util.Log;
import android.util.Pair;

import com.android.vcard.VCardEntry;
import com.android.vcard.VCardUtils;
import com.github.slavebluetooth.model.BluetoothCallLog;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class CallLogPullRequest extends PullRequest {
    private static final String TAG = "PbapCallLogPullRequest";
    private static final String TIMESTAMP_PROPERTY = "X-IRMC-CALL-DATETIME";
    private static final String TIMESTAMP_FORMAT = "yyyyMMdd'T'HHmmss";

    private final Account mAccount;
    private Context mContext;
    private HashMap<String, Integer> mCallCounter;
    private ArrayList<BluetoothCallLog> recordList;

    public CallLogPullRequest(Context context, String path, HashMap<String, Integer> map,
                              Account account, ArrayList<BluetoothCallLog> recordList) {
        mContext = context;
        this.path = path;
        mCallCounter = map;
        mAccount = account;
        this.recordList = recordList;
    }

    @Override
    public void onPullComplete() {
        if (mEntries == null) {
            return;
        }

        int type;
        switch (path) {
            case PbapClientConnectionHandler.ICH_PATH:
                type = Calls.INCOMING_TYPE;
                break;
            case PbapClientConnectionHandler.OCH_PATH:
                type = Calls.OUTGOING_TYPE;
                break;
            case PbapClientConnectionHandler.MCH_PATH:
                type = Calls.MISSED_TYPE;
                break;
            default:
                return;
        }

        SimpleDateFormat parser = new SimpleDateFormat(TIMESTAMP_FORMAT, Locale.getDefault());
        for (VCardEntry vcard : mEntries) {
            String number;
            String name;
            long time = -1;

            List<VCardEntry.PhoneData> phones = vcard.getPhoneList();
            if (phones == null || phones.get(0).getNumber().equals(";")) {
                number = "";
            } else {
                number = VCardUtils.PhoneNumberUtilsPort.formatNumber(phones.get(0).getNumber(), 0);
            }
            List<Pair<String, String>> irmc = vcard.getUnknownXData();
            if (irmc != null) {
                for (Pair<String, String> pair : irmc) {
                    if (pair.first.startsWith(TIMESTAMP_PROPERTY)) {
                        try {
                            time = parser.parse(pair.second).getTime();
                        } catch (ParseException e) {
                            Log.d(TAG, "Failed to parse date ");
                        }
                    }
                }
            }


            if (vcard.getNameData().emptyStructuredName()) {
                name = number;
            } else {
                name = vcard.getDisplayName();
            }
            recordList.add(new BluetoothCallLog(type, name, number, time));
        }

        synchronized (this) {
            this.notify();
        }


    }
}
