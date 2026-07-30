package com.celfii.pos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 11, 10);
    private static final int PANEL = Color.rgb(17, 20, 18);
    private static final int LIME = Color.rgb(170, 255, 0);
    private static final int TEXT = Color.rgb(245, 247, 245);
    private static final int MUTED = Color.rgb(157, 165, 159);
    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));

    private ApiClient api;
    private Models.Sale sale;
    private LinearLayout content;
    private LinearLayout productList;
    private LinearLayout cartList;
    private TextView totalText;
    private EditText search;
    private PrinterManager printer;
    private String seller = "Cel-Fii";
    private boolean productsManagement;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(this);
        printer = new PrinterManager(this);
        requestNeededPermissions();
        newSale();
        buildShell();
        showSale();
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 31 && !printer.hasPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, 100);
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(14), dp(8), dp(14), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_celfii);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        TextView brandName = text("CEL-FII", 25, LIME, true);
        TextView brandMode = text("VENTAS", 12, MUTED, true);
        brandMode.setLetterSpacing(0.25f);
        brandCopy.addView(brandName);
        brandCopy.addView(brandMode);
        LinearLayout.LayoutParams brandTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandTextParams.leftMargin = dp(10);
        brand.addView(brandCopy, brandTextParams);
        root.addView(brand, matchWrap());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(7), dp(8), dp(9));
        nav.setBackgroundColor(PANEL);
        addNav(nav, "VENTA", this::showSale);
        addNav(nav, "PRODUCTOS", this::showProducts);
        addNav(nav, "HISTORIAL", () -> showMessagePage(
                "Historial", "Ventas, reimpresión y devoluciones."));
        addNav(nav, "MÁS", this::showMore);
        root.addView(nav, matchWrap());
        setContentView(root);
    }

    private void showSale() {
        productsManagement = false;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(18));
        scroll.addView(body);

        TextView title = text("Nueva venta", 27, TEXT, true);
        body.addView(title);
        TextView meta = text("Vendedor: " + seller, 13, MUTED, false);
        meta.setPadding(0, dp(3), 0, dp(12));
        body.addView(meta);

        addSearchControls(body);

        TextView productsTitle = text("Productos", 17, TEXT, true);
        body.addView(productsTitle, marginBottom(8));
        productList = new LinearLayout(this);
        productList.setOrientation(LinearLayout.VERTICAL);
        body.addView(productList, matchWrap());

        TextView cartTitle = text("Ticket", 17, TEXT, true);
        body.addView(cartTitle, marginTopBottom(18, 8));
        cartList = new LinearLayout(this);
        cartList.setOrientation(LinearLayout.VERTICAL);
        body.addView(cartList, matchWrap());

        totalText = text("TOTAL  " + money(sale.total()), 23, LIME, true);
        totalText.setGravity(Gravity.END);
        totalText.setPadding(0, dp(12), 0, dp(10));
        body.addView(totalText);

        Button finish = button("COBRAR Y FINALIZAR", true);
        finish.setOnClickListener(v -> openPayments());
        body.addView(finish, matchWrap());

        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        renderCart();
        loadProducts();
    }

    private void loadProducts() {
        productList.removeAllViews();
        productList.addView(text("Cargando productos…", 13, MUTED, false));
        api.loadProducts(search.getText().toString(), new ApiClient.Callback<>() {
            @Override public void onSuccess(List<Models.Product> value) {
                runOnUiThread(() -> renderProducts(value));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> toast(message));
            }
        });
    }

    private void renderProducts(List<Models.Product> products) {
        productList.removeAllViews();
        if (products.isEmpty()) {
            productList.addView(text("No encontramos productos.", 13, MUTED, false));
            return;
        }
        for (Models.Product product : products) {
            LinearLayout row = panel();
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(product.name, 15, TEXT, true));
            info.addView(text(product.category + " · Stock " + product.stock,
                    12, MUTED, false));
            info.addView(text(money(product.cashPrice), 16, LIME, true));
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button add = button(productsManagement ? "VER" : "+", true);
            add.setMinWidth(dp(48));
            add.setOnClickListener(v -> {
                if (productsManagement) showProductDetails(product);
                else addProduct(product);
            });
            row.addView(add, new LinearLayout.LayoutParams(
                    productsManagement ? dp(76) : dp(52), dp(48)));
            productList.addView(row, marginBottom(8));
        }
    }

    private void addProduct(Models.Product product) {
        for (Models.CartLine line : sale.lines) {
            if (line.product.id.equals(product.id)) {
                if (line.quantity < product.stock) line.quantity++;
                renderCart();
                return;
            }
        }
        if (product.stock <= 0) {
            toast("Sin stock disponible");
            return;
        }
        sale.lines.add(new Models.CartLine(product, 1, product.cashPrice));
        renderCart();
    }

    private void renderCart() {
        if (cartList == null || totalText == null) return;
        cartList.removeAllViews();
        if (sale.lines.isEmpty()) {
            cartList.addView(text("Todavía no agregaste productos.", 13, MUTED, false));
        }
        for (Models.CartLine line : sale.lines) {
            LinearLayout row = panel();
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(text(line.product.name, 14, TEXT, true));
            info.addView(text(line.quantity + " × " + money(line.unitPrice)
                    + "  ·  " + money(line.total()), 13, MUTED, false));
            row.addView(info, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button minus = button("−", false);
            minus.setOnClickListener(v -> {
                line.quantity--;
                if (line.quantity <= 0) sale.lines.remove(line);
                renderCart();
            });
            row.addView(minus, new LinearLayout.LayoutParams(dp(52), dp(48)));
            cartList.addView(row, marginBottom(8));
        }
        totalText.setText("TOTAL  " + money(sale.total()));
    }

    private void openPayments() {
        if (sale.lines.isEmpty()) {
            toast("Agregá al menos un producto");
            return;
        }
        sale.payments.clear();
        showPaymentDialog();
    }

    private void showPaymentDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        Spinner methods = new Spinner(this);
        String[] options = {"Efectivo", "Transferencia", "Débito", "Crédito"};
        methods.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options));
        box.addView(methods);

        EditText amount = input("Importe");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(String.valueOf(Math.max(0, sale.total() - sale.paid())));
        box.addView(amount, marginTopBottom(10, 8));

        EditText installments = input("Cuotas (solo crédito)");
        installments.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(installments);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Registrar pago")
                .setMessage("Pendiente: " + money(sale.total() - sale.paid()))
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Agregar otro", null)
                .setPositiveButton("Finalizar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (addPayment(methods, amount, installments)) {
                    dialog.dismiss();
                    showPaymentDialog();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!addPayment(methods, amount, installments)) return;
                if (Math.abs(sale.total() - sale.paid()) > 0.01) {
                    toast("Los pagos deben coincidir con el total");
                    return;
                }
                dialog.dismiss();
                persistSale();
            });
        });
        dialog.show();
    }

    private boolean addPayment(Spinner methods, EditText amount, EditText installments) {
        try {
            String method = String.valueOf(methods.getSelectedItem());
            double value = Double.parseDouble(amount.getText().toString().replace(",", "."));
            int count = installments.getText().toString().isBlank()
                    ? 0 : Integer.parseInt(installments.getText().toString());
            if (value <= 0) throw new IllegalArgumentException();
            if ("Crédito".equals(method) && count <= 0) {
                toast("Indicá la cantidad de cuotas");
                return false;
            }
            if (sale.paid() + value > sale.total() + 0.01) {
                toast("El importe supera el total pendiente");
                return false;
            }
            sale.payments.add(new Models.Payment(method, value, count));
            return true;
        } catch (Exception e) {
            toast("Ingresá un importe válido");
            return false;
        }
    }

    private void persistSale() {
        sale.date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
        if (!api.isConfigured()) {
            toast("Primero conectá la app con Google Sheets desde la pestaña MÁS");
            return;
        }
        toast("Registrando venta…");
        api.createSale(sale, new ApiClient.Callback<>() {
            @Override public void onSuccess(String saleId) {
                runOnUiThread(() -> {
                    sale.id = saleId;
                    choosePrinter();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> toast(message));
            }
        });
    }

    private void choosePrinter() {
        List<BluetoothDevice> devices = printer.pairedDevices();
        if (devices.isEmpty()) {
            toast("Venta guardada. Vinculá la impresora desde Bluetooth para imprimir.");
            completedSale();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice device = devices.get(i);
            names[i] = device.getName() == null ? device.getAddress() : device.getName();
        }
        new AlertDialog.Builder(this)
                .setTitle("Elegir impresora")
                .setItems(names, (dialog, which) -> printTo(devices.get(which)))
                .setNegativeButton("No imprimir", (dialog, which) -> completedSale())
                .show();
    }

    private void printTo(BluetoothDevice device) {
        toast("Imprimiendo…");
        printer.print(device, ReceiptFormatter.format(sale), new PrinterManager.PrintCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    toast("Venta guardada e impresa");
                    completedSale();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    toast("Venta guardada. Error al imprimir: " + message);
                    completedSale();
                });
            }
        });
    }

    private void completedSale() {
        newSale();
        showSale();
    }

    private void newSale() {
        sale = new Models.Sale();
        sale.id = UUID.randomUUID().toString();
        sale.seller = seller;
    }

    private void showMessagePage(String titleValue, String message) {
        content.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.addView(text(titleValue, 27, TEXT, true));
        TextView copy = text(message + "\n\nEste módulo se incorpora en la siguiente etapa.",
                15, MUTED, false);
        copy.setPadding(0, dp(12), 0, 0);
        box.addView(copy);
        content.addView(box);
    }

    private void showProducts() {
        productsManagement = true;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(18));
        scroll.addView(body);

        body.addView(text("Productos", 27, TEXT, true));
        TextView subtitle = text("Catálogo, códigos, precios y stock", 13, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(14));
        body.addView(subtitle);
        addSearchControls(body);

        productList = new LinearLayout(this);
        productList.setOrientation(LinearLayout.VERTICAL);
        body.addView(productList, matchWrap());
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        loadProducts();
    }

    private void addSearchControls(LinearLayout body) {
        search = input("Nombre, modelo o código de barras");
        body.addView(search, matchWrap());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button find = button("BUSCAR", false);
        find.setOnClickListener(v -> loadProducts());
        actions.addView(find, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button scan = button("▣ ESCANEAR", true);
        scan.setOnClickListener(v -> scanBarcode());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        scanParams.leftMargin = dp(8);
        actions.addView(scan, scanParams);
        body.addView(actions, marginTopBottom(8, 14));
    }

    private void scanBarcode() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        scanner.startScan()
                .addOnSuccessListener(barcode -> {
                    String value = barcode.getRawValue();
                    if (value != null) {
                        search.setText(value);
                        loadProducts();
                    }
                })
                .addOnFailureListener(error ->
                        toast("No se pudo abrir el lector: " + error.getMessage()));
    }

    private void showProductDetails(Models.Product product) {
        String message = product.category
                + "\n\nPrecio efectivo: " + money(product.cashPrice)
                + "\nPrecio crédito: " + money(product.cardPrice)
                + "\nStock actual: " + product.stock
                + "\nCódigo interno: " + product.id;
        new AlertDialog.Builder(this)
                .setTitle(product.name)
                .setMessage(message)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void showMore() {
        productsManagement = false;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(18));
        scroll.addView(body);

        body.addView(text("Más", 27, TEXT, true));
        body.addView(menuCard("Conexión con la planilla",
                api.isConfigured() ? "Conectada" : "Pendiente de configurar",
                this::showServerConfiguration));
        body.addView(menuCard("Impresora Bluetooth",
                "Vincular, seleccionar y realizar prueba",
                this::testPrinter));
        body.addView(menuCard("Clientes",
                "Consultar y administrar clientes",
                () -> showMessagePage("Clientes", "Módulo en construcción.")));
        body.addView(menuCard("Ingreso de mercadería",
                "Actualizar cantidades y costos",
                () -> showMessagePage("Ingresos", "Módulo en construcción.")));
        body.addView(menuCard("Informes",
                "Ventas, ganancias y faltantes",
                () -> showMessagePage("Informes", "Módulo en construcción.")));
        body.addView(menuCard("Acerca de",
                "Cel-Fii POS v0.2 · www.cel-fii.com",
                () -> toast("Cel-Fii Tecnología · San Rafael, Mendoza")));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View menuCard(String titleValue, String detail, Runnable action) {
        LinearLayout row = panel();
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(titleValue, 15, TEXT, true));
        copy.addView(text(detail, 12, MUTED, false));
        row.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView arrow = text("›", 28, LIME, true);
        row.addView(arrow);
        row.setOnClickListener(v -> action.run());
        row.setClickable(true);
        row.setLayoutParams(marginBottom(8));
        return row;
    }

    private void showServerConfiguration() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText url = input("URL de Google Apps Script");
        url.setText(api.configuredUrl());
        box.addView(url, marginBottom(10));
        EditText token = input("Token de conexión");
        box.addView(token);
        new AlertDialog.Builder(this)
                .setTitle("Conexión con Google Sheets")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    api.configure(url.getText().toString(), token.getText().toString());
                    toast("Configuración guardada");
                    showMore();
                })
                .show();
    }

    private void testPrinter() {
        Models.Sale test = new Models.Sale();
        test.id = "PRUEBA";
        test.date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
        test.seller = seller;
        test.customerName = "Prueba Cel-Fii";
        test.lines.add(new Models.CartLine(
                new Models.Product("TEST", "Prueba de impresión", "", 0, 0, 1, ""),
                1, 0));
        test.payments.add(new Models.Payment("Prueba", 0, 0));
        sale = test;
        choosePrinter();
    }

    private void addNav(LinearLayout nav, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(LIME);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(v -> action.run());
        nav.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(14), dp(12), dp(10), dp(12));
        layout.setBackgroundResource(R.drawable.panel);
        return layout;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setHintTextColor(MUTED);
        view.setTextColor(TEXT);
        view.setTextSize(15);
        view.setSingleLine(true);
        view.setBackgroundResource(R.drawable.input);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(primary ? BG : LIME);
        view.setBackgroundResource(primary ? R.drawable.button_lime : R.drawable.button_outline);
        return view;
    }

    private static String money(double value) {
        return MONEY.format(value).replace(",00", "");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams marginBottom(int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams marginTopBottom(int top, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }
}
