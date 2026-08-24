package com.celfii.ventas;

import android.os.Bundle;
import android.widget.Toast;

import com.journeyapps.barcodescanner.CaptureActivity;

public final class BarcodeScannerActivity extends CaptureActivity {
    @Override protected void onCreate(Bundle state) {
        try {
            super.onCreate(state);
        } catch (RuntimeException error) {
            Toast.makeText(this, "No se pudo abrir la cámara", Toast.LENGTH_LONG).show();
            finish();
        }
    }
}
