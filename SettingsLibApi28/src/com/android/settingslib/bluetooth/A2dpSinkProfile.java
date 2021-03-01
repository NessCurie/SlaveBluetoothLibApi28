/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.settingslib.bluetooth;

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.settingslib.R;

import java.util.ArrayList;
import java.util.List;

public final class A2dpSinkProfile implements LocalBluetoothProfile {
    public interface OnA2dpSinkProfileReadyListener {
        void onA2dpSinkProfileReady();
    }

    private OnA2dpSinkProfileReadyListener onA2dpSinkProfileReadyListener;
    private static final String TAG = "A2dpSinkProfile";
    private static boolean V = true;

    private BluetoothA2dpSink mService;
    private boolean mIsProfileReady;

    private final LocalBluetoothAdapter mLocalAdapter;
    private final CachedBluetoothDeviceManager mDeviceManager;

    static final ParcelUuid[] SRC_UUIDS = {
            BluetoothUuid.AudioSource,
            BluetoothUuid.AdvAudioDist,
    };

    static final String NAME = "A2DPSink";
    private final LocalBluetoothProfileManager mProfileManager;

    // Order of this profile in device profiles list
    private static final int ORDINAL = 5;

    // These callbacks run on the main thread.
    private final class A2dpSinkServiceListener
            implements BluetoothProfile.ServiceListener {

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (V) Log.d(TAG, "Bluetooth service connected");
            mService = (BluetoothA2dpSink) proxy;
            // We just bound to the service, so refresh the UI for any connected A2DP devices.
            List<BluetoothDevice> deviceList = mService.getConnectedDevices();
            while (!deviceList.isEmpty()) {
                BluetoothDevice nextDevice = deviceList.remove(0);
                CachedBluetoothDevice device = mDeviceManager.findDevice(nextDevice);
                // we may add a new device here, but generally this should not happen
                if (device == null) {
                    Log.w(TAG, "A2dpSinkProfile found new device: " + nextDevice);
                    device = mDeviceManager.addDevice(mLocalAdapter, mProfileManager, nextDevice);
                }
                device.onProfileStateChanged(A2dpSinkProfile.this, BluetoothProfile.STATE_CONNECTED);
                device.refresh();
            }
            mIsProfileReady = true;
            if (onA2dpSinkProfileReadyListener != null) {
                onA2dpSinkProfileReadyListener.onA2dpSinkProfileReady();
            }
        }

        public void onServiceDisconnected(int profile) {
            if (V) Log.d(TAG, "Bluetooth service disconnected");
            mIsProfileReady = false;
        }
    }

    public boolean isProfileReady() {
        return mIsProfileReady;
    }

    @Override
    public int getProfileId() {
        return BluetoothProfile.A2DP_SINK;
    }

    A2dpSinkProfile(Context context, LocalBluetoothAdapter adapter,
                    CachedBluetoothDeviceManager deviceManager,
                    LocalBluetoothProfileManager profileManager) {
        mLocalAdapter = adapter;
        mDeviceManager = deviceManager;
        mProfileManager = profileManager;
        mLocalAdapter.getProfileProxy(context, new A2dpSinkServiceListener(),
                BluetoothProfile.A2DP_SINK);
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        if (mService == null) return new ArrayList<BluetoothDevice>(0);
        return mService.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED,
                        BluetoothProfile.STATE_CONNECTING,
                        BluetoothProfile.STATE_DISCONNECTING});
    }

    public BluetoothDevice getConnectedDevice() {
        List<BluetoothDevice> connectedDevices = getConnectedDevices();
        if (connectedDevices != null && connectedDevices.size() > 0) {
            return connectedDevices.get(0);
        } else {
            return null;
        }
    }

    public boolean connect(BluetoothDevice device) {
        if (mService == null) return false;
        List<BluetoothDevice> srcs = getConnectedDevices();
        if (srcs != null) {
            for (BluetoothDevice src : srcs) {
                if (src.equals(device)) {
                    // Connect to same device, Ignore it
                    Log.d(TAG, "Ignoring Connect");
                    return true;
                }
            }
        }
        return mService.connect(device);
    }

    public boolean disconnect(BluetoothDevice device) {
        if (mService == null) return false;
        // Downgrade priority as user is disconnecting the headset.
        if (mService.getPriority(device) > BluetoothProfile.PRIORITY_ON) {
            mService.setPriority(device, BluetoothProfile.PRIORITY_ON);
        }
        return mService.disconnect(device);
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (mService == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mService.getConnectionState(device);
    }

    public int getConnectionStatus() {
        if (mService == null) {
            return 0;
        }
        BluetoothDevice connectedDevice = getConnectedDevice();
        if (connectedDevice == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mService.getConnectionState(connectedDevice);
    }

    public boolean isConnected() {
        if (mService == null) return false;
        List<BluetoothDevice> connectedDevices = getConnectedDevices();
        for (BluetoothDevice connectedDevice : connectedDevices) {
            if (mService.getConnectionState(connectedDevice) == BluetoothProfile.STATE_CONNECTED) {
                return true;
            }
        }
        return false;
    }

    public void setOnA2dpSinkProfileReadyListener(OnA2dpSinkProfileReadyListener listener) {
        this.onA2dpSinkProfileReadyListener = listener;
    }

    public boolean isPreferred(BluetoothDevice device) {
        if (mService == null) return false;
        return mService.getPriority(device) > BluetoothProfile.PRIORITY_OFF;
    }

    public int getPreferred(BluetoothDevice device) {
        if (mService == null) return BluetoothProfile.PRIORITY_OFF;
        return mService.getPriority(device);
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
        if (mService == null) return;
        if (preferred) {
            if (mService.getPriority(device) < BluetoothProfile.PRIORITY_ON) {
                mService.setPriority(device, BluetoothProfile.PRIORITY_ON);
            }
        } else {
            mService.setPriority(device, BluetoothProfile.PRIORITY_OFF);
        }
    }

    boolean isA2dpPlaying() {
        if (mService == null) return false;
        List<BluetoothDevice> srcs = mService.getConnectedDevices();
        if (!srcs.isEmpty()) {
            if (mService.isA2dpPlaying(srcs.get(0))) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        return NAME;
    }

    public int getOrdinal() {
        return ORDINAL;
    }

    public int getNameResource(BluetoothDevice device) {
        // we need to have same string in UI for even SINK Media Audio.
        return R.string.bluetooth_profile_a2dp;
    }

    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return R.string.bluetooth_a2dp_profile_summary_use_for;

            case BluetoothProfile.STATE_CONNECTED:
                return R.string.bluetooth_a2dp_profile_summary_connected;

            default:
                return Utils.getConnectionStateSummary(state);
        }
    }

    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_headphones_a2dp;
    }

    public void finalize() {
        if (V) Log.d(TAG, "finalize()");
        if (mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(BluetoothProfile.A2DP_SINK,
                        mService);
                mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up A2DP proxy", t);
            }
        }
    }
}
