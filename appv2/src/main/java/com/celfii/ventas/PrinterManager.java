package com.celfii.ventas;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
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

    PrinterManager(android.content.Context context) {}

    @SuppressLint("MissingPermission")
    void printPreferred(byte[] bytes, PrintCallback callback) {
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) throw new IllegalStateException("Bluetooth no disponible");
                if (!adapter.isEnabled()) throw new IllegalStateException("Encendé el Bluetooth");
                List<BluetoothDevice> devices = orderedDevices(adapter.getBondedDevices());
                if (devices.isEmpty()) throw new IllegalStateException(
                        "No se encontró la TP.POS58 emparejada");
                Exception lastError = null;
                for (BluetoothDevice device : devices) {
                    try {
                        socket = connect(device);
                        OutputStream output = socket.getOutputStream();
                        output.write(bytes);
                        output.flush();
                        Thread.sleep(600);
                        callback.onSuccess();
                        return;
                    } catch (Exception error) {
                        lastError = error;
                        if (socket != null) try { socket.close(); } catch (Exception ignored) {}
                        socket = null;
                    }
                }
                throw new IllegalStateException(lastError == null
                        ? "No se pudo conectar con la TP.POS58"
                        : "No se pudo conectar con la TP.POS58: " + lastError.getMessage());
            } catch (Exception error) {
                callback.onError(error.getMessage() == null
                        ? "No se pudo conectar con la impresora" : error.getMessage());
            } finally {
                if (socket != null) try { socket.close(); } catch (Exception ignored) {}
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private List<BluetoothDevice> orderedDevices(Set<BluetoothDevice> paired) {
        List<BluetoothDevice> devices = new ArrayList<>();
        if (paired != null) devices.addAll(paired);
        devices.sort(Comparator.comparing(device -> isPrinter(device) ? 0 : 1));
        return devices;
    }

    @SuppressLint("MissingPermission")
    private boolean isPrinter(BluetoothDevice device) {
        String name = device.getName() == null ? "" : device.getName().toUpperCase();
        return name.contains("POS") || name.contains("58") || name.contains("GLOBAL")
                || name.contains("RPP") || name.contains("PRINTER");
    }

    @SuppressLint("MissingPermission")
    private BluetoothSocket connect(BluetoothDevice device) throws Exception {
        Exception last = null;
        BluetoothSocket socket = null;
        try {
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            return socket;
        } catch (Exception error) {
            last = error;
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
        }
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            return socket;
        } catch (Exception error) {
            last = error;
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
        }
        try {
            Method method = device.getClass().getMethod("createRfcommSocket", int.class);
            socket = (BluetoothSocket) method.invoke(device, 1);
            socket.connect();
            return socket;
        } catch (Exception error) {
            if (socket != null) try { socket.close(); } catch (Exception ignored) {}
            throw last == null ? error : last;
        }
    }
}
