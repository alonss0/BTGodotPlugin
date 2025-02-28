package com.alonss0.godotplugin;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class BTGodotPlugin extends GodotPlugin {

    private static final String TAG = "BTGodotPlugin";
    // Typical descriptor for BLE notifications
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private final Activity activity;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private boolean isConnected = false;

    /**
     * Base constructor passing a {@link Godot} instance through which the plugin can access Godot's
     * APIs and lifecycle events.
     *
     * @param godot
     */
    public BTGodotPlugin(Godot godot) {
        super(godot);
        this.activity = getActivity();
        initBluetooth();
    }

    private void initBluetooth() {
        if (activity.getSystemService(Context.BLUETOOTH_SERVICE) instanceof BluetoothManager) {
            bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            emitSignal("on_subscription_error", "BluetoothManager not available");
        }
    }
    @SuppressLint("MissingPermission")
    @UsedByGodot
    public void connectToDevice(String deviceAddress) {
        if (!checkBlePermissions()) {
            emitSignal("on_subscription_error", "BLE permissions are not granted.");
            return;
        }

        if (bluetoothAdapter == null || deviceAddress == null) {
            emitSignal("on_subscription_error", "BluetoothAdapter not initialized or invalid address.");
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            emitSignal("on_subscription_error", "Device not found. Unable to connect.");
            return;
        }

        // Close any existing GATT connection
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        bluetoothGatt = device.connectGatt(activity, false, gattCallback);
        Log.d(TAG, "Trying to create a new GATT connection.");
    }

    // Subscribe to the characteristic once we have discovered services
    @SuppressLint("MissingPermission")
    @UsedByGodot
    public void subscribeToCharacteristic(String serviceUUID, String characteristicUUID) {
        if (!isConnected || bluetoothGatt == null) {
            emitSignal("on_subscription_error", "Not connected to a BLE device.");
            return;
        }
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUUID));
        if (service == null) {
            emitSignal("on_subscription_error", "Service not found: " + serviceUUID);
            return;
        }
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristicUUID));
        if (characteristic == null) {
            emitSignal("on_subscription_error", "Characteristic not found: " + characteristicUUID);
            return;
        }

        // Enable notification
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
        emitSignal("on_subscription_success", "Subscribed to characteristic.");
        Log.d(TAG, "Subscribed to characteristic: " + characteristicUUID);
    }

    @SuppressLint("MissingPermission")
    @UsedByGodot
    public void disconnectDevice() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            isConnected = false;
            emitSignal("on_connection_state_change", false);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (!checkBlePermissions()) {
                return;
            }

            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                isConnected = true;
                emitSignal("on_connection_state_change", true);

                // Start service discovery
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                isConnected = false;
                emitSignal("on_connection_state_change", false);

                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered.");
                emitSignal("on_services_discovered", "Services discovered successfully.");
            } else {
                Log.d(TAG, "onServicesDiscovered received: " + status);
                emitSignal("on_subscription_error", "Service discovery failed with status: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte[] data = characteristic.getValue();
            if (data != null) {
                int buttonState = data[0]; // 1 = pressed, 0 = released
                emitSignal("on_button_state_changed", buttonState == 1);

                String strValue = new String(data, StandardCharsets.UTF_8);
                emitSignal("on_button_state_string", strValue);
            }
        }
    };

    // Helper method to check BLE permissions (for Android 12+)
    private boolean checkBlePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true; // No specific BLE permissions needed below Android 12
        }
        if (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    @Override
    public String getPluginName() {
        return BTGodotPlugin.TAG;
    }

    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new java.util.HashSet<>();
        signals.add(new SignalInfo("on_connection_state_change", Boolean.class));
        signals.add(new SignalInfo("on_subscription_error", String.class));
        signals.add(new SignalInfo("on_subscription_success", String.class));
        signals.add(new SignalInfo("on_button_state_changed", Boolean.class));
        signals.add(new SignalInfo("on_services_discovered", String.class));
        signals.add(new SignalInfo("on_button_state_string", String.class));
        return signals;
    }
}
