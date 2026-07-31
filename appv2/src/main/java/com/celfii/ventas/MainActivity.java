package com.celfii.ventas;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int BLACK = Color.rgb(9, 11, 10);
    private static final int PANEL = Color.rgb(21, 25, 22);
    private static final int LIME = Color.rgb(170, 255, 0);
    private static final int WHITE = Color.rgb(245, 247, 245);
    private static final int MUTED = Color.rgb(157, 165, 159);

    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        renderApplication();
    }

    private void renderApplication() {
        LinearLayout root = column();
        root.setBackgroundColor(BLACK);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.celfii.ventas.R.drawable.logo_celfii);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout brand = column();
        brand.setPadding(dp(11), 0, 0, 0);
        brand.addView(label("CEL-FII", 25, LIME, true));
        TextView mode = label("VENTAS", 11, MUTED, true);
        mode.setLetterSpacing(.28f);
        brand.addView(mode);
        header.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView version = label("NUEVA · 0.1", 11, LIME, true);
        header.addView(version);
        root.addView(header);

        content = column();
        content.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nav = row();
        nav.setBackgroundColor(PANEL);
        nav.setPadding(dp(5), dp(5), dp(5), dp(8));
        navButton(nav, "VENTA", () -> showPage("Nueva venta",
                "Base estable lista. El siguiente módulo será el catálogo y el carrito."));
        navButton(nav, "PRODUCTOS", () -> showPage("Productos",
                "Aquí se cargarán los artículos y fotos de Cel-Fii Stock Real."));
        navButton(nav, "HISTORIAL", () -> showPage("Historial",
                "Aquí se guardarán ventas, pagos y reimpresiones."));
        navButton(nav, "MÁS", () -> showPage("Más",
                "Configuración de Google Sheets e impresora Bluetooth TP-POS58."));
        root.addView(nav);

        setContentView(root);
        showPage("Cel-Fii Ventas", "Aplicación nueva iniciada correctamente.");
    }

    private void showPage(String title, String description) {
        content.removeAllViews();
        content.addView(label(title, 28, WHITE, true));
        TextView copy = label(description, 15, MUTED, false);
        copy.setPadding(0, dp(12), 0, 0);
        content.addView(copy);

        LinearLayout status = column();
        status.setPadding(dp(16), dp(16), dp(16), dp(16));
        status.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(22);
        status.addView(label("● SISTEMA ESTABLE", 13, LIME, true));
        TextView detail = label("Sin base anterior · Sin permisos al iniciar · Sin conexión automática", 12, MUTED, false);
        detail.setPadding(0, dp(7), 0, 0);
        status.addView(detail);
        content.addView(status, params);
    }

    private void navButton(LinearLayout nav, String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(LIME);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(view -> action.run());
        nav.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
