package com.alonss0.godotplugin;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class BTGodotPlugin extends GodotPlugin {
    public final String TAG = BTGodotPlugin.class.getName();

    /**
     * Base constructor passing a {@link Godot} instance through which the plugin can access Godot's
     * APIs and lifecycle events.
     *
     * @param godot
     */
    public BTGodotPlugin(Godot godot) {
        super(godot);
    }

    private final BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
    private final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
    private final BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    private BluetoothDevice bluetoothDevice = null; // Store found device as its HARDCODED
    private boolean scanning;
    private final Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    @Override
    public String getPluginName() {
        return BuildConfig.GODOT_PLUGIN_NAME;
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "> Scan failed with error code " + errorCode);
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            try {
                if (getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                // TODO: [ClickUp: https://sharing.clickup.com/9015884462/t/h/86c27gejv/R0TWUAS2K5GE6UI]
                String deviceFoundName = result.getDevice().getName();
                if (Objects.equals(deviceFoundName, "Blade Hawk Controller")) {
                    if (result.getDevice() != null) {
                        bluetoothDevice = result.getDevice();
                        connect(bluetoothDevice);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    };
    @UsedByGodot
    private void startScan() {
        try {
            if (getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            if (!scanning) {
                handler.postDelayed(()->{
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }, SCAN_PERIOD);

                scanning = true;
                bluetoothLeScanner.startScan(leScanCallback);
            } else {
                scanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        } catch (Exception e){
            Log.w(TAG, "> Unable to scan");
            throw new RuntimeException(e);
        }
    }

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
           Log.i(TAG, "> Connection state change status:  " + status + " new state: " + newState);
        }
    };

    private void connect(BluetoothDevice bluetoothDevice) {
        try {
            if (getActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothDevice.connectGatt(getActivity().getApplicationContext(), true, bluetoothGattCallback);
        } catch (Exception e) {
            Log.w(TAG, "> Unable to connect");
            throw new RuntimeException(e);
        }
    }


    /**
     * Experimental method: getPluginSignals.
     * NOTE: This method has not been validated; its usage and effectiveness require testing.
     */
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new HashSet<>();
        signals.add(new SignalInfo("bt_godot_plugin_signal", String.class));
        signals.add(new SignalInfo("device_found", String.class));
        signals.add(new SignalInfo("data_received", String.class));
        return signals;
    }

    /**
     * Experimental method: delayedSignalTrigger.
     * NOTE: This method has not been validated; its usage and effectiveness require testing.
     */
    private void delayedSignalTrigger() {
        handler.postDelayed(()->{
            emitSignal("bt_godot_plugin_signal", "Hello from Android with Godot Signals!");
        }, 15000);
    }

    /**
     * Experimental method: showToastInAndroid.
     * NOTE: This method has not been validated; its usage and effectiveness require testing.
     */
    @UsedByGodot
    public void showToastInAndroid(String godotMessagePrefix) {
        StringBuilder message = new StringBuilder();

        message.append(godotMessagePrefix).append(" ").append("Hello from Android!");

        runOnUiThread(()->{
            Toast.makeText(getActivity(), message.toString(), Toast.LENGTH_LONG).show();
        });
    }

}
