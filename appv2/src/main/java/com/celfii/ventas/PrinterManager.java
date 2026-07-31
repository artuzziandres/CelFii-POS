package com.celfii.ventas;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.OutputStream;
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

    PrinterManager(Context context) { this.context = context; }

    @SuppressLint("MissingPermission")
    void printPreferred(byte[] bytes, PrintCallback callback) {
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) throw new IllegalStateException("Bluetooth no disponible");
                if (!adapter.isEnabled()) throw new IllegalStateException("Encendé el Bluetooth");
                BluetoothDevice printer = preferredDevice(adapter.getBondedDevices());
                if (printer == null) throw new IllegalStateException(
                        "No se encontró la TP.POS58 emparejada");
                adapter.cancelDiscovery();
                socket = printer.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                OutputStream output = socket.getOutputStream();
                output.write(bytes);
                output.flush();
                callback.onSuccess();
            } catch (Exception error) {
                callback.onError(error.getMessage() == null
                        ? "No se pudo conectar con la impresora" : error.getMessage());
            } finally {
                if (socket != null) try { socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private BluetoothDevice preferredDevice(Set<BluetoothDevice> devices) {
        if (devices == null || devices.isEmpty()) return null;
        BluetoothDevice only = devices.size() == 1 ? devices.iterator().next() : null;
        for (BluetoothDevice device : devices) {
            String name = device.getName() == null ? "" : device.getName().toUpperCase();
            if (name.contains("POS58") || name.contains("TP.POS")
                    || name.contains("GLOBAL") || name.contains("RPP")) return device;
        }
        return only;
    }
}
