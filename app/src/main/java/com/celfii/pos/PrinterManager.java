package com.celfii.pos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PrinterManager {
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    interface PrintCallback {
        void onSuccess();
        void onError(String message);
    }

    private final Context context;

    PrinterManager(Context context) {
        this.context = context;
    }

    boolean hasPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    List<BluetoothDevice> pairedDevices() {
        List<BluetoothDevice> result = new ArrayList<>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !hasPermission()) return result;
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded != null) result.addAll(bonded);
        return result;
    }

    @SuppressLint("MissingPermission")
    void print(BluetoothDevice device, byte[] bytes, PrintCallback callback) {
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) throw new IllegalStateException("Bluetooth no disponible");
                adapter.cancelDiscovery();
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                OutputStream output = socket.getOutputStream();
                output.write(bytes);
                output.flush();
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(e.getMessage() == null
                        ? "No se pudo conectar con la impresora" : e.getMessage());
            } finally {
                if (socket != null) {
                    try { socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }
}
