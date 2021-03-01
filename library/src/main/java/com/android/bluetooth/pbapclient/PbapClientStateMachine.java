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

/*
 * Bluetooth Pbap PCE StateMachine
 *                      (Disconnected)
 *                           |    ^
 *                   CONNECT |    | DISCONNECTED
 *                           V    |
 *                 (Connecting) (Disconnecting)
 *                           |    ^
 *                 CONNECTED |    | DISCONNECT
 *                           V    |
 *                        (Connected)
 *
 * Valid Transitions:
 * State + Event -> Transition:
 *
 * Disconnected + CONNECT -> Connecting
 * Connecting + CONNECTED -> Connected
 * Connecting + TIMEOUT -> Disconnecting
 * Connecting + DISCONNECT -> Disconnecting
 * Connected + DISCONNECT -> Disconnecting
 * Disconnecting + DISCONNECTED -> (Safe) Disconnected
 * Disconnecting + TIMEOUT -> (Force) Disconnected
 * Disconnecting + CONNECT : Defer Message
 *
 */
package com.android.bluetooth.pbapclient;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Process;
import android.os.UserManager;

import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.github.slavebluetooth.controller.pbapclient.OnCallLogPullStateChangeListener;
import com.github.slavebluetooth.controller.pbapclient.OnPhonebookPullStateChangeListener;
import com.github.slavebluetooth.controller.pbapclient.PbapClientController;
import com.github.slavebluetooth.model.BluetoothCallLog;
import com.github.slavebluetooth.model.BluetoothPhonebook;

import java.util.ArrayList;
import java.util.List;

public final class PbapClientStateMachine extends StateMachine {

    // Messages for handling connect/disconnect requests.
    private static final int MSG_DISCONNECT = 2;
    private static final int MSG_SDP_COMPLETE = 9;

    // Messages for handling error conditions.
    private static final int MSG_CONNECT_TIMEOUT = 3;
    private static final int MSG_DISCONNECT_TIMEOUT = 4;

    // Messages for feedback from ConnectionHandler.
    static final int MSG_CONNECTION_COMPLETE = 5;
    static final int MSG_CONNECTION_FAILED = 6;
    static final int MSG_CONNECTION_CLOSED = 7;
    static final int MSG_RESUME_DOWNLOAD = 8;

    static final int CONNECT_TIMEOUT = 10000;
    static final int DISCONNECT_TIMEOUT = 3000;

    private final Object mLock;
    private State mDisconnected;
    private State mConnecting;
    private State mConnected;
    private State mDisconnecting;

    // mCurrentDevice may only be changed in Disconnected State.
    private final BluetoothDevice mCurrentDevice;
    private Context mContext;
    private PbapClientConnectionHandler mConnectionHandler;
    private HandlerThread mHandlerThread = null;
    private UserManager mUserManager;

    // mMostRecentState maintains previous state for broadcasting transitions.
    private int mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;

    private OnPhonebookPullStateChangeListener onPhonebookPullStateChangeListener;
    private OnCallLogPullStateChangeListener onCallLogPullStateChangeListener;
    private boolean autoPull = false;
    private boolean pullAll = false;
    private boolean pullPhonebook = false;
    private boolean pullCallLog = false;
    private boolean start = false;

    public PbapClientStateMachine(Context context, BluetoothDevice device,
                                  OnPhonebookPullStateChangeListener onPhonebookPullStateChangeListener,
                                  OnCallLogPullStateChangeListener onCallLogPullStateChangeListener) {
        super("SelfPbapClientStateMachine");

        mContext = context;
        mCurrentDevice = device;
        this.onPhonebookPullStateChangeListener = onPhonebookPullStateChangeListener;
        this.onCallLogPullStateChangeListener = onCallLogPullStateChangeListener;
        mLock = new Object();
        mUserManager = UserManager.get(mContext);
        mDisconnected = new Disconnected();
        mConnecting = new Connecting();
        mDisconnecting = new Disconnecting();
        mConnected = new Connected();

        addState(mDisconnected);
        addState(mConnecting);
        addState(mDisconnecting);
        addState(mConnected);

        setInitialState(mConnecting);
    }

    public void setAutoPull(boolean autoPull) {
        this.autoPull = autoPull;
    }

    public boolean isPhonebookPulling() {
        if (mConnectionHandler != null) {
            return mConnectionHandler.isPhonebookPulling();
        } else {
            return false;
        }
    }

    public boolean isCallLogPulling() {
        if (mConnectionHandler != null) {
            return mConnectionHandler.isCallLogPulling();
        } else {
            return false;
        }
    }

