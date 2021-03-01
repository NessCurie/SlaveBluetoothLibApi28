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

package com.android.settingslib.bluetooth;

import android.annotation.NonNull;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothHeadsetClientCall;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.settingslib.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the Handsfree HF role.
 */
public final class HfpClientProfile implements LocalBluetoothProfile {
    public interface OnHfpClientProfileReadyListener {
        void onHfpClientProfileReady();
    }

    private OnHfpClientProfileReadyListener onHfpClientProfileReadyListener;
    private static final String TAG = "HfpClientProfile";
    private static boolean V = false;

    private BluetoothHeadsetClient mService;
    private boolean mIsProfileReady;

    private final LocalBluetoothAdapter mLocalAdapter;
    private final CachedBluetoothDeviceManager mDeviceManager;

    static final ParcelUuid[] SRC_UUIDS = {
            BluetoothUuid.HSP_AG,
            BluetoothUuid.Handsfree_AG,
    };

    static final String NAME = "HEADSET_CLIENT";
    private final LocalBluetoothProfileManager mProfileManager;

    // Order of this profile in device profiles list
    private static final int ORDINAL = 0;

    // These callbacks run on the main thread.
    private final class HfpClientServiceListener
            implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (V) Log.d(TAG, "Bluetooth service connected");
            mService = (BluetoothHeadsetClient) proxy;
            // We just bound to the service, so refresh the UI for any connected HFP devices.
            List<BluetoothDevice> deviceList = mService.getConnectedDevices();
            while (!deviceList.isEmpty()) {
                BluetoothDevice nextDevice = deviceList.remove(0);
                CachedBluetoothDevice device = mDeviceManager.findDevice(nextDevice);
                // we may add a new device here, but generally this should not happen
                if (device == null) {
                    Log.w(TAG, "HfpClient profile found new device: " + nextDevice);
                    device = mDeviceManager.addDevice(mLocalAdapter, mProfileManager, nextDevice);
                }
                device.onProfileStateChanged(
                        HfpClientProfile.this, BluetoothProfile.STATE_CONNECTED);
                device.refresh();
            }
            mIsProfileReady = true;
            if (onHfpClientProfileReadyListener != null) {
                onHfpClientProfileReadyListener.onHfpClientProfileReady();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (V) Log.d(TAG, "Bluetooth service disconnected");
            mIsProfileReady = false;
        }
    }

    @Override
    public boolean isProfileReady() {
        return mIsProfileReady;
    }

    @Override
    public int getProfileId() {
        return BluetoothProfile.HEADSET_CLIENT;
    }

    HfpClientProfile(Context context, LocalBluetoothAdapter adapter,
                     CachedBluetoothDeviceManager deviceManager,
                     LocalBluetoothProfileManager profileManager) {
        mLocalAdapter = adapter;
        mDeviceManager = deviceManager;
        mProfileManager = profileManager;
        mLocalAdapter.getProfileProxy(context, new HfpClientServiceListener(),
                BluetoothProfile.HEADSET_CLIENT);
    }

    @Override
    public boolean isConnectable() {
        return true;
    }

    @Override
    public boolean isAutoConnectable() {
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        if (mService == null) return new ArrayList<>(0);
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

    @Override
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

    @Override
    public boolean disconnect(BluetoothDevice device) {
        if (mService == null) return false;
        // Downgrade priority as user is disconnecting the headset.
        if (mService.getPriority(device) > BluetoothProfile.PRIORITY_ON) {
            mService.setPriority(device, BluetoothProfile.PRIORITY_ON);
        }
        return mService.disconnect(device);
    }

    @Override
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

    @Override
    public boolean isPreferred(BluetoothDevice device) {
        if (mService == null) return false;
        return mService.getPriority(device) > BluetoothProfile.PRIORITY_OFF;
    }

    @Override
    public int getPreferred(BluetoothDevice device) {
        if (mService == null) return BluetoothProfile.PRIORITY_OFF;
        return mService.getPriority(device);
    }

    @Override
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

    @Override
    public String toString() {
        return NAME;
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }

    public BluetoothHeadsetClient getService() {
        return mService;
    }

    @Override
    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_headset;
    }

    @Override
    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return R.string.bluetooth_headset_profile_summary_use_for;

            case BluetoothProfile.STATE_CONNECTED:
                return R.string.bluetooth_headset_profile_summary_connected;

            default:
                return Utils.getConnectionStateSummary(state);
        }
    }

    public void setOnHfpClientProfileReadyListener(OnHfpClientProfileReadyListener listener) {
        this.onHfpClientProfileReadyListener = listener;
    }

    public BluetoothHeadsetClientCall dial(@NonNull String number) {
        if (mService != null) {
            List<BluetoothDevice> connectedDevices = getConnectedDevices();
            if (connectedDevices != null && connectedDevices.size() > 0) {
                return mService.dial(connectedDevices.get(0), number);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /*public boolean reDial() {
        if (mService != null) {
            List<BluetoothDevice> connectedDevices = getConnectedDevices();
            if (connectedDevices != null && connectedDevices.size() > 0) {
                return mService.redial(connectedDevices.get(0));
            } else {
                return false;
            }
        } else {
            return false;
        }
    }*/

    public boolean acceptCall() {
        if (mService != null) {
            BluetoothDevice connectedDevice = getConnectedDevice();
            if (connectedDevice != null) {
                return mService.acceptCall(connectedDevice, BluetoothHeadsetClient.CALL_ACCEPT_NONE);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public BluetoothHeadsetClientCall terminateCall() {
        if (mService != null) {
            BluetoothDevice device = getConnectedDevice();
            if (device != null) {
                List<BluetoothHeadsetClientCall> currentCalls = mService.getCurrentCalls(device);
                if (currentCalls != null && currentCalls.size() > 0) {
                    BluetoothHeadsetClientCall call = null;
                    if (currentCalls.size() > 1) {
                        for (BluetoothHeadsetClientCall currentCall : currentCalls) {
                            if (currentCall.getState() != BluetoothHeadsetClientCall.CALL_STATE_WAITING
                                    && currentCall.getState() != BluetoothHeadsetClientCall.CALL_STATE_HELD) {
                                call = currentCall;
                                break;
                            }
                        }
                    } else {
                        call = currentCalls.get(0);
                    }
                    if (call != null) {
                        if (call.getState() == BluetoothHeadsetClientCall.CALL_STATE_INCOMING) {
                            mService.rejectCall(device);
                        } else {
                            mService.terminateCall(device, call);
                        }
                        return call;
                    }
                }
            }
        }
        return null;
    }

    public boolean setAudioRouteReverse() {
        if (mService != null) {
            List<BluetoothDevice> connectedDevices = getConnectedDevices();
            if (connectedDevices != null && connectedDevices.size() > 0) {
                BluetoothDevice bluetoothDevice = connectedDevices.get(0);
                if (mService.getAudioState(bluetoothDevice) == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                    return mService.disconnectAudio(getConnectedDevice());
                } else {
                    return mService.connectAudio(getConnectedDevice());
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean setAudio(boolean connect) {
        if (mService != null) {
            List<BluetoothDevice> connectedDevices = getConnectedDevices();
            if (connectedDevices != null && connectedDevices.size() > 0) {
                BluetoothDevice bluetoothDevice = connectedDevices.get(0);
                if (connect) {
                    if (mService.getAudioState(bluetoothDevice) != BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                        return mService.connectAudio(getConnectedDevice());
                    } else {
                        return false;
                    }
                } else {
                    if (mService.getAudioState(bluetoothDevice) == BluetoothHeadsetClient.STATE_AUDIO_CONNECTED) {
                        return mService.disconnectAudio(getConnectedDevice());
                    } else {
                        return false;
                    }
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public boolean sendDTMF(byte code) {
        if (mService != null) {
            List<BluetoothDevice> connectedDevices = getConnectedDevices();
            if (connectedDevices != null && connectedDevices.size() > 0) {
                return mService.sendDTMF(connectedDevices.get(0), code);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int getDrawableResource(BluetoothClass btClass) {
        return R.drawable.ic_bt_headset_hfp;
    }

    public void finalize() {
        if (V) Log.d(TAG, "finalize()");
        if (mService != null) {
            try {
                BluetoothAdapter.getDefaultAdapter().closeProfileProxy(
                        BluetoothProfile.HEADSET_CLIENT, mService);
                mService = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up HfpClient proxy", t);
            }
        }
    }
}
