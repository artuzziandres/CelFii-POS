package com.celfii.ventas;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.nio.charset.Charset;
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
    private CelFiiApi api;
    private SaleStore saleStore;
    private ImageLoader imageLoader;
    private PhotoMap photoMap;
    private ProductAdapter activeAdapter;
    private EditText activeSearch;
    private EditText scanDestination;
    private PrinterManager printer;
    private byte[] pendingTicket;
    private boolean productsTab;
    private String selectedSeller = "Andres";
    private static final int CAMERA_REQUEST = 501;
    private static final int BLUETOOTH_REQUEST = 502;
    private static final int PRODUCT_PHOTO_REQUEST = 503;
    private Bitmap pendingProductPhoto;
    private ImageView pendingPhotoPreview;
    private static final String[] SELLERS = {
            "Andres", "Maxi", "Gaby", "Facu", "Malena", "Benjamin", "Alejandra", "Elio"
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        catalogRepository = new CatalogRepository(this);
        api = new CelFiiApi(this);
        saleStore = new SaleStore(this);
        imageLoader = new ImageLoader();
        photoMap = new PhotoMap(this);
        printer = new PrinterManager(this);
        renderApplication();
        showSale();
        synchronize(false);
    }

    private void renderApplication() {
        LinearLayout root = column();
        root.setBackgroundColor(BLACK);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_celfii_app);
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
        Button create = actionButton("NUEVO", true);
        create.setOnClickListener(v -> showProductEditor(null));
        titleRow.addView(create, new LinearLayout.LayoutParams(dp(88), dp(48)));
        Button update = actionButton("ACTUALIZAR", false);
        update.setOnClickListener(v -> synchronize(true));
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(dp(112), dp(48));
        updateParams.leftMargin = dp(6);
        titleRow.addView(update, updateParams);
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
        scanner.setOnClickListener(v -> scanBarcode(activeSearch));
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(dp(56), dp(52));
        scanParams.leftMargin = dp(7);
        searchRow.addView(scanner, scanParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(52));
        rowParams.bottomMargin = dp(8);
        searchRow.setLayoutParams(rowParams);
        return searchRow;
    }

    private void scanBarcode(EditText destination) {
        scanDestination = destination;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        openBarcodeScanner();
    }

    private void openBarcodeScanner() {
        try {
            DecoratedBarcodeView camera = new DecoratedBarcodeView(this);
            camera.setMinimumHeight(dp(420));
            camera.setStatusText("Mantené el código dentro del recuadro");
            camera.setDecoderFactory(new DefaultDecoderFactory());
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Enfocá el código de barras")
                    .setView(camera)
                    .setNegativeButton("Cerrar", null)
                    .create();
            camera.decodeContinuous(new BarcodeCallback() {
                @Override public void barcodeResult(BarcodeResult result) {
                    camera.pause();
                    if (scanDestination != null) scanDestination.setText(result.getText());
                    scanDestination = null;
                    dialog.dismiss();
                }
            });
            dialog.setOnShowListener(value -> camera.resume());
            dialog.setOnDismissListener(value -> camera.pause());
            dialog.show();
        } catch (RuntimeException error) {
            toast("No se pudo iniciar la cámara. Revisá el permiso e intentá nuevamente.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openBarcodeScanner();
        } else if (requestCode == CAMERA_REQUEST) {
            toast("La cámara es necesaria para escanear códigos");
        } else if (requestCode == BLUETOOTH_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingTicket != null) {
            byte[] ticket = pendingTicket;
            pendingTicket = null;
            printDirect(ticket);
        } else if (requestCode == BLUETOOTH_REQUEST) {
            pendingTicket = null;
            toast("Se necesita permiso de Bluetooth para imprimir");
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
        if (api.configured()) {
            api.products(new CelFiiApi.Callback<>() {
                @Override public void success(List<Product> products) {
                    runOnUiThread(() -> applyCatalog(products, false, announce));
                }
                @Override public void error(String message) {
                    runOnUiThread(() -> {
                        if (announce) toast("Conector: " + message + ". Usando copia local.");
                        loadFallbackCatalog(announce);
                    });
                }
            });
        } else loadFallbackCatalog(announce);
    }

    private void loadFallbackCatalog(boolean announce) {
        catalogRepository.load(new CatalogRepository.Callback() {
            @Override public void success(List<Product> products, boolean fromCache) {
                runOnUiThread(() -> applyCatalog(products, fromCache, announce));
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

    private void applyCatalog(List<Product> products, boolean fromCache, boolean announce) {
        catalog.clear();
        catalog.addAll(products);
        syncStatus.setText("● " + products.size() + " PRODUCTOS");
        syncStatus.setTextColor(LIME);
        if (activeSearch != null) filterProducts(activeSearch.getText().toString());
        if (announce && !fromCache) toast("Catálogo y stock actualizados");
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
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Imprimir", (d, w) -> printCurrentSale()).show();
    }

    private void printCurrentSale() {
        StringBuilder rows = new StringBuilder();
        for (CartLine line : cart.values()) {
            rows.append(line.product.name).append('\n')
                    .append(line.quantity).append(" x ").append(money(line.product.cashPrice))
                    .append("    ").append(money(line.total())).append('\n');
        }
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date());
        printDirect(ticketBytes(date, selectedSeller, "", rows.toString(), cartTotal()));
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
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
                .setTitle("Finalizar venta · " + money(cartTotal())).setView(box)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Solo guardar", null)
                .setPositiveButton("Guardar e imprimir", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String method = String.valueOf(methods.getSelectedItem());
                selectedSeller = String.valueOf(sellerSpinner.getSelectedItem());
                saveSale(method, true);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    String method = String.valueOf(methods.getSelectedItem());
                    selectedSeller = String.valueOf(sellerSpinner.getSelectedItem());
                    saveSale(method, false);
                    dialog.dismiss();
                });
        });
        dialog.show();
    }

    private void saveSale(String payment, boolean printAfterSave) {
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
        final String savedDetails = details.toString();
        if (!api.configured()) {
            toast("La conexión con Google Sheets no está configurada");
            return;
        }
        toast("Guardando venta y descontando stock…");
        api.createSale(id, selectedSeller, payment, cart.values(), new CelFiiApi.Callback<>() {
            @Override public void success(String saleId) {
                runOnUiThread(() -> completeSavedSale(
                        id, date, payment, savedDetails, printAfterSave));
            }
            @Override public void error(String message) {
                runOnUiThread(() -> toast("No se guardó la venta: " + message));
            }
        });
    }

    private void completeSavedSale(String id, String date, String payment, String details,
                                   boolean printAfterSave) {
        saleStore.add(id, date, cartTotal(), payment, itemCount(), details, selectedSeller);
        if (printAfterSave) printCurrentSale();
        toast(printAfterSave ? "Venta guardada. Enviando ticket…" : "Venta guardada");
        finishCompletedSale();
        synchronize(false);
    }

    private void finishCompletedSale() {
        cart.clear();
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
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Imprimir", (d, w) -> printSavedSale(sale))
                .show();
    }

    private void printSavedSale(SaleStore.Entry sale) {
        printDirect(ticketBytes(sale.date, sale.seller, sale.payment,
                sale.details == null ? "" : sale.details, sale.total));
    }

    private void printDirect(byte[] ticket) {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            pendingTicket = ticket;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    BLUETOOTH_REQUEST);
            return;
        }
        toast("Imprimiendo…");
        printer.printPreferred(ticket, new PrinterManager.PrintCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> toast("Ticket impreso correctamente"));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> toast(message));
            }
        });
    }

    private byte[] ticketBytes(String date, String seller, String payment,
                               String details, double total) {
        String line = "--------------------------------";
        String text = "       CEL-FII TECNOLOGIA\n"
                + "      San Rafael, Mendoza\n" + line + "\n"
                + "Fecha: " + date + "\nVendedor: " + seller + "\n"
                + (payment.isEmpty() ? "" : "Pago: " + payment + "\n")
                + line + "\n" + details + "\n" + line + "\n"
                + "TOTAL: " + money(total) + "\n" + line + "\n"
                + "   Gracias por elegir Cel-Fii\n"
                + "        www.cel-fii.com\n\n\n";
        byte[] init = new byte[]{0x1B, 0x40};
        byte[] content = text.getBytes(Charset.forName("CP850"));
        byte[] feed = new byte[]{0x1B, 0x64, 0x03};
        byte[] result = new byte[init.length + content.length + feed.length];
        System.arraycopy(init, 0, result, 0, init.length);
        System.arraycopy(content, 0, result, init.length, content.length);
        System.arraycopy(feed, 0, result, init.length + content.length, feed.length);
        return result;
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
        Button connection = actionButton(api.configured()
                ? "CAMBIAR CONEXIÓN" : "CONFIGURAR CONEXIÓN", false);
        connection.setOnClickListener(v -> showConnectionSetup());
        LinearLayout.LayoutParams connectionParams = new LinearLayout.LayoutParams(-1, dp(54));
        connectionParams.topMargin = dp(8);
        page.addView(connection, connectionParams);
        TextView details = label("Planilla: Cel-Fii Stock Real\nPestaña: Articulos\n"
                + "Productos cargados: " + catalog.size()
                + "\n\nPróximos módulos: códigos de barras, pagos combinados, "
                + "sincronización de ventas e impresión TP-POS58.", 14, MUTED, false);
        details.setPadding(0, dp(18), 0, 0);
        page.addView(details);
        content.addView(page);
    }

    private void showConnectionSetup() {
        EditText token = editorInput("Token de conexión", "", false);
        token.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Conectar Google Sheets")
                .setMessage("Pegá el token de Apps Script. Se guardará solamente en este teléfono.")
                .setView(token)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Verificar y guardar", null)
                .create();
        dialog.setOnShowListener(value -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String candidate = token.getText().toString().trim();
                    if (candidate.isEmpty()) { toast("Pegá el token de conexión"); return; }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    toast("Verificando conexión…");
                    api.configureToken(candidate, new CelFiiApi.Callback<>() {
                        @Override public void success(Boolean ignored) {
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                toast("Google Sheets conectado correctamente");
                                synchronize(true);
                                showMore();
                            });
                        }
                        @Override public void error(String message) {
                            runOnUiThread(() -> {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                toast("No se pudo conectar: " + message);
                            });
                        }
                    });
                }));
        dialog.show();
    }

    private void showProduct(Product product) {
        new AlertDialog.Builder(this).setTitle(product.name)
                .setMessage(product.category + "\n\nEfectivo: " + money(product.cashPrice)
                        + "\nCrédito: " + money(product.creditPrice)
                        + "\nStock: " + product.stock + "\nCódigo: " + product.code)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Editar", (dialog, which) -> showProductEditor(product)).show();
    }

    private void showProductEditor(Product current) {
        if (!api.configured()) { toast("Falta configurar Google Sheets"); return; }
        pendingProductPhoto = null;
        LinearLayout form = column();
        form.setPadding(dp(18), dp(4), dp(18), dp(8));
        pendingPhotoPreview = new ImageView(this);
        pendingPhotoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (current != null) imageLoader.load(pendingPhotoPreview, photoMap.urlFor(current));
        else pendingPhotoPreview.setImageResource(R.drawable.logo_celfii_app);
        form.addView(pendingPhotoPreview, new LinearLayout.LayoutParams(-1, dp(145)));
        Button photo = actionButton("ELEGIR FOTO", false);
        photo.setOnClickListener(v -> chooseProductPhoto());
        form.addView(photo, new LinearLayout.LayoutParams(-1, dp(48)));
        EditText name = editorInput("Nombre", current == null ? "" : current.name, false);
        EditText category = editorInput("Categoría", current == null ? "" : current.category, false);
        EditText cash = editorInput("Precio efectivo", current == null ? "" : plainNumber(current.cashPrice), true);
        EditText card = editorInput("Precio Posnet", current == null ? "" : plainNumber(current.creditPrice), true);
        EditText stock = editorInput("Stock inicial", current == null ? "0" : String.valueOf(current.stock), true);
        stock.setEnabled(current == null);
        EditText code = editorInput("Código de barras", current == null ? "" : current.code, false);
        EditText backup = editorInput("Código alternativo", current == null ? "" : current.backupCode, false);
        LinearLayout codeRow = row();
        code.setLayoutParams(new LinearLayout.LayoutParams(0, dp(52), 1));
        codeRow.addView(code);
        ImageButton codeScanner = new ImageButton(this);
        codeScanner.setImageResource(R.drawable.ic_scan);
        codeScanner.setContentDescription("Escanear código del producto");
        codeScanner.setBackgroundColor(LIME);
        codeScanner.setPadding(dp(14), dp(14), dp(14), dp(14));
        codeScanner.setOnClickListener(v -> scanBarcode(code));
        LinearLayout.LayoutParams codeScannerParams =
                new LinearLayout.LayoutParams(dp(56), dp(52));
        codeScannerParams.leftMargin = dp(7);
        codeRow.addView(codeScanner, codeScannerParams);
        LinearLayout.LayoutParams codeRowParams = new LinearLayout.LayoutParams(-1, dp(52));
        codeRowParams.topMargin = dp(7);
        codeRow.setLayoutParams(codeRowParams);
        form.addView(name); form.addView(category); form.addView(cash); form.addView(card);
        form.addView(stock); form.addView(codeRow); form.addView(backup);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(current == null ? "Nuevo producto" : "Editar producto")
                .setView(scroll).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (name.getText().toString().trim().isEmpty()) {
                        toast("Ingresá el nombre del producto"); return;
                    }
                    Product value = new Product(current == null ? "" : current.id,
                            name.getText().toString().trim(), category.getText().toString().trim(),
                            decimal(cash), decimal(card), current == null ? integer(stock) : current.stock,
                            current == null ? "" : current.photo, code.getText().toString().trim(),
                            backup.getText().toString().trim(), current == null ? "Accesorio" : current.type,
                            current == null ? "" : current.description,
                            current == null ? 0 : current.minimumStock);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    saveProduct(value, integer(stock), current == null, dialog);
                }));
        dialog.show();
    }

    private void saveProduct(Product value, int initialStock, boolean creating, AlertDialog dialog) {
        toast("Guardando producto…");
        api.saveProduct(value, initialStock, creating, new CelFiiApi.Callback<>() {
            @Override public void success(String productId) {
                Bitmap photo = pendingProductPhoto;
                if (photo == null) runOnUiThread(() -> productSaved(dialog));
                else api.uploadPhoto(productId, photo, new CelFiiApi.Callback<>() {
                    @Override public void success(String path) {
                        runOnUiThread(() -> productSaved(dialog));
                    }
                    @Override public void error(String message) {
                        runOnUiThread(() -> {
                            toast("Producto guardado; la foto falló: " + message);
                            productSaved(dialog);
                        });
                    }
                });
            }
            @Override public void error(String message) {
                runOnUiThread(() -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("No se guardó: " + message);
                });
            }
        });
    }

    private void productSaved(AlertDialog dialog) {
        pendingProductPhoto = null;
        pendingPhotoPreview = null;
        dialog.dismiss();
        toast("Producto guardado en Google Sheets");
        synchronize(true);
    }

    private void chooseProductPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Elegir foto"), PRODUCT_PHOTO_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PRODUCT_PHOTO_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            Bitmap original = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            int max = Math.max(original.getWidth(), original.getHeight());
            if (max > 1200) {
                float scale = 1200f / max;
                pendingProductPhoto = Bitmap.createScaledBitmap(original,
                        Math.round(original.getWidth() * scale),
                        Math.round(original.getHeight() * scale), true);
            } else pendingProductPhoto = original;
            if (pendingPhotoPreview != null) pendingPhotoPreview.setImageBitmap(pendingProductPhoto);
        } catch (Exception error) { toast("No se pudo abrir la foto"); }
    }

    private EditText editorInput(String hint, String value, boolean numeric) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setBackgroundColor(PANEL);
        input.setPadding(dp(12), 0, dp(12), 0);
        if (numeric) input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(7);
        input.setLayoutParams(params);
        return input;
    }

    private static double decimal(EditText input) {
        try { return Double.parseDouble(input.getText().toString().replace(",", ".")); }
        catch (Exception ignored) { return 0; }
    }

    private static int integer(EditText input) {
        return Math.max(0, (int) Math.floor(decimal(input)));
    }

    private static String plainNumber(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
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

    static final class CartLine {
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
