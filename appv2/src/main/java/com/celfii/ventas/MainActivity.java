package com.celfii.ventas;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.content.pm.PackageManager;
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
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int BLACK = Color.rgb(9, 11, 10);
    private static final int PANEL = Color.rgb(21, 25, 22);
    private static final int LIME = Color.rgb(170, 255, 0);
    private static final int WHITE = Color.rgb(245, 247, 245);
    private static final int MUTED = Color.rgb(157, 165, 159);
    private static final int RED = Color.rgb(255, 105, 105);
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(
            Locale.forLanguageTag("es-AR"));

    private LinearLayout content;
    private TextView syncStatus;
    private final List<Product> catalog = new ArrayList<>();
    private final Map<String, CartLine> cart = new LinkedHashMap<>();
    private CatalogRepository catalogRepository;
    private SaleStore saleStore;
    private ImageLoader imageLoader;
    private PhotoMap photoMap;
    private ProductAdapter activeAdapter;
    private EditText activeSearch;
    private boolean productsTab;
    private String selectedSeller = "Andres";
    private static final int CAMERA_REQUEST = 501;
    private static final String[] SELLERS = {
            "Andres", "Maxi", "Gaby", "Facu", "Malena", "Benjamin", "Alejandra", "Elio"
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        catalogRepository = new CatalogRepository(this);
        saleStore = new SaleStore(this);
        imageLoader = new ImageLoader();
        photoMap = new PhotoMap(this);
        renderApplication();
        showSale();
        synchronize(false);
    }

    private void renderApplication() {
        LinearLayout root = column();
        root.setBackgroundColor(BLACK);
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_celfii);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout brand = column();
        brand.setPadding(dp(10), 0, 0, 0);
        brand.addView(label("CEL-FII", 24, LIME, true));
        TextView mode = label("VENTAS", 11, MUTED, true);
        mode.setLetterSpacing(.25f);
        brand.addView(mode);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        syncStatus = label("● INICIANDO", 10, MUTED, true);
        header.addView(syncStatus);
        root.addView(header);

        content = column();
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = row();
        nav.setBackgroundColor(PANEL);
        nav.setPadding(dp(4), dp(4), dp(4), dp(7));
        navButton(nav, "VENTA", this::showSale);
        navButton(nav, "PRODUCTOS", this::showProducts);
        navButton(nav, "HISTORIAL", this::showHistory);
        navButton(nav, "MÁS", this::showMore);
        root.addView(nav);
        setContentView(root);
    }

    private void showSale() {
        productsTab = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(label("Nueva venta", 26, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button ticket = actionButton("TICKET · " + itemCount(), true);
        ticket.setOnClickListener(v -> showCart());
        titleRow.addView(ticket, new LinearLayout.LayoutParams(dp(126), dp(48)));
        page.addView(titleRow);
        TextView hint = label(catalog.isEmpty() ? "Cargando catálogo…"
                : catalog.size() + " productos disponibles", 12, MUTED, false);
        hint.setPadding(0, 0, 0, dp(8));
        page.addView(hint);
        page.addView(searchBox());
        ListView list = productList();
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout totalRow = row();
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.setPadding(0, dp(8), 0, 0);
        totalRow.addView(label(money(cartTotal()), 23, LIME, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button charge = actionButton("COBRAR", true);
        charge.setOnClickListener(v -> showPayment());
        totalRow.addView(charge, new LinearLayout.LayoutParams(dp(138), dp(54)));
        page.addView(totalRow);
        content.addView(page);
        filterProducts("");
    }

    private void showProducts() {
        productsTab = true;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(label("Productos", 26, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button update = actionButton("ACTUALIZAR", false);
        update.setOnClickListener(v -> synchronize(true));
        titleRow.addView(update, new LinearLayout.LayoutParams(dp(128), dp(48)));
        page.addView(titleRow);
        TextView hint = label(catalog.size() + " artículos de Cel-Fii Stock Real",
                12, MUTED, false);
        hint.setPadding(0, 0, 0, dp(8));
        page.addView(hint);
        page.addView(searchBox());
        page.addView(productList(), new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(page);
        filterProducts("");
    }

    private View searchBox() {
        LinearLayout searchRow = row();
        activeSearch = new EditText(this);
        activeSearch.setHint("Buscar nombre, modelo o código");
        activeSearch.setHintTextColor(MUTED);
        activeSearch.setTextColor(WHITE);
        activeSearch.setSingleLine(true);
        activeSearch.setTextSize(14);
        activeSearch.setPadding(dp(14), 0, dp(14), 0);
        activeSearch.setBackgroundColor(PANEL);
        activeSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchRow.addView(activeSearch, new LinearLayout.LayoutParams(0, dp(52), 1));
        ImageButton scanner = new ImageButton(this);
        scanner.setImageResource(R.drawable.ic_scan);
        scanner.setContentDescription("Escanear código de barras");
        scanner.setBackgroundColor(LIME);
        scanner.setPadding(dp(14), dp(14), dp(14), dp(14));
        scanner.setOnClickListener(v -> scanBarcode());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(56), dp(52));
        scanParams.leftMargin = dp(7);
        searchRow.addView(scanner, scanParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(52));
        rowParams.bottomMargin = dp(8);
        searchRow.setLayoutParams(rowParams);
        return searchRow;
    }

    private void scanBarcode() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        openBarcodeScanner();
    }

    private void openBarcodeScanner() {
        try {
            IntentIntegrator scanner = new IntentIntegrator(this);
            scanner.setCaptureActivity(BarcodeScannerActivity.class);
            scanner.setDesiredBarcodeFormats(IntentIntegrator.PRODUCT_CODE_TYPES);
            scanner.setPrompt("Enfocá el código de barras");
            scanner.setBeepEnabled(true);
            scanner.setBarcodeImageEnabled(false);
            scanner.setOrientationLocked(true);
            scanner.initiateScan();
        } catch (RuntimeException error) {
            toast("No se pudo iniciar la cámara. Revisá el permiso e intentá nuevamente.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode,
                                               android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null && activeSearch != null) {
                activeSearch.setText(result.getContents());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openBarcodeScanner();
        } else if (requestCode == CAMERA_REQUEST) {
            toast("La cámara es necesaria para escanear códigos");
        }
    }

    private ListView productList() {
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(7));
        list.setCacheColorHint(Color.TRANSPARENT);
        activeAdapter = new ProductAdapter();
        list.setAdapter(activeAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Product product = activeAdapter.getItem(position);
            if (productsTab) showProduct(product); else addProduct(product);
        });
        return list;
    }

    private void filterProducts(String query) {
        if (activeAdapter == null) return;
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<Product> result = new ArrayList<>();
        for (Product product : catalog) {
            if (!productsTab && needle.isEmpty() && product.stock <= 0) continue;
            if (needle.isEmpty() || product.searchable().contains(needle)) result.add(product);
            if (result.size() == 120) break;
        }
        activeAdapter.setItems(result);
    }

    private void synchronize(boolean announce) {
        syncStatus.setText("● ACTUALIZANDO");
        syncStatus.setTextColor(LIME);
        if (announce) toast("Actualizando productos…");
        catalogRepository.load(new CatalogRepository.Callback() {
            @Override public void success(List<Product> products, boolean fromCache) {
                runOnUiThread(() -> {
                    catalog.clear();
                    catalog.addAll(products);
                    syncStatus.setText("● " + products.size() + " PRODUCTOS");
                    syncStatus.setTextColor(LIME);
                    if (activeSearch != null) filterProducts(activeSearch.getText().toString());
                    if (announce && !fromCache) toast("Catálogo actualizado");
                });
            }
            @Override public void error(String message) {
                runOnUiThread(() -> {
                    syncStatus.setText("● SIN CONEXIÓN");
                    syncStatus.setTextColor(RED);
                    toast(message);
                });
            }
        });
    }

    private void addProduct(Product product) {
        if (product.stock <= 0) { toast("Producto sin stock"); return; }
        CartLine line = cart.get(product.id);
        if (line == null) cart.put(product.id, new CartLine(product));
        else if (line.quantity < product.stock) line.quantity++;
        else { toast("No hay más stock"); return; }
        showSale();
    }

    private void showCart() {
        if (cart.isEmpty()) { toast("El ticket está vacío"); return; }
        LinearLayout box = column();
        box.setPadding(dp(16), dp(5), dp(16), 0);
        for (CartLine line : cart.values()) {
            LinearLayout row = row();
            row.setPadding(0, dp(7), 0, dp(7));
            LinearLayout copy = column();
            copy.addView(label(line.product.name, 14, WHITE, true));
            copy.addView(label(line.quantity + " × " + money(line.product.cashPrice)
                    + " = " + money(line.total()), 12, MUTED, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Ticket · " + money(cartTotal()))
                .setView(scroll).setNegativeButton("Cerrar", null)
                .setNeutralButton("Vaciar", (d, w) -> { cart.clear(); showSale(); })
                .setPositiveButton("Cobrar", (d, w) -> showPayment()).show();
    }

    private void showPayment() {
        if (cart.isEmpty()) { toast("Agregá productos"); return; }
        LinearLayout box = column();
        box.setPadding(dp(18), dp(5), dp(18), 0);
        Spinner methods = new Spinner(this);
        methods.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Efectivo", "Transferencia", "Posnet"}));
        box.addView(methods);
        TextView sellerLabel = label("Vendedor", 12, MUTED, true);
        sellerLabel.setPadding(0, dp(12), 0, dp(4));
        box.addView(sellerLabel);
        Spinner sellerSpinner = new Spinner(this);
        sellerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SELLERS));
        for (int i = 0; i < SELLERS.length; i++) {
            if (SELLERS[i].equals(selectedSeller)) sellerSpinner.setSelection(i);
        }
        box.addView(sellerSpinner);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cobrar " + money(cartTotal())).setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar venta", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String method = String.valueOf(methods.getSelectedItem());
                    selectedSeller = String.valueOf(sellerSpinner.getSelectedItem());
                    saveSale(method);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void saveSale(String payment) {
        String id = UUID.randomUUID().toString();
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        StringBuilder details = new StringBuilder();
        for (CartLine line : cart.values()) {
            if (details.length() > 0) details.append("\n\n");
            details.append(line.product.name)
                    .append("\n")
                    .append(line.quantity).append(" × ")
                    .append(money(line.product.cashPrice))
                    .append(" = ").append(money(line.total()));
        }
        saleStore.add(id, date, cartTotal(), payment, itemCount(), details.toString(),
                selectedSeller);
        cart.clear();
        toast("Venta guardada correctamente");
        showSale();
    }

    private void showHistory() {
        productsTab = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        page.addView(label("Historial", 26, WHITE, true));
        List<SaleStore.Entry> sales = saleStore.all();
        if (sales.isEmpty()) {
            TextView empty = label("Todavía no hay ventas guardadas.", 14, MUTED, false);
            empty.setPadding(0, dp(14), 0, 0);
            page.addView(empty);
        } else {
            ListView list = new ListView(this);
            list.setDivider(null);
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return sales.size(); }
                @Override public Object getItem(int position) { return sales.get(position); }
                @Override public long getItemId(int position) { return position; }
                @Override public View getView(int position, View recycled, ViewGroup parent) {
                    SaleStore.Entry sale = sales.get(position);
                    LinearLayout card = panel();
                    LinearLayout copy = column();
                    copy.addView(label(sale.date, 14, WHITE, true));
                    copy.addView(label(sale.items + " artículos · " + sale.payment
                                    + " · " + sale.seller,
                            12, MUTED, false));
                    card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                    card.addView(label(money(sale.total), 17, LIME, true));
                    return card;
                }
            });
            list.setOnItemClickListener((parent, view, position, id) ->
                    showSaleDetails(sales.get(position)));
            page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        content.addView(page);
    }

    private void showSaleDetails(SaleStore.Entry sale) {
        String shortId = sale.id.length() > 8 ? sale.id.substring(0, 8).toUpperCase() : sale.id;
        String products = sale.details == null || sale.details.trim().isEmpty()
                ? "El detalle de productos no estaba disponible en esta venta anterior."
                : sale.details;
        String message = "Venta #" + shortId
                + "\nFecha: " + sale.date
                + "\nMedio de pago: " + sale.payment
                + "\nVendedor: " + sale.seller
                + "\nArtículos: " + sale.items
                + "\n\n" + products
                + "\n\nTOTAL: " + money(sale.total);
        new AlertDialog.Builder(this)
                .setTitle("Detalle de venta")
                .setMessage(message)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void showMore() {
        productsTab = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        page.addView(label("Más", 26, WHITE, true));
        Button update = actionButton("ACTUALIZAR CATÁLOGO", true);
        update.setOnClickListener(v -> synchronize(true));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.topMargin = dp(16);
        page.addView(update, params);
        TextView details = label("Planilla: Cel-Fii Stock Real\nPestaña: Articulos\n"
                + "Productos cargados: " + catalog.size()
                + "\n\nPróximos módulos: códigos de barras, pagos combinados, "
                + "sincronización de ventas e impresión TP-POS58.", 14, MUTED, false);
        details.setPadding(0, dp(18), 0, 0);
        page.addView(details);
        content.addView(page);
    }

    private void showProduct(Product product) {
        new AlertDialog.Builder(this).setTitle(product.name)
                .setMessage(product.category + "\n\nEfectivo: " + money(product.cashPrice)
                        + "\nCrédito: " + money(product.creditPrice)
                        + "\nStock: " + product.stock + "\nCódigo: " + product.code)
                .setPositiveButton("Cerrar", null).show();
    }

    private double cartTotal() {
        double total = 0;
        for (CartLine line : cart.values()) total += line.total();
        return total;
    }

    private int itemCount() {
        int count = 0;
        for (CartLine line : cart.values()) count += line.quantity;
        return count;
    }

    private final class ProductAdapter extends BaseAdapter {
        private List<Product> items = new ArrayList<>();
        void setItems(List<Product> value) { items = value; notifyDataSetChanged(); }
        @Override public int getCount() { return items.size(); }
        @Override public Product getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            Product product = getItem(position);
            LinearLayout card = panel();
            ImageView image = new ImageView(MainActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageLoader.load(image, photoMap.urlFor(product));
            card.addView(image, new LinearLayout.LayoutParams(dp(58), dp(58)));
            LinearLayout copy = column();
            copy.setPadding(dp(10), 0, dp(5), 0);
            TextView name = label(product.name, 14, WHITE, true);
            name.setMaxLines(2);
            copy.addView(name);
            copy.addView(label(product.category + " · Stock " + product.stock,
                    11, MUTED, false));
            copy.addView(label(money(product.cashPrice), 16, LIME, true));
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(label(productsTab ? "VER" : "+", productsTab ? 11 : 25, LIME, true),
                    new LinearLayout.LayoutParams(dp(42), dp(42)));
            card.setAlpha(product.stock <= 0 ? .55f : 1f);
            return card;
        }
    }

    private static final class CartLine {
        final Product product;
        int quantity = 1;
        CartLine(Product product) { this.product = product; }
        double total() { return quantity * product.cashPrice; }
    }

    private LinearLayout panel() {
        LinearLayout view = row();
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(11), dp(10), dp(9), dp(10));
        view.setBackgroundColor(PANEL);
        return view;
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

    private Button actionButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? BLACK : LIME);
        button.setBackgroundColor(primary ? LIME : PANEL);
        return button;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private static String money(double value) { return MONEY.format(value).replace(",00", ""); }
}