    public void pullAll() {
        if (start) {
            if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                if (!isPhonebookPulling() && !isCallLogPulling()) {
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD)
                            .sendToTarget();
                }
            } else if (getConnectionState() == BluetoothProfile.STATE_CONNECTING) {
                pullAll = true;
                pullPhonebook = false;
                pullCallLog = false;
            } else {
                setInitialState(mConnecting);
            }
        } else {
            pullAll = true;
            pullPhonebook = false;
            pullCallLog = false;
            start();
            start = true;
        }

    }

    public void pullPhonebook() {
        if (start) {
            if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                if (!isPhonebookPulling()) {
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD_PHONEBOOK)
                            .sendToTarget();
                }
            } else if (getConnectionState() == BluetoothProfile.STATE_CONNECTING) {
                if (!pullAll) {
                    if (pullCallLog) {
                        pullAll = true;
                    } else {
                        pullPhonebook = true;
                    }
                }
            } else {
                setInitialState(mConnecting);
            }
        } else {
            if (!pullAll) {
                if (pullCallLog) {
                    pullAll = true;
                } else {
                    pullPhonebook = true;
                }
            }
            start();
            start = true;
        }
    }

    public void pullCallLog() {
        if (start) {
            if (getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                if (!isCallLogPulling()) {
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD_CALL_LOG)
                            .sendToTarget();
                }
            } else if (getConnectionState() == BluetoothProfile.STATE_CONNECTING) {
                if (!pullAll) {
                    if (pullPhonebook) {
                        pullAll = true;
                    } else {
                        pullCallLog = true;
                    }
                }
            } else {
                setInitialState(mConnecting);
            }
        } else {
            if (!pullAll) {
                if (pullPhonebook) {
                    pullAll = true;
                } else {
                    pullCallLog = true;
                }
            }
            start();
            start = true;
        }

    }

    public ArrayList<BluetoothPhonebook> getPhoneBookList() {
        if (mConnectionHandler != null) {
            return mConnectionHandler.getPhoneBookList();
        } else {
            return new ArrayList<>();
        }
    }

    public ArrayList<BluetoothCallLog> getCallLogList() {
        if (mConnectionHandler != null) {
            return mConnectionHandler.getCallLogList();
        } else {
            return new ArrayList<>();
        }
    }

    public void setStart(boolean hasStart) {
        start = hasStart;
    }

    public boolean isStart() {
        return start;
    }

    class Disconnected extends State {
        @Override
        public void enter() {
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_DISCONNECTED);
            mMostRecentState = BluetoothProfile.STATE_DISCONNECTED;
            quit();
        }
    }

    class Connecting extends State {
        private SDPBroadcastReceiver mSdpReceiver;

        @Override
        public void enter() {
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_CONNECTING);
            mSdpReceiver = new SDPBroadcastReceiver();
            mSdpReceiver.register();
            mCurrentDevice.sdpSearch(BluetoothUuid.PBAP_PSE);
            mMostRecentState = BluetoothProfile.STATE_CONNECTING;

            // Create a separate handler instance and thread for performing
            // connect/download/disconnect operations as they may be time consuming and error prone.
            mHandlerThread =
                    new HandlerThread("PBAP PCE handler", Process.THREAD_PRIORITY_BACKGROUND);
            mHandlerThread.start();
            mConnectionHandler =
                    new PbapClientConnectionHandler.Builder().setLooper(mHandlerThread.getLooper())
                            .setContext(mContext)
                            .setClientSM(PbapClientStateMachine.this)
                            .setRemoteDevice(mCurrentDevice)
                            .setOnPhonebookPullStateChangeListener(onPhonebookPullStateChangeListener)
                            .setOnCallLogPullStateChangeListener(onCallLogPullStateChangeListener)
                            .build();

            sendMessageDelayed(MSG_CONNECT_TIMEOUT, CONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case MSG_DISCONNECT:
                    if (message.obj instanceof BluetoothDevice && message.obj.equals(
                            mCurrentDevice)) {
                        removeMessages(MSG_CONNECT_TIMEOUT);
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_CONNECTION_COMPLETE:
                    removeMessages(MSG_CONNECT_TIMEOUT);
                    transitionTo(mConnected);
                    break;

                case MSG_CONNECTION_FAILED:
                case MSG_CONNECT_TIMEOUT:
                    removeMessages(MSG_CONNECT_TIMEOUT);
                    transitionTo(mDisconnecting);
                    break;

                case MSG_SDP_COMPLETE:
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_CONNECT,
                            message.obj).sendToTarget();
                    break;

                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }

        @Override
        public void exit() {
            mSdpReceiver.unregister();
            mSdpReceiver = null;
        }

        private class SDPBroadcastReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(BluetoothDevice.ACTION_SDP_RECORD)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (!device.equals(getDevice())) {
                        return;
                    }
                    ParcelUuid uuid = intent.getParcelableExtra(BluetoothDevice.EXTRA_UUID);
                    if (uuid.equals(BluetoothUuid.PBAP_PSE)) {
                        sendMessage(MSG_SDP_COMPLETE,
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_SDP_RECORD));
                    }
                }
            }

            public void register() {
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_SDP_RECORD);
                mContext.registerReceiver(this, filter);
            }

            public void unregister() {
                mContext.unregisterReceiver(this);
            }
        }
    }

    class Disconnecting extends State {
        @Override
        public void enter() {
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_DISCONNECTING);
            mMostRecentState = BluetoothProfile.STATE_DISCONNECTING;
            mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DISCONNECT)
                    .sendToTarget();
            sendMessageDelayed(MSG_DISCONNECT_TIMEOUT, DISCONNECT_TIMEOUT);
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case MSG_CONNECTION_CLOSED:
                    removeMessages(MSG_DISCONNECT_TIMEOUT);
                    mHandlerThread.quitSafely();
                    transitionTo(mDisconnected);
                    break;

                case MSG_DISCONNECT:
                    deferMessage(message);
                    break;

                case MSG_DISCONNECT_TIMEOUT:
                    mConnectionHandler.abort();
                    break;

                case MSG_RESUME_DOWNLOAD:
                    // Do nothing.
                    break;

                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    class Connected extends State {
        @Override
        public void enter() {
            onConnectionStateChanged(mCurrentDevice, mMostRecentState,
                    BluetoothProfile.STATE_CONNECTED);
            mMostRecentState = BluetoothProfile.STATE_CONNECTED;
            if (mUserManager.isUserUnlocked()) {
                if (autoPull || pullAll) {
                    pullAll = false;
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD)
                            .sendToTarget();
                } else if (pullPhonebook) {
                    pullPhonebook = false;
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD_PHONEBOOK)
                            .sendToTarget();
                } else if (pullCallLog) {
                    pullCallLog = false;
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD_CALL_LOG)
                            .sendToTarget();
                }

            }
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case MSG_DISCONNECT:
                    if ((message.obj instanceof BluetoothDevice) && message.obj.equals(mCurrentDevice)) {
                        transitionTo(mDisconnecting);
                    }
                    break;

                case MSG_RESUME_DOWNLOAD:
                    mConnectionHandler.obtainMessage(PbapClientConnectionHandler.MSG_DOWNLOAD)
                            .sendToTarget();
                    break;

                default:
                    return NOT_HANDLED;
            }
            return HANDLED;
        }
    }

    private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
        if (device == null) {
            return;
        }
        Intent intent = new Intent(BluetoothPbapClient.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent);
    }

    public void disconnect(BluetoothDevice device) {
        sendMessage(MSG_DISCONNECT, device);
    }

    public void resumeDownload() {
        sendMessage(MSG_RESUME_DOWNLOAD);
    }

    public void doQuit() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
        }
        quitNow();
    }

    @Override
    protected void onQuitting() {
        PbapClientController.INSTANCE.cleanupDevice(mCurrentDevice);
    }

    public int getConnectionState() {
        IState currentState = getCurrentState();
        if (currentState instanceof Disconnected) {
            return BluetoothProfile.STATE_DISCONNECTED;
        } else if (currentState instanceof Connecting) {
            return BluetoothProfile.STATE_CONNECTING;
        } else if (currentState instanceof Connected) {
            return BluetoothProfile.STATE_CONNECTED;
        } else if (currentState instanceof Disconnecting) {
            return BluetoothProfile.STATE_DISCONNECTING;
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        int clientState;
        BluetoothDevice currentDevice;
        synchronized (mLock) {
            clientState = getConnectionState();
            currentDevice = getDevice();
        }
        List<BluetoothDevice> deviceList = new ArrayList<>();
        for (int state : states) {
            if (clientState == state) {
                if (currentDevice != null) {
                    deviceList.add(currentDevice);
                }
            }
        }
        return deviceList;
    }

    public int getConnectionState(BluetoothDevice device) {
        if (device == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        synchronized (mLock) {
            if (device.equals(mCurrentDevice)) {
                return getConnectionState();
            }
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public BluetoothDevice getDevice() {
        /*
         * Disconnected is the only state where device can change, and to prevent the race
         * condition of reporting a valid device while disconnected fix the report here.  Note that
         * Synchronization of the state and device is not possible with current state machine
         * desingn since the actual Transition happens sometime after the transitionTo method.
         */
        if (getCurrentState() instanceof Disconnected) {
            return null;
        }
        return mCurrentDevice;
    }

    Context getContext() {
        return mContext;
    }
}
