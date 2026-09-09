package com.celfii.ventas;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.HorizontalScrollView;
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
import java.util.Calendar;

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
    private TextView productCount;
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
    private boolean historyTab;
    private boolean saleSaving;
    private String selectedSeller = "Andres";
    private static final int CAMERA_REQUEST = 501;
    private static final int BLUETOOTH_REQUEST = 502;
    private static final int PRODUCT_PHOTO_REQUEST = 503;
    private final Bitmap[] pendingProductPhotos = new Bitmap[3];
    private final ImageView[] pendingPhotoPreviews = new ImageView[3];
    private int pendingPhotoSlot;
    private String selectedTypeFilter = "Todos";
    private static final String[] PRODUCT_TYPES = {"Todos", "Accesorios", "Repuestos", "Equipos"};
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
        logo.setImageResource(R.drawable.logo_celfii);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setBackground(rounded(PANEL, LIME, 1, 14));
        logo.setPadding(dp(4), dp(4), dp(4), dp(4));
        header.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
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
        navButton(nav, "PEDIDOS", this::showOrders);
        navButton(nav, "HISTORIAL", this::showHistory);
        navButton(nav, "MÁS", this::showMore);
        root.addView(nav);
        setContentView(root);
    }

    private void showSale() {
        historyTab = false;
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
        productCount = hint;
        addConnectionNotice(page);
        page.addView(typeFilter());
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
        historyTab = false;
        productsTab = true;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        int expired = countExpiredReservations();
        titleRow.addView(label(expired > 0 ? "Productos · ⚠ " + expired : "Productos",
                        26, expired > 0 ? RED : WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button create = actionButton("NUEVO", true);
        create.setOnClickListener(v -> chooseProductType());
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
        productCount = hint;
        addConnectionNotice(page);
        page.addView(typeFilter());
        page.addView(searchBox());
        page.addView(productList(), new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(page);
        filterProducts("");
    }

    private void showOrders() {
        historyTab = false;
        productsTab = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout title = row();
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(label("Pedidos web", 26, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = actionButton("ACTUALIZAR", false);
        refresh.setOnClickListener(v -> showOrders());
        title.addView(refresh, new LinearLayout.LayoutParams(dp(112), dp(48)));
        page.addView(title);
        TextView loading = label("Cargando pagos y comprobantes…", 13, MUTED, false);
        loading.setPadding(0, dp(12), 0, 0);
        page.addView(loading);
        content.addView(page);
        if (!api.configured()) { loading.setText("Primero conectá Google Sheets desde MÁS."); return; }
        api.orders(new CelFiiApi.Callback<>() {
            @Override public void success(List<Order> orders) { runOnUiThread(() -> {
                page.removeView(loading);
                if (orders.isEmpty()) { page.addView(label("Todavía no hay pedidos web.", 14, MUTED, false)); return; }
                ScrollView scroll = new ScrollView(MainActivity.this);
                LinearLayout list = column();
                for (Order order : orders) {
                    Button item = actionButton(order.ticket + " · " + order.status + "\n"
                            + order.customerName + " · " + money(order.total), false);
                    item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    item.setTextColor("Pago informado".equalsIgnoreCase(order.status) ? LIME
                            : order.isClosed() ? MUTED : WHITE);
                    item.setOnClickListener(v -> showOrder(order));
                    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(68));
                    p.topMargin = dp(7); list.addView(item, p);
                }
                scroll.addView(list); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            }); }
            @Override public void error(String message) { runOnUiThread(() ->
                    loading.setText("No se cargaron los pedidos: " + message)); }
        });
    }

    private void showOrder(Order order) {
        LinearLayout box = column(); box.setPadding(dp(18), dp(5), dp(18), 0);
        box.addView(label(order.status, 16, order.isClosed() ? MUTED : LIME, true));
        box.addView(label(order.date + "\nCliente: " + order.customerName + "\nTeléfono: "
                + order.customerPhone + "\nEntrega: " + order.delivery
                + (order.address.isBlank() ? "" : "\nDirección: " + order.address)
                + "\n\n" + order.details + "\n\nTotal informado: " + money(order.total),
                14, WHITE, false));
        ImageView proof = new ImageView(this); proof.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (order.proofAttached) {
            box.addView(label("Comprobante adjunto", 13, LIME, true));
            box.addView(proof, new LinearLayout.LayoutParams(-1, dp(260)));
            api.orderProof(order.ticket, new CelFiiApi.Callback<>() {
                @Override public void success(Bitmap value) { runOnUiThread(() -> proof.setImageBitmap(value)); }
                @Override public void error(String message) { runOnUiThread(() ->
                        proof.setContentDescription("No se pudo abrir: " + message)); }
            });
        } else box.addView(label("Sin comprobante adjunto", 13, RED, true));
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(order.ticket).setView(scroll)
                .setNegativeButton("Cerrar", null).create();
        dialog.setOnShowListener(x -> {
            if (!order.isClosed()) {
                Button cancel = actionButton("CANCELAR PEDIDO", false); cancel.setTextColor(RED);
                cancel.setOnClickListener(v -> confirmCancelOrder(order, dialog)); box.addView(cancel);
                Button release = actionButton("CONFIRMAR PAGO Y LIBERAR", true);
                release.setEnabled(order.proofAttached);
                release.setOnClickListener(v -> confirmReleaseOrder(order, dialog)); box.addView(release);
            }
        });
        dialog.show();
    }

    private void confirmReleaseOrder(Order order, AlertDialog parent) {
        new AlertDialog.Builder(this).setTitle("Confirmar pago")
                .setMessage("Verificá primero que el dinero haya ingresado realmente a Mercado Pago.\n\n"
                        + order.ticket + " · " + money(order.total))
                .setNegativeButton("Volver", null).setPositiveButton("PAGO VERIFICADO", (d, w) -> {
                    api.confirmOrder(order.ticket, selectedSeller, new CelFiiApi.Callback<>() {
                        @Override public void success(String saleId) { runOnUiThread(() -> {
                            parent.dismiss(); toast("Venta liberada · " + saleId);
                            synchronize(false); showOrders();
                        }); }
                        @Override public void error(String message) { runOnUiThread(() ->
                                toast("No se liberó: " + message)); }
                    });
                }).show();
    }

    private void confirmCancelOrder(Order order, AlertDialog parent) {
        new AlertDialog.Builder(this).setTitle("Cancelar " + order.ticket)
                .setMessage("Se liberarán los equipos reservados y el pedido quedará registrado como cancelado.")
                .setNegativeButton("Volver", null).setPositiveButton("CANCELAR PEDIDO", (d, w) ->
                        api.cancelOrder(order.ticket, new CelFiiApi.Callback<>() {
                            @Override public void success(String value) { runOnUiThread(() -> {
                                parent.dismiss(); toast("Pedido cancelado"); synchronize(false); showOrders();
                            }); }
                            @Override public void error(String message) { runOnUiThread(() ->
                                    toast("No se canceló: " + message)); }
                        })).show();
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

    private View typeFilter() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = row();
        chips.setPadding(0, dp(2), dp(6), dp(2));
        for (String type : PRODUCT_TYPES) {
            boolean selected = type.equals(selectedTypeFilter);
            Button chip = new Button(this);
            chip.setAllCaps(false);
            chip.setText(type);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            chip.setTextColor(selected ? BLACK : LIME);
            chip.setBackground(rounded(selected ? LIME : PANEL, LIME, 1, 22));
            chip.setPadding(dp(18), 0, dp(18), 0);
            chip.setOnClickListener(v -> {
                selectedTypeFilter = type;
                refreshCurrentCatalogPage();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(-2, dp(44));
            chipParams.rightMargin = dp(7);
            chips.addView(chip, chipParams);
        }
        scroll.addView(chips);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.bottomMargin = dp(7);
        scroll.setLayoutParams(params);
        return scroll;
    }

    private void refreshCurrentCatalogPage() {
        if (productsTab) showProducts(); else showSale();
    }

    private void addConnectionNotice(LinearLayout page) {
        if (api.configured()) return;
        Button connect = actionButton("CONECTAR STOCK REAL", true);
        connect.setOnClickListener(v -> showConnectionSetup());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.bottomMargin = dp(8);
        page.addView(connect, params);
        TextView warning = label("Mostrando la última copia guardada. Conectá Apps Script para cargar, editar o eliminar.",
                11, RED, false);
        warning.setPadding(0, 0, 0, dp(8));
        page.addView(warning);
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
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!productsTab) return false;
            showDeleteProduct(activeAdapter.getItem(position), null);
            return true;
        });
        return list;
    }

    private void filterProducts(String query) {
        if (activeAdapter == null) return;
        String needle = query == null ? "" : Product.normalizeSearch(query.trim());
        List<Product> result = new ArrayList<>();
        for (Product product : catalog) {
            if (!productsTab && product.isUnavailableEquipment()) continue;
            if (!matchesTypeFilter(product)) continue;
            if (!productsTab && needle.isEmpty() && product.stock <= 0) continue;
            if (needle.isEmpty() || product.normalizedSearch().contains(needle)) result.add(product);

        }
        if (productsTab && productCount != null) productCount.setText(
                result.size() + " de " + catalog.size() + " artículos");
        activeAdapter.setItems(result);
    }

    private boolean matchesTypeFilter(Product product) {
        if ("Todos".equals(selectedTypeFilter)) return true;
        String type = product.type == null || product.type.isBlank() ? "Accesorio" : product.type;
        String normalized = type.toLowerCase(Locale.ROOT);
        if ("Accesorios".equals(selectedTypeFilter)) return normalized.startsWith("accesorio");
        if ("Repuestos".equals(selectedTypeFilter)) return normalized.startsWith("repuesto");
        return normalized.startsWith("equipo");
    }

    private void chooseProductType() {
        String[] choices = {"Accesorio", "Repuesto", "Equipo"};
        new AlertDialog.Builder(this).setTitle("¿Qué querés cargar?")
                .setItems(choices, (dialog, which) -> showProductEditor(null, choices[which]))
                .setNegativeButton("Cancelar", null).show();
    }

    private void synchronize(boolean announce) {
        api.catalogPhotos(new CelFiiApi.Callback<org.json.JSONObject>() {
            @Override public void success(org.json.JSONObject photos) {
                runOnUiThread(() -> {
                    photoMap.update(photos);
                    if (activeSearch != null) filterProducts(activeSearch.getText().toString());
                });
            }
            @Override public void error(String message) {
                if (announce) runOnUiThread(() -> toast("No se pudieron renovar las fotos. Se conserva la última copia."));
            }
        });
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
                        if (announce) toast("No se pudo actualizar: " + message
                                + ". Revisá la conexión en MÁS. Se mantiene la copia guardada.");
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
        syncStatus.setText("● " + products.size() + (fromCache ? " · COPIA GUARDADA" : " PRODUCTOS"));
        syncStatus.setTextColor(fromCache ? MUTED : LIME);
        if (activeSearch != null) filterProducts(activeSearch.getText().toString());
        int expired = countExpiredReservations();
        if (expired > 0) toast("Hay " + expired + " reserva(s) vencida(s) para revisar");
        if (announce && !fromCache) toast("Catálogo y stock actualizados");
    }

    private int countExpiredReservations() {
        int count = 0;
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        format.setLenient(false);
        Date today = new Date();
        for (Product product : catalog) if (product.isReserved()
                && product.reservationExpiry != null && !product.reservationExpiry.isBlank()) {
            try { if (format.parse(product.reservationExpiry).before(today)) count++; }
            catch (Exception ignored) { }
        }
        return count;
    }

    private void addProduct(Product product) {
        if (saleSaving) { toast("Esperá a que termine de guardarse la venta"); return; }
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
            copy.addView(label(line.quantity + " × " + money(line.unitPrice)
                    + " = " + money(line.total()), 12, MUTED, false));
            row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button price = actionButton("PRECIO", false);
            price.setOnClickListener(v -> changeCartPrice(line));
            row.addView(price, new LinearLayout.LayoutParams(dp(92), dp(42)));
            box.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        new AlertDialog.Builder(this).setTitle("Ticket · " + money(cartTotal()))
                .setView(scroll)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Imprimir", (d, w) -> printCurrentSale()).show();
    }

    private void changeCartPrice(CartLine line) {
        if (saleSaving) { toast("La venta se está guardando"); return; }
        EditText value = editorInput("Precio final", plainNumber(line.unitPrice), true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(line.product.name)
                .setView(value).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (decimal(value) <= 0) { toast("Ingresá un precio válido"); return; }
                    line.unitPrice = decimal(value); dialog.dismiss();
                    toast("Precio actualizado. Cerrá y volvé a abrir el ticket para verlo.");
                }));
        dialog.show();
    }

    private void printCurrentSale() {
        StringBuilder rows = new StringBuilder();
        for (CartLine line : cart.values()) {
            rows.append(line.product.name).append('\n')
                    .append(line.product.isEquipment() ? "IMEI: " + line.product.imei + "\n" : "")
                    .append(line.quantity).append(" x ").append(money(line.unitPrice))
                    .append("    ").append(money(line.total())).append('\n');
            if (line.product.isReserved() && line.product.reservationDeposit > 0) {
                rows.append("Seña: ").append(money(line.product.reservationDeposit)).append('\n')
                        .append("Saldo: ").append(money(Math.max(0,
                                line.total() - line.product.reservationDeposit))).append('\n');
            }
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
        if (saleSaving) { toast("La venta anterior todavía se está guardando"); return; }
        if (cart.isEmpty()) { toast("Agregá productos"); return; }
        LinearLayout box = column();
        box.setPadding(dp(18), dp(5), dp(18), 0);
        Spinner methods = new Spinner(this);
        methods.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Efectivo", "Transferencia", "Débito", "Crédito / Posnet", "Mercado Pago"}));
        box.addView(methods);
        box.addView(label("Total a cobrar: " + money(cartTotal()) + " · Revisá el precio final del ticket antes de confirmar.", 13, LIME, true));
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
        boolean hasEquipment = false;
        for (CartLine line : cart.values()) if (line.product.isEquipment()) hasEquipment = true;
        EditText customerName = editorInput("Comprador (opcional)", "", false);
        EditText customerPhone = editorInput("Teléfono (opcional)", "", false);
        int automaticWarrantyDays = 0;
        for (CartLine line : cart.values()) if (line.product.isEquipment()) {
            automaticWarrantyDays = Math.max(automaticWarrantyDays,
                    "Nuevo".equalsIgnoreCase(line.product.condition) ? 180 : 90);
        }
        final int warrantyDays = automaticWarrantyDays;
        if (hasEquipment) {
            box.addView(customerName); box.addView(customerPhone);
            TextView warrantyLabel = label("Garantía incluida: "
                    + (warrantyDays == 180 ? "6 meses" : "3 meses"), 12, LIME, true);
            warrantyLabel.setPadding(0, dp(12), 0, 0);
            box.addView(warrantyLabel);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Finalizar venta · " + money(cartTotal())).setView(box)
                .setNegativeButton("Cancelar", null)
                .setNeutralButton("Solo guardar", null)
                .setPositiveButton("Guardar e imprimir", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String method = String.valueOf(methods.getSelectedItem());
                selectedSeller = String.valueOf(sellerSpinner.getSelectedItem());
                saveSale(method, customerName.getText().toString().trim(),
                        customerPhone.getText().toString().trim(), warrantyDays, true);
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    String method = String.valueOf(methods.getSelectedItem());
                    selectedSeller = String.valueOf(sellerSpinner.getSelectedItem());
                    saveSale(method, customerName.getText().toString().trim(),
                            customerPhone.getText().toString().trim(), warrantyDays, false);
                    dialog.dismiss();
                });
        });
        dialog.show();
    }

    private void saveSale(String payment, String customerName, String customerPhone,
                          int warrantyDays, boolean printAfterSave) {
        String id = UUID.randomUUID().toString();
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());
        StringBuilder details = new StringBuilder();
        for (CartLine line : cart.values()) {
            if (line.product.isEquipment() && (line.product.imei == null
                    || line.product.imei.trim().isEmpty())) {
                toast("Completá el IMEI de " + line.product.name + " antes de vender");
                return;
            }
            if (line.product.isReserved() && line.product.reservationDeposit > line.total()) {
                toast("La seña supera el precio final de " + line.product.name);
                return;
            }
            if (details.length() > 0) details.append("\n\n");
            details.append(line.product.name)
                    .append(line.product.isEquipment() ? "\nIMEI: " + line.product.imei : "")
                    .append("\n")
                    .append(line.quantity).append(" × ")
                    .append(money(line.unitPrice)).append(" = ").append(money(line.total()));
            if (Math.abs(line.unitPrice - line.product.cashPrice) > .01)
                details.append("\nOriginal: ").append(money(line.product.cashPrice))
                        .append(" · Diferencia: ")
                        .append(money((line.unitPrice - line.product.cashPrice) * line.quantity));
        }
        final String savedDetails = details.toString();
        if (!api.configured()) {
            toast("La conexión con Google Sheets no está configurada");
            return;
        }
        if (saleSaving) return;
        saleSaving = true;
        toast("Guardando venta y descontando stock…");
        api.createSale(id, selectedSeller, payment, customerName, customerPhone, warrantyDays,
                cart.values(), new CelFiiApi.Callback<>() {
            @Override public void success(String saleId) {
                runOnUiThread(() -> {
                    saleSaving = false;
                    completeSavedSale(saleId, date, payment, savedDetails, printAfterSave);
                });
            }
            @Override public void error(String message) {
                runOnUiThread(() -> {
                    saleSaving = false;
                    toast("No pudimos confirmar el resultado: " + message + ". Reintentá la misma venta; conservará su identificador.");
                });
            }
        });
    }

    private void completeSavedSale(String id, String date, String payment, String details,
                                   boolean printAfterSave) {
        saleStore.add(id, date, cartTotal(), payment, itemCount(), details, selectedSeller);
        if (printAfterSave) printCurrentSale();
        toast(printAfterSave ? "Venta guardada. Enviando ticket…" : "Venta guardada");
        catalog.removeIf(product -> product.isEquipment() && cart.containsKey(product.id));
        finishCompletedSale();
        synchronize(false);
    }

    private void finishCompletedSale() {
        cart.clear();
        showSale();
    }

    private void showHistory() {
        historyTab = true;
        productsTab = false;
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout title = row();
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(label("Historial", 26, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = actionButton("ACTUALIZAR", false);
        refresh.setOnClickListener(v -> showHistory());
        title.addView(refresh, new LinearLayout.LayoutParams(dp(112), dp(46)));
        page.addView(title);
        TextView loading = label("Actualizando ventas de Google Sheets…", 14, MUTED, false);
        loading.setPadding(0, dp(14), 0, 0);
        page.addView(loading);
        content.addView(page);
        if (!api.configured()) renderHistory(saleStore.all(), "Historial local");
        else api.sales(new CelFiiApi.Callback<>() {
            @Override public void success(List<SaleStore.Entry> sales) {
                runOnUiThread(() -> { if (historyTab) renderHistory(sales, "Sincronizado"); });
            }
            @Override public void error(String message) {
                runOnUiThread(() -> {
                    if (!historyTab) return;
                    toast("Historial: " + message + ". Mostrando copia local.");
                    renderHistory(saleStore.all(), "Historial local");
                });
            }
        });
    }

    private void renderHistory(List<SaleStore.Entry> sales, String source) {
        content.removeAllViews();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(7), dp(14), dp(10));
        LinearLayout title = row();
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(label("Historial", 26, WHITE, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        Button refresh = actionButton("ACTUALIZAR", false);
        refresh.setOnClickListener(v -> showHistory());
        title.addView(refresh, new LinearLayout.LayoutParams(dp(112), dp(46)));
        page.addView(title);
        page.addView(label(source + " · " + sales.size() + " ventas", 12, MUTED, false));
        if (sales.isEmpty()) {
            TextView empty = label("Todavía no hay ventas guardadas.", 14, MUTED, false);
            empty.setPadding(0, dp(14), 0, 0);
            page.addView(empty);
        } else {
            Map<String, List<SaleStore.Entry>> months = new LinkedHashMap<>();
            for (SaleStore.Entry sale : sales) {
                String month = sale.month == null || sale.month.isEmpty()
                        ? "Sin fecha" : sale.month;
                months.computeIfAbsent(month, ignored -> new ArrayList<>()).add(sale);
            }
            ScrollView scroll = new ScrollView(this);
            LinearLayout folders = column();
            boolean first = true;
            for (Map.Entry<String, List<SaleStore.Entry>> month : months.entrySet()) {
                LinearLayout monthSales = column();
                monthSales.setVisibility(first ? View.VISIBLE : View.GONE);
                double monthTotal = 0;
                for (SaleStore.Entry sale : month.getValue()) monthTotal += sale.total;
                Button folder = actionButton(monthName(month.getKey()) + " · "
                        + month.getValue().size() + " VENTAS · " + money(monthTotal), first);
                folder.setOnClickListener(v -> monthSales.setVisibility(
                        monthSales.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
                LinearLayout.LayoutParams folderParams = new LinearLayout.LayoutParams(-1, dp(52));
                folderParams.topMargin = dp(9);
                folders.addView(folder, folderParams);
                for (SaleStore.Entry sale : month.getValue()) {
                    LinearLayout card = panel();
                    card.setOnClickListener(v -> showSaleDetails(sale));
                    LinearLayout copy = column();
                    copy.addView(label(sale.date, 14, WHITE, true));
                    copy.addView(label(sale.items + " artículos · " + sale.payment
                                    + " · " + sale.seller, 12, MUTED, false));
                    card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                    card.addView(label(money(sale.total), 17, LIME, true));
                    LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                    cardParams.topMargin = dp(6);
                    monthSales.addView(card, cardParams);
                }
                folders.addView(monthSales);
                first = false;
            }
            scroll.addView(folders);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        content.addView(page);
    }

    private String monthName(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}")) return "SIN FECHA";
        String[] names = {"ENERO", "FEBRERO", "MARZO", "ABRIL", "MAYO", "JUNIO",
                "JULIO", "AGOSTO", "SEPTIEMBRE", "OCTUBRE", "NOVIEMBRE", "DICIEMBRE"};
        int month = Integer.parseInt(value.substring(5, 7));
        return names[Math.max(1, Math.min(12, month)) - 1] + " " + value.substring(0, 4);
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
        historyTab = false;
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
        String details = product.type + "\n" + product.category + "\n\nPrecio: "
                + money(product.cashPrice) + "\nStock: " + product.stock;
        if (product.isEquipment()) details += "\nIMEI: " + product.imei + "\nMemoria: "
                + product.memory + "\nColor: " + product.color + "\nCondición: "
                + product.condition + "\nBatería: " + product.battery + "\nEstado: "
                + product.equipmentStatus + "\n" + product.observations;
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(product.name)
                .setMessage(details)
                .setPositiveButton("Editar", (dialog, which) -> showProductEditor(product));
        builder.setNegativeButton("Eliminar", (d, w) -> showDeleteProduct(product, null));
        if (product.isEquipment())
            builder.setNeutralButton("Agregar a venta", (d, w) -> addProduct(product));
        builder.show();
    }

    private void showReservation(Product product) {
        LinearLayout box = column();
        box.setPadding(dp(18), dp(4), dp(18), 0);
        EditText customer = editorInput("Cliente (opcional)", "", false);
        EditText phone = editorInput("Teléfono (opcional)", "", false);
        EditText deposit = editorInput("Seña", "0", true);
        EditText days = editorInput("Días de reserva (máximo 30)", "7", true);
        box.addView(customer); box.addView(phone); box.addView(deposit); box.addView(days);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reservar · " + product.name).setView(box)
                .setNegativeButton("Cancelar", null).setNeutralButton("Guardar", null)
                .setPositiveButton("Guardar e imprimir", null).create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    saveReservation(product, customer, phone, deposit, days, false, dialog));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                    saveReservation(product, customer, phone, deposit, days, true, dialog));
        });
        dialog.show();
    }

    private void saveReservation(Product product, EditText customer, EditText phone,
                                 EditText deposit, EditText days, boolean print,
                                 AlertDialog dialog) {
        int duration = Math.max(1, Math.min(30, integer(days)));
        double depositValue = Math.max(0, decimal(deposit));
        if (depositValue > product.cashPrice) { toast("La seña supera el precio"); return; }
        Calendar expiry = Calendar.getInstance(); expiry.add(Calendar.DAY_OF_YEAR, duration);
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Product reserved = product.withReservation("Reservado",
                customer.getText().toString().trim(), phone.getText().toString().trim(),
                depositValue, format.format(new Date()), format.format(expiry.getTime()));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        api.saveProduct(reserved, 1, false, new CelFiiApi.Callback<>() {
            @Override public void success(String id) { runOnUiThread(() -> {
                dialog.dismiss();
                if (print) printReservation(reserved);
                toast("Equipo reservado hasta " + reserved.reservationExpiry);
                synchronize(false);
            }); }
            @Override public void error(String message) { runOnUiThread(() -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                toast("No se guardó la reserva: " + message);
            }); }
        });
    }

    private void cancelReservation(Product product) {
        Product available = product.withReservation("Disponible", "", "", 0, "", "");
        api.saveProduct(available, 1, false, new CelFiiApi.Callback<>() {
            @Override public void success(String id) { runOnUiThread(() -> {
                toast("Reserva cancelada"); synchronize(false); }); }
            @Override public void error(String message) { runOnUiThread(() ->
                    toast("No se canceló: " + message)); }
        });
    }

    private void printReservation(Product product) {
        double balance = Math.max(0, product.cashPrice - product.reservationDeposit);
        String details = product.name + "\nIMEI: " + product.imei + "\nSeña: "
                + money(product.reservationDeposit) + "\nSaldo: " + money(balance);
        printDirect(ticketBytes(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                .format(new Date()), selectedSeller, "RESERVA", details, product.cashPrice));
    }

    private void showProductEditor(Product current) {
        showProductEditor(current, current == null || current.type == null || current.type.isBlank()
                ? "Accesorio" : current.type);
    }

    private void showProductEditor(Product current, String selectedType) {
        if (!api.configured()) { toast("Falta configurar Google Sheets"); return; }
        for (int i = 0; i < 3; i++) { pendingProductPhotos[i] = null; pendingPhotoPreviews[i] = null; }
        final boolean equipment = "Equipo".equalsIgnoreCase(selectedType);
        final boolean replacement = "Repuesto".equalsIgnoreCase(selectedType);
        LinearLayout form = column();
        form.setPadding(dp(18), dp(4), dp(18), dp(8));
        form.addView(label("* Obligatorio · Foto principal y datos del producto", 13, LIME, true));
        LinearLayout extraPhotos = column();
        int photoCount = 3;
        for (int slot = 0; slot < photoCount; slot++) {
            final int selectedSlot = slot;
            ImageView preview = new ImageView(this);
            pendingPhotoPreviews[slot] = preview;
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            String existing = current == null ? "" : (slot == 0 ? photoMap.urlFor(current)
                    : "celfii-photo://" + current.id + "?slot=" + (slot + 1));
            if (current != null && !(slot > 0 && (slot == 1 ? current.photo2 : current.photo3).isBlank()))
                loadProductPhoto(preview, existing, current.id, slot + 1);
            else preview.setImageResource(R.drawable.logo_celfii_app);
            (slot == 0 ? form : extraPhotos).addView(preview, new LinearLayout.LayoutParams(-1, dp(slot == 0 ? 190 : 120)));
            Button photo = actionButton((slot == 0 ? "FOTO PRINCIPAL *" : "FOTO " + (slot + 1) + " · OPCIONAL"), false);
            photo.setOnClickListener(v -> chooseProductPhoto(selectedSlot));
            (slot == 0 ? form : extraPhotos).addView(photo, new LinearLayout.LayoutParams(-1, dp(46)));
        }
        EditText name = editorInput(equipment ? "Modelo *" : "Nombre *",
                current == null ? "" : current.name, false);
        EditText category = editorInput("Categoría", current == null ? "" : current.category, false);
        EditText cash = editorInput("Precio efectivo/transferencia *",
                current == null ? "" : plainNumber(current.cashPrice), true);
        EditText card = editorInput("Precio Posnet", current == null ? "" : plainNumber(current.creditPrice), true);
        EditText stock = editorInput("Stock inicial *", current == null ? (equipment ? "1" : "0") : String.valueOf(current.stock), true);
        stock.setEnabled(current == null);
        if (equipment) { stock.setText("1"); stock.setVisibility(View.GONE); card.setVisibility(View.GONE); category.setVisibility(View.GONE); }
        EditText code = editorInput("Código de barras", current == null ? "" : current.code, false);
        EditText backup = editorInput("Código alternativo", current == null ? "" : current.backupCode, false);
        EditText brand = editorInput("Marca", current == null ? "" : current.brand, false);
        EditText compatible = editorInput("Modelos compatibles", current == null ? "" : current.compatibleModels, false);
        EditText color = editorInput("Color", current == null ? "" : current.color, false);
        EditText supplier = editorInput("Proveedor", current == null ? "" : current.supplier, false);
        EditText quality = editorInput("Calidad", current == null ? "" : current.quality, false);
        EditText replacementWarranty = editorInput("Garantía del repuesto", current == null ? "" : current.warrantyInfo, false);
        EditText imei = editorInput("IMEI", current == null ? "" : current.imei, false);
        EditText memory = editorInput("Memoria", current == null ? "" : current.memory, false);
        EditText battery = editorInput("Estado de batería", current == null ? "" : current.battery, false);
        EditText observations = editorInput("Observaciones particulares", current == null ? "" : current.observations, false);
        EditText description = editorInput("Descripción para la web",
                current == null ? "" : current.description, false);
        EditText cost = editorInput("Costo", current == null ? "" : plainNumber(current.cost), true);
        Spinner condition = new Spinner(this);
        condition.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Elegir condición", "Nuevo", "Usado"}));
        if (current != null && "Usado".equalsIgnoreCase(current.condition)) condition.setSelection(2);
        else if (current != null && "Nuevo".equalsIgnoreCase(current.condition)) condition.setSelection(1);
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
        Spinner grade = new Spinner(this);
        String[] grades = {"Elegir estado estético", "A+ · Como nuevo", "A · Excelente", "B · Muy bueno", "C · Con detalles"};
        grade.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, grades));
        if (current != null) for (int i=1;i<grades.length;i++)
            if (grades[i].split(" · ")[0].equals(current.aestheticGrade())) grade.setSelection(i);
        TextView gradeLabel = label("Estado estético *", 18, LIME, true);
        name.setTextSize(22); memory.setTextSize(22); cash.setTextSize(24);
        category.setHint("Categoría (opcional)"); description.setHint("Descripción (opcional)");
        form.addView(name); form.addView(cash);
        LinearLayout optional = column(); optional.setVisibility(View.GONE);
        optional.addView(extraPhotos);
        if (equipment) {
            memory.setHint("Memoria / capacidad *"); form.addView(memory);
            form.addView(label("Condición *", 18, MUTED, true)); form.addView(condition);
            form.addView(gradeLabel); form.addView(grade);
            battery.setHint("Salud de batería % · iPhone usado"); form.addView(battery);
            optional.addView(brand); optional.addView(color); optional.addView(imei);
            optional.addView(observations); optional.addView(supplier);
            Runnable updateFields = () -> {
                boolean used = "Usado".equals(String.valueOf(condition.getSelectedItem()));
                gradeLabel.setVisibility(used ? View.VISIBLE : View.GONE);
                grade.setVisibility(used ? View.VISIBLE : View.GONE);
                battery.setVisibility(used && name.getText().toString().toLowerCase(Locale.ROOT).contains("iphone") ? View.VISIBLE : View.GONE);
            };
            condition.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { updateFields.run(); }
                public void onNothingSelected(android.widget.AdapterView<?> p) { }
            });
            name.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence t,int a,int c,int f) { }
                public void onTextChanged(CharSequence t,int a,int b,int c) { updateFields.run(); }
                public void afterTextChanged(Editable e) { }
            });
            updateFields.run();
        } else {
            form.addView(stock);
            if (replacement) { compatible.setHint("Modelos compatibles *"); form.addView(compatible); optional.addView(quality); optional.addView(replacementWarranty); }
            else optional.addView(compatible);
            optional.addView(category); optional.addView(brand); optional.addView(color);
            optional.addView(supplier); optional.addView(codeRow); optional.addView(backup);
        }
        optional.addView(description); card.setVisibility(View.VISIBLE);
        optional.addView(card); optional.addView(cost);
        Button optionalToggle = actionButton("MÁS CARACTERÍSTICAS +", false);
        optionalToggle.setOnClickListener(v -> {
            boolean open = optional.getVisibility() == View.GONE;
            optional.setVisibility(open ? View.VISIBLE : View.GONE);
            optionalToggle.setText(open ? "MENOS CARACTERÍSTICAS −" : "MÁS CARACTERÍSTICAS +");
        });
        form.addView(optionalToggle); form.addView(optional);
        final Button deleteProduct = current == null ? null : actionButton("ELIMINAR PRODUCTO", false);
        if (deleteProduct != null) {
            deleteProduct.setTextColor(RED);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(52));
            deleteParams.topMargin = dp(18);
            form.addView(deleteProduct, deleteParams);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((current == null ? "Alta · " : "Editar · ") + selectedType)
                .setView(scroll).setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (name.getText().toString().trim().isEmpty()) {
                        toast("Ingresá el nombre del producto"); return;
                    }
                    if (pendingProductPhotos[0] == null && (current == null ||
                            ((current.photo == null || current.photo.trim().isEmpty()) &&
                             (current.photoUrl == null || current.photoUrl.trim().isEmpty())))) {
                        toast("Agregá la foto principal del producto"); return;
                    }
                    EditText[] requiredFields = equipment ? new EditText[]{memory}
                            : replacement ? new EditText[]{compatible}
                            : new EditText[]{};
                    for (EditText field : requiredFields) if (field.getText().toString().trim().isEmpty()) {
                        field.setError("Obligatorio"); field.requestFocus(); toast("Completá " + field.getHint()); return;
                    }
                    if (equipment && "Usado".equals(String.valueOf(condition.getSelectedItem())) &&
                            (name.getText().toString().toLowerCase(Locale.ROOT).contains("iphone"))) {
                        String batteryValue = battery.getText().toString().trim().replace("%", "");
                        try { int percent = Integer.parseInt(batteryValue);
                            if (percent < 1 || percent > 100) throw new NumberFormatException();
                        } catch (NumberFormatException error) { battery.setError("Ingresá un porcentaje entre 1 y 100"); battery.requestFocus(); return; }
                    }
                    if (decimal(cash) <= 0) { toast("Ingresá el precio"); return; }
                    if (!equipment && current == null && (stock.getText().toString().trim().isEmpty() || decimal(stock) < 0 || decimal(stock) != integer(stock))) { toast("Ingresá un stock entero, cero o mayor"); return; }
                    if (equipment && condition.getSelectedItemPosition() == 0) { toast("Elegí Nuevo o Usado"); return; }
                    boolean usedEquipment = equipment && "Usado".equals(String.valueOf(condition.getSelectedItem()));
                    if (usedEquipment && grade.getSelectedItemPosition() == 0) { toast("Elegí A+, A, B o C"); return; }
                    String selectedGrade = usedEquipment ? grades[grade.getSelectedItemPosition()].split(" · ")[0] : "";
                    String savedObservations = observations.getText().toString().trim();
                    if (equipment) {
                        savedObservations = savedObservations.replaceAll("(?m)^Estado estético: (?:A\\+|A|B|C)\\s*", "").trim();
                        if (usedEquipment) savedObservations = "Estado estético: " + selectedGrade + (savedObservations.isEmpty() ? "" : "\n" + savedObservations);
                    }
                    Product value = new Product(current == null ? "" : current.id,
                            name.getText().toString().trim(), equipment ? "Equipos" : (category.getText().toString().trim().isEmpty() ? "Otros" : category.getText().toString().trim()),
                            decimal(cash), decimal(card), current == null ? integer(stock) : current.stock,
                            current == null ? "" : current.photo, current == null ? "" : current.photo2,
                            current == null ? "" : current.photo3, "", code.getText().toString().trim(),
                            backup.getText().toString().trim(), selectedType, description.getText().toString().trim(),
                            current == null ? 0 : current.minimumStock, brand.getText().toString().trim(),
                            compatible.getText().toString().trim(), color.getText().toString().trim(),
                            supplier.getText().toString().trim(), equipment ? selectedGrade : quality.getText().toString().trim(),
                            replacementWarranty.getText().toString().trim(), imei.getText().toString().trim(),
                            memory.getText().toString().trim(), String.valueOf(condition.getSelectedItem()),
                            battery.getText().toString().trim(), savedObservations,
                            current == null ? "Disponible" : current.equipmentStatus,
                            current == null ? "" : current.reservationCustomer,
                            current == null ? "" : current.reservationPhone,
                            current == null ? 0 : current.reservationDeposit,
                            current == null ? "" : current.reservationDate,
                            current == null ? "" : current.reservationExpiry, decimal(cost));
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    saveProduct(value, equipment ? 1 : integer(stock), current == null, dialog);
                }));
        if (deleteProduct != null) {
            deleteProduct.setOnClickListener(v -> showDeleteProduct(current, dialog));
        }
        dialog.show();
    }

    private void showDeleteProduct(Product product, AlertDialog editor) {
        AlertDialog confirmation = new AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Querés eliminar definitivamente “" + product.name
                        + "”?\n\nSe quitará de la APK y de la página web. Las ventas anteriores no se modificarán.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("ELIMINAR DEFINITIVAMENTE", null)
                .create();
        confirmation.setOnShowListener(value -> {
            Button remove = confirmation.getButton(AlertDialog.BUTTON_POSITIVE);
            remove.setTextColor(RED);
            remove.setOnClickListener(view -> {
                remove.setEnabled(false);
                remove.setText("ELIMINANDO…");
                api.deleteProduct(product.id, "Eliminado desde APK",
                        new CelFiiApi.Callback<>() {
                            @Override public void success(String productId) {
                                runOnUiThread(() -> {
                                    confirmation.dismiss();
                                    if (editor != null) editor.dismiss();
                                    catalog.removeIf(item -> item.id.equals(productId));
                                    toast("Producto eliminado correctamente");
                                    showProducts();
                                    synchronize(false);
                                });
                            }
                            @Override public void error(String message) {
                                runOnUiThread(() -> {
                                    remove.setEnabled(true);
                                    remove.setText("ELIMINAR DEFINITIVAMENTE");
                                    toast("No se pudo eliminar: " + message);
                                });
                            }
                        });
            });
        });
        confirmation.show();
    }

    private void saveProduct(Product value, int initialStock, boolean creating, AlertDialog dialog) {
        toast("Guardando producto…");
        api.saveProduct(value, initialStock, creating, new CelFiiApi.Callback<>() {
            @Override public void success(String productId) {
                uploadPendingPhotos(productId, 0, dialog);
            }
            @Override public void error(String message) {
                runOnUiThread(() -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("No se guardó: " + message);
                });
            }
        });
    }

    private void uploadPendingPhotos(String productId, int slot, AlertDialog dialog) {
        if (slot >= pendingProductPhotos.length) { runOnUiThread(() -> productSaved(dialog)); return; }
        Bitmap bitmap = pendingProductPhotos[slot];
        if (bitmap == null) { uploadPendingPhotos(productId, slot + 1, dialog); return; }
        api.uploadPhoto(productId, bitmap, slot + 1, new CelFiiApi.Callback<>() {
            @Override public void success(String path) { uploadPendingPhotos(productId, slot + 1, dialog); }
            @Override public void error(String message) { runOnUiThread(() -> {
                toast("Producto guardado; falló la foto " + (slot + 1) + ": " + message);
                productSaved(dialog);
            }); }
        });
    }

    private void productSaved(AlertDialog dialog) {
        for (int i = 0; i < 3; i++) { pendingProductPhotos[i] = null; pendingPhotoPreviews[i] = null; }
        dialog.dismiss();
        toast("Producto guardado en Google Sheets");
        synchronize(true);
    }

    private void chooseProductPhoto(int slot) {
        pendingPhotoSlot = slot;
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
                pendingProductPhotos[pendingPhotoSlot] = Bitmap.createScaledBitmap(original,
                        Math.round(original.getWidth() * scale),
                        Math.round(original.getHeight() * scale), true);
            } else pendingProductPhotos[pendingPhotoSlot] = original;
            if (pendingPhotoPreviews[pendingPhotoSlot] != null)
                pendingPhotoPreviews[pendingPhotoSlot].setImageBitmap(pendingProductPhotos[pendingPhotoSlot]);
        } catch (Exception error) { toast("No se pudo abrir la foto"); }
    }

    private void loadProductPhoto(ImageView view, String url, String productId, int slot) {
        if (slot == 1) { imageLoader.load(view, url, api); return; }
        api.productPhoto(productId, slot, new CelFiiApi.Callback<>() {
            @Override public void success(Bitmap bitmap) { view.post(() -> view.setImageBitmap(bitmap)); }
            @Override public void error(String message) { }
        });
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
            LinearLayout card;
            ImageView image;
            TextView name, subtitleView, priceView, action;
            if (recycled instanceof LinearLayout && recycled.getTag() instanceof Object[]) {
                card = (LinearLayout) recycled;
                Object[] holder = (Object[]) card.getTag();
                image = (ImageView) holder[0]; name = (TextView) holder[1];
                subtitleView = (TextView) holder[2]; priceView = (TextView) holder[3];
                action = (TextView) holder[4];
            } else {
                card = panel();
                image = new ImageView(MainActivity.this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(image, new LinearLayout.LayoutParams(dp(58), dp(58)));
                LinearLayout copy = column(); copy.setPadding(dp(10), 0, dp(5), 0);
                name = label("", 14, WHITE, true); name.setMaxLines(2);
                subtitleView = label("", 11, MUTED, false); subtitleView.setMaxLines(2);
                priceView = label("", 16, LIME, true);
                copy.addView(name); copy.addView(subtitleView); copy.addView(priceView);
                card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
                action = label("", 11, LIME, true);
                card.addView(action, new LinearLayout.LayoutParams(dp(42), dp(42)));
                card.setTag(new Object[]{image, name, subtitleView, priceView, action});
            }
            String url = photoMap.urlFor(product);
            if (!java.util.Objects.equals(url, image.getTag()) || image.getDrawable() == null)
                imageLoader.load(image, url, api);
            name.setText(product.name);
            subtitleView.setText(product.type + (product.isEquipment()
                    ? " · " + product.memory + " · " + product.color + " · " + product.equipmentStatus
                    : " · " + product.category + " · Stock " + product.stock));
            priceView.setText(money(product.cashPrice));
            action.setText(productsTab ? "VER" : "+"); action.setTextSize(productsTab ? 11 : 25);
            card.setAlpha(product.stock <= 0 ? .55f : 1f);
            return card;
        }
    }

    static final class CartLine {
        final Product product;
        int quantity = 1;
        double unitPrice;
        CartLine(Product product) { this.product = product; this.unitPrice = product.cashPrice; }
        double total() { return quantity * unitPrice; }
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
        button.setBackground(rounded(primary ? LIME : PANEL, LIME, primary ? 0 : 1, 12));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (strokeWidth > 0) shape.setStroke(dp(strokeWidth), stroke);
        return shape;
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
