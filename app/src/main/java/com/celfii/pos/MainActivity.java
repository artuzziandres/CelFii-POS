package com.celfii.pos;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

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
    private static final int PANEL_2 = Color.rgb(27, 31, 28);
    private static final int LIME = Color.rgb(170, 255, 0);
    private static final int TEXT = Color.rgb(245, 247, 245);
    private static final int MUTED = Color.rgb(157, 165, 159);
    private static final int RED = Color.rgb(255, 105, 105);
    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));

    private ApiClient api;
    private PosDatabase database;
    private PrinterManager printer;
    private Models.Sale sale;
    private FrameLayout content;
    private TextView syncBadge;
    private ProductAdapter productAdapter;
    private EditText searchInput;
    private boolean productMode;
    private String seller = "Cel-Fii";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        api = new ApiClient(this);
        database = new PosDatabase(this);
        printer = new PrinterManager(this);
        requestPermissionsIfNeeded();
        newSale();
        buildShell();
        showSale();
        refreshCatalog(false);
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && !printer.hasPermission()) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN}, 100);
        }
    }

    private void buildShell() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_celfii);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout titles = column();
        titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(text("CEL-FII", 24, LIME, true));
        TextView mode = text("VENTAS", 11, MUTED, true);
        mode.setLetterSpacing(.28f);
        titles.addView(mode);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        syncBadge = text("● LOCAL", 11, MUTED, true);
        syncBadge.setGravity(Gravity.END);
        header.addView(syncBadge);
        root.addView(header, matchWrap());

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = row();
        nav.setPadding(dp(5), dp(5), dp(5), dp(8));
        nav.setBackgroundColor(PANEL);
        addNav(nav, "VENTA", this::showSale);
        addNav(nav, "PRODUCTOS", this::showProducts);
        addNav(nav, "HISTORIAL", this::showHistory);
        addNav(nav, "MÁS", this::showMore);
        root.addView(nav, matchWrap());
        setContentView(root);
    }

    private void showSale() {
        productMode = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("Nueva venta", 26, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button cart = button("TICKET · " + itemCount(), true);
        cart.setOnClickListener(v -> showCart());
        top.addView(cart, new LinearLayout.LayoutParams(dp(126), dp(48)));
        page.addView(top);
        TextView subtitle = text("Vendedor: " + seller + " · " + database.productCount()
                + " productos", 12, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(8));
        page.addView(subtitle);

        page.addView(searchBar());
        ListView list = productListView();
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout checkout = row();
        checkout.setGravity(Gravity.CENTER_VERTICAL);
        checkout.setPadding(0, dp(8), 0, 0);
        TextView total = text(money(sale.total()), 23, LIME, true);
        checkout.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        Button charge = button("COBRAR", true);
        charge.setOnClickListener(v -> openPayments());
        checkout.addView(charge, new LinearLayout.LayoutParams(dp(138), dp(54)));
        page.addView(checkout);
        content.addView(page);
        loadLocalProducts("");
    }

    private void showProducts() {
        productMode = true;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Productos", 26, TEXT, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = button("ACTUALIZAR", false);
        refresh.setOnClickListener(v -> refreshCatalog(true));
        heading.addView(refresh, new LinearLayout.LayoutParams(dp(128), dp(48)));
        page.addView(heading);
        TextView subtitle = text(database.productCount()
                + " artículos guardados en este teléfono", 12, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(8));
        page.addView(subtitle);
        page.addView(searchBar());
        page.addView(productListView(), new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(page);
        loadLocalProducts("");
    }

    private View searchBar() {
        LinearLayout box = row();
        box.setPadding(0, 0, 0, dp(8));
        searchInput = input("Nombre, modelo o código");
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                loadLocalProducts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        box.addView(searchInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button scan = button("▣", true);
        scan.setContentDescription("Escanear código de barras");
        scan.setTextSize(21);
        scan.setOnClickListener(v -> scanBarcode());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(58), dp(52));
        scanParams.leftMargin = dp(7);
        box.addView(scan, scanParams);
        return box;
    }

    private ListView productListView() {
        ListView list = new ListView(this);
        list.setDividerHeight(dp(7));
        list.setDivider(null);
        list.setCacheColorHint(Color.TRANSPARENT);
        list.setBackgroundColor(BG);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(6));
        productAdapter = new ProductAdapter();
        list.setAdapter(productAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Models.Product product = productAdapter.getItem(position);
            if (productMode) showProductDetails(product);
            else addProduct(product);
        });
        return list;
    }

    private void loadLocalProducts(String query) {
        if (productAdapter == null) return;
        List<Models.Product> products = database.products(query, productMode, 120);
        productAdapter.setItems(products);
    }

    private void refreshCatalog(boolean announce) {
        syncBadge.setText("● ACTUALIZANDO");
        syncBadge.setTextColor(LIME);
        if (announce) toast("Actualizando productos desde Google Sheets…");
        api.loadProducts("", new ApiClient.Callback<>() {
            @Override public void onSuccess(List<Models.Product> products) {
                database.replaceProducts(products);
                runOnUiThread(() -> {
                    syncBadge.setText("● " + products.size() + " PRODUCTOS");
                    syncBadge.setTextColor(LIME);
                    if (searchInput != null) loadLocalProducts(searchInput.getText().toString());
                    if (announce) toast("Catálogo actualizado: " + products.size() + " productos");
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    syncBadge.setText("● SIN CONEXIÓN");
                    syncBadge.setTextColor(RED);
                    if (database.productCount() == 0) toast(message);
                });
            }
        });
    }

    private void scanBarcode() {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39)
                .enableAutoZoom().build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        scanner.startScan().addOnSuccessListener(barcode -> {
            String value = barcode.getRawValue();
            if (value == null) return;
            searchInput.setText(value);
            List<Models.Product> found = database.products(value, true, 10);
            Models.Product exact = null;
            for (Models.Product product : found) {
                if (value.equalsIgnoreCase(product.code)
                        || value.equalsIgnoreCase(product.backupCode)
                        || value.equalsIgnoreCase(product.id)) {
                    exact = product;
                    break;
                }
            }
            if (!productMode && exact != null) {
                addProduct(exact);
                searchInput.setText("");
                toast(exact.name + " agregado");
            } else if (found.isEmpty()) {
                toast("Código no encontrado: " + value);
            }
        }).addOnFailureListener(error -> toast("No se pudo abrir el lector"));
    }

    private void addProduct(Models.Product product) {
        if (product.stock <= 0) {
            toast("Este producto figura sin stock");
            return;
        }
        for (Models.CartLine line : sale.lines) {
            if (line.product.id.equals(product.id)) {
                if (line.quantity >= product.stock) toast("No hay más stock disponible");
                else line.quantity++;
                showSale();
                return;
            }
        }
        sale.lines.add(new Models.CartLine(product, 1, product.cashPrice));
        showSale();
    }

    private void showCart() {
        if (sale.lines.isEmpty()) {
            toast("El ticket está vacío");
            return;
        }
        final AlertDialog[] active = new AlertDialog[1];
        LinearLayout box = column();
        box.setPadding(dp(14), dp(5), dp(14), 0);
        for (Models.CartLine line : new ArrayList<>(sale.lines)) {
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout info = column();
            info.addView(text(line.product.name, 14, TEXT, true));
            info.addView(text(line.quantity + " × " + money(line.unitPrice)
                    + " = " + money(line.total()), 12, MUTED, false));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            Button minus = button("−", false);
            minus.setOnClickListener(v -> {
                line.quantity--;
                if (line.quantity <= 0) sale.lines.remove(line);
                if (active[0] != null) active[0].dismiss();
                showCart();
            });
            row.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(45)));
            Button plus = button("+", true);
            plus.setOnClickListener(v -> {
                if (line.quantity < line.product.stock) line.quantity++;
                else toast("No hay más stock");
                if (active[0] != null) active[0].dismiss();
                showCart();
            });
            LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(48), dp(45));
            plusParams.leftMargin = dp(5);
            row.addView(plus, plusParams);
            box.addView(row, marginBottom(9));
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        active[0] = new AlertDialog.Builder(this)
                .setTitle("Ticket · " + money(sale.total()))
                .setView(scroll)
                .setNegativeButton("Seguir vendiendo", (d, w) -> showSale())
                .setNeutralButton("Vaciar", (d, w) -> {
                    sale.lines.clear();
                    showSale();
                })
                .setPositiveButton("Cobrar", (d, w) -> openPayments())
                .create();
        active[0].show();
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
        double pending = sale.total() - sale.paid();
        LinearLayout box = column();
        box.setPadding(dp(20), dp(5), dp(20), 0);
        Spinner methods = new Spinner(this);
        methods.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Efectivo", "Transferencia", "Débito", "Crédito"}));
        box.addView(methods);
        EditText amount = input("Importe");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText(String.format(Locale.US, "%.2f", Math.max(0, pending)));
        box.addView(amount, marginTopBottom(10, 8));
        EditText installments = input("Cantidad de cuotas (solo crédito)");
        installments.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(installments);

        String message = sale.payments.isEmpty() ? "Podés combinar medios de pago."
                : paymentSummary() + "\nPendiente: " + money(pending);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cobrar " + money(sale.total()))
                .setMessage(message).setView(box)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Agregar otro pago", null)
                .setPositiveButton("Finalizar venta", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (addPayment(methods, amount, installments)) {
                    dialog.dismiss();
                    showPaymentDialog();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (!addPayment(methods, amount, installments)) return;
                if (Math.abs(sale.total() - sale.paid()) > .01) {
                    toast("Falta registrar " + money(sale.total() - sale.paid()));
                    return;
                }
                dialog.dismiss();
                persistAndPrint();
            });
        });
        dialog.show();
    }

    private boolean addPayment(Spinner methods, EditText amount, EditText installments) {
        try {
            String method = String.valueOf(methods.getSelectedItem());
            double value = Double.parseDouble(amount.getText().toString().replace(",", "."));
            int count = installments.getText().toString().trim().isEmpty()
                    ? 0 : Integer.parseInt(installments.getText().toString());
            if (value <= 0) throw new IllegalArgumentException();
            if ("Crédito".equals(method) && count <= 0) {
                toast("Indicá la cantidad de cuotas del crédito");
                return false;
            }
            if (sale.paid() + value > sale.total() + .01) {
                toast("El importe supera el saldo pendiente");
                return false;
            }
            sale.payments.add(new Models.Payment(method, value, count));
            return true;
        } catch (Exception error) {
            toast("Ingresá un importe válido");
            return false;
        }
    }

    private String paymentSummary() {
        StringBuilder result = new StringBuilder();
        for (Models.Payment payment : sale.payments) {
            if (result.length() > 0) result.append("\n");
            result.append(payment.method).append(": ").append(money(payment.amount));
        }
        return result.toString();
    }

    private void persistAndPrint() {
        sale.date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        database.saveSale(sale, api.isConfigured() ? "Pendiente" : "Local");
        Models.Sale savedSale = sale;
        if (api.isConfigured()) {
            api.createSale(savedSale, new ApiClient.Callback<>() {
                @Override public void onSuccess(String saleId) {
                    database.markSynced(savedSale.id);
                }
                @Override public void onError(String message) {}
            });
        }
        choosePrinter(savedSale, true);
    }

    private void choosePrinter(Models.Sale toPrint, boolean finishAfter) {
        List<BluetoothDevice> devices = printer.pairedDevices();
        if (devices.isEmpty()) {
            toast("Venta guardada. Primero vinculá la impresora por Bluetooth.");
            if (finishAfter) completedSale();
            return;
        }
        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice device = devices.get(i);
            names[i] = device.getName() == null ? device.getAddress() : device.getName();
        }
        new AlertDialog.Builder(this).setTitle("Imprimir ticket")
                .setItems(names, (dialog, which) ->
                        printTo(devices.get(which), toPrint, finishAfter))
                .setNegativeButton("Guardar sin imprimir",
                        (dialog, which) -> { if (finishAfter) completedSale(); })
                .show();
    }

    private void printTo(BluetoothDevice device, Models.Sale toPrint, boolean finishAfter) {
        toast("Imprimiendo…");
        printer.print(device, ReceiptFormatter.format(toPrint), new PrinterManager.PrintCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> {
                    toast("Ticket impreso correctamente");
                    if (finishAfter) completedSale();
                });
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    toast("Venta guardada. No se pudo imprimir: " + message);
                    if (finishAfter) completedSale();
                });
            }
        });
    }

    private void completedSale() {
        newSale();
        showSale();
    }

    private void showHistory() {
        productMode = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        page.addView(text("Historial", 26, TEXT, true));
        TextView subtitle = text("Últimas ventas guardadas en este teléfono", 12, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(10));
        page.addView(subtitle);
        List<PosDatabase.SaleSummary> sales = database.recentSales();
        ListView list = new ListView(this);
        list.setDividerHeight(dp(7));
        list.setDivider(null);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return sales.size(); }
            @Override public Object getItem(int position) { return sales.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                PosDatabase.SaleSummary item = sales.get(position);
                LinearLayout view = panel();
                LinearLayout copy = column();
                copy.addView(text(item.created, 14, TEXT, true));
                String shortId = item.id.length() > 8 ? item.id.substring(0, 8) : item.id;
                copy.addView(text("#" + shortId + " · " + item.status, 11,
                        "Sincronizada".equals(item.status) ? LIME : MUTED, false));
                view.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                view.addView(text(money(item.total), 17, LIME, true));
                return view;
            }
        });
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        if (sales.isEmpty()) {
            TextView empty = text("Todavía no hay ventas registradas.", 14, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        content.addView(page);
    }

    private void showMore() {
        productMode = false;
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(18));
        page.addView(text("Más", 26, TEXT, true));
        TextView subtitle = text("Configuración y herramientas", 12, MUTED, false);
        subtitle.setPadding(0, 0, 0, dp(10));
        page.addView(subtitle);
        page.addView(menuCard("Sincronizar catálogo",
                database.productCount() + " productos locales · planilla Articulos",
                () -> refreshCatalog(true)));
        page.addView(menuCard("Conexión de ventas con Google Sheets",
                api.isConfigured() ? "Configurada" : "Ventas seguras en modo local",
                this::showServerConfiguration));
        page.addView(menuCard("Impresora Bluetooth",
                "Probar Global TP.POS58-PORTA-USB+BT", this::testPrinter));
        page.addView(menuCard("Clientes", "Preparado para próxima ampliación",
                () -> toast("Clientes se habilitará en una actualización")));
        page.addView(menuCard("Informes", "Ventas locales e indicadores",
                this::showHistory));
        page.addView(menuCard("Acerca de",
                "Cel-Fii Ventas 1.0 · www.cel-fii.com",
                () -> toast("Cel-Fii Tecnología · San Rafael, Mendoza")));
        scroll.addView(page);
        content.addView(scroll);
    }

    private View menuCard(String title, String detail, Runnable action) {
        LinearLayout card = panel();
        card.setClickable(true);
        card.setOnClickListener(v -> action.run());
        LinearLayout copy = column();
        copy.addView(text(title, 15, TEXT, true));
        copy.addView(text(detail, 12, MUTED, false));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(text("›", 28, LIME, true));
        card.setLayoutParams(marginBottom(8));
        return card;
    }

    private void showServerConfiguration() {
        LinearLayout box = column();
        box.setPadding(dp(18), dp(6), dp(18), 0);
        EditText url = input("URL publicada de Google Apps Script");
        url.setText(api.configuredUrl());
        box.addView(url, marginBottom(9));
        EditText token = input("Token de conexión");
        box.addView(token);
        new AlertDialog.Builder(this)
                .setTitle("Sincronización de ventas")
                .setMessage("El catálogo ya se descarga de la planilla. Esta conexión permite escribir las ventas.")
                .setView(box).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    api.configure(url.getText().toString(), token.getText().toString());
                    toast("Configuración guardada");
                    showMore();
                }).show();
    }

    private void testPrinter() {
        Models.Sale test = new Models.Sale();
        test.id = "PRUEBA";
        test.date = new SimpleDateFormat("dd/MM/yyyy HH:mm",
                Locale.getDefault()).format(new Date());
        test.seller = seller;
        test.customerName = "Prueba Cel-Fii";
        test.lines.add(new Models.CartLine(
                new Models.Product("TEST", "Prueba de impresión", "", 0, 0, 1, ""), 1, 0));
        test.payments.add(new Models.Payment("Prueba", 0, 0));
        choosePrinter(test, false);
    }

    private void showProductDetails(Models.Product product) {
        String codes = product.code.isEmpty() ? "Sin código de barras" : product.code;
        new AlertDialog.Builder(this).setTitle(product.name)
                .setMessage(product.category
                        + "\n\nEfectivo: " + money(product.cashPrice)
                        + "\nCrédito: " + money(product.cardPrice)
                        + "\nStock: " + product.stock
                        + "\nCódigo: " + codes
                        + "\nID: " + product.id)
                .setPositiveButton("Cerrar", null).show();
    }

    private void newSale() {
        sale = new Models.Sale();
        sale.id = UUID.randomUUID().toString();
        sale.seller = seller;
    }

    private int itemCount() {
        int total = 0;
        for (Models.CartLine line : sale.lines) total += line.quantity;
        return total;
    }

    private final class ProductAdapter extends BaseAdapter {
        private List<Models.Product> items = new ArrayList<>();
        void setItems(List<Models.Product> value) {
            items = value;
            notifyDataSetChanged();
        }
        @Override public int getCount() { return items.size(); }
        @Override public Models.Product getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            ProductHolder holder;
            if (recycled == null) {
                LinearLayout card = panel();
                ImageView photo = new ImageView(MainActivity.this);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setBackgroundColor(PANEL_2);
                card.addView(photo, new LinearLayout.LayoutParams(dp(66), dp(66)));
                LinearLayout info = column();
                info.setPadding(dp(11), 0, dp(6), 0);
                TextView name = text("", 15, TEXT, true);
                name.setMaxLines(2);
                TextView meta = text("", 11, MUTED, false);
                TextView price = text("", 17, LIME, true);
                info.addView(name);
                info.addView(meta);
                info.addView(price);
                card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                TextView action = text(productMode ? "VER" : "+", productMode ? 12 : 27,
                        LIME, true);
                action.setGravity(Gravity.CENTER);
                card.addView(action, new LinearLayout.LayoutParams(dp(48), dp(48)));
                holder = new ProductHolder(photo, name, meta, price, action);
                card.setTag(holder);
                recycled = card;
            } else holder = (ProductHolder) recycled.getTag();
            Models.Product product = getItem(position);
            holder.name.setText(product.name);
            String code = product.code.isEmpty() ? "" : " · " + product.code;
            holder.meta.setText(product.category + " · Stock " + product.stock + code);
            holder.price.setText(money(product.cashPrice));
            holder.action.setText(productMode ? "VER" : "+");
            Glide.with(MainActivity.this).clear(holder.photo);
            holder.photo.setImageDrawable(null);
            if (!product.imageUrl().isEmpty()) {
                Glide.with(MainActivity.this).load(product.imageUrl())
                        .centerCrop().placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground).into(holder.photo);
            } else holder.photo.setImageResource(R.drawable.ic_launcher_foreground);
            recycled.setAlpha(product.stock <= 0 ? .55f : 1f);
            return recycled;
        }
    }

    private static final class ProductHolder {
        final ImageView photo;
        final TextView name, meta, price, action;
        ProductHolder(ImageView photo, TextView name, TextView meta,
                      TextView price, TextView action) {
            this.photo = photo; this.name = name; this.meta = meta;
            this.price = price; this.action = action;
        }
    }

    private void addNav(LinearLayout nav, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(10);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(LIME);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(v -> action.run());
        nav.addView(button, new LinearLayout.LayoutParams(0, dp(50), 1));
    }

    private LinearLayout panel() {
        LinearLayout view = row();
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(11), dp(10), dp(9), dp(10));
        view.setBackgroundResource(R.drawable.panel);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
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
        view.setTextSize(14);
        view.setSingleLine(true);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setBackgroundResource(R.drawable.input);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(12);
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
        return new LinearLayout.LayoutParams(-1, -2);
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
