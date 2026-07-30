package com.celfii.pos;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ApiClient {
    private static final String PREFS = "celfii_server";
    private static final String PUBLIC_PRODUCTS_URL =
            "https://docs.google.com/spreadsheets/d/"
            + "1Fpow8nljHN21D2wBsi7r6IZf5WS1O7RQTUHzGGOXRZ4"
            + "/gviz/tq?tqx=out:csv&sheet=Articulos";
    private final SharedPreferences preferences;
    private volatile List<CachedProduct> publicCatalog;

    ApiClient(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    boolean isConfigured() {
        return !apiUrl().isBlank() && !apiToken().isBlank();
    }

    void configure(String url, String token) {
        preferences.edit()
                .putString("url", url.trim())
                .putString("token", token.trim())
                .apply();
    }

    String configuredUrl() {
        return apiUrl();
    }

    void loadProducts(String query, Callback<List<Models.Product>> callback) {
        if (!isConfigured()) {
            loadPublicProducts(query, callback);
            return;
        }
        new Thread(() -> {
            try {
                String url = apiUrl()
                        + "?action=products&token=" + encoded(apiToken())
                        + "&q=" + encoded(query == null ? "" : query);
                JSONObject response = request("GET", url, null);
                JSONArray rows = response.getJSONArray("products");
                List<Models.Product> products = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    products.add(new Models.Product(
                            row.optString("id"), row.optString("name"),
                            row.optString("category"), row.optDouble("cashPrice"),
                            row.optDouble("cardPrice"), row.optInt("stock"),
                            row.optString("photo")
                    ));
                }
                callback.onSuccess(products);
            } catch (Exception e) {
                loadPublicProducts(query, callback);
            }
        }).start();
    }

    private void loadPublicProducts(String query, Callback<List<Models.Product>> callback) {
        new Thread(() -> {
            try {
                List<CachedProduct> catalog = publicCatalog;
                if (catalog == null) {
                    catalog = downloadPublicCatalog();
                    publicCatalog = catalog;
                }
                String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
                List<Models.Product> products = new ArrayList<>();
                for (CachedProduct item : catalog) {
                    if (needle.isEmpty() || item.search.contains(needle)) {
                        products.add(item.product);
                    }
                }
                callback.onSuccess(products);
            } catch (Exception e) {
                callback.onError("No se pudo sincronizar la pestaña Artículos");
            }
        }).start();
    }

    private List<CachedProduct> downloadPublicCatalog() throws Exception {
        List<List<String>> rows = parseCsv(requestText(PUBLIC_PRODUCTS_URL));
        List<CachedProduct> catalog = new ArrayList<>();
        if (rows.isEmpty()) return catalog;
        java.util.Map<String, Integer> headers = new java.util.HashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            headers.put(rows.get(0).get(i).trim(), i);
        }
        for (int i = 1; i < rows.size(); i++) {
                    List<String> row = rows.get(i);
                    String id = csvCell(row, headers, "idArticulos");
                    String name = csvCell(row, headers, "Nombre");
                    if (id.isBlank() || name.isBlank()) continue;
                    String searchable = (name + " "
                            + csvCell(row, headers, "Categoría") + " "
                            + csvCell(row, headers, "Codigo") + " "
                            + csvCell(row, headers, "Codigo_Backup"))
                            .toLowerCase(Locale.ROOT);
                    Models.Product product = new Models.Product(
                            id, name, csvCell(row, headers, "Categoría"),
                            csvNumber(csvCell(row, headers, "Precio Efectivo")),
                            csvNumber(csvCell(row, headers, "Precio en 3 Cuotas")),
                            (int) csvNumber(csvCell(row, headers, "Stock Actual")),
                            csvCell(row, headers, "Foto")
                    );
                    catalog.add(new CachedProduct(product, searchable));
        }
        return catalog;
    }

    private static final class CachedProduct {
        final Models.Product product;
        final String search;

        CachedProduct(Models.Product product, String search) {
            this.product = product;
            this.search = search;
        }
    }

    private static String csvCell(List<String> row,
                                  java.util.Map<String, Integer> headers,
                                  String name) {
        Integer index = headers.get(name);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private static double csvNumber(String value) {
        try {
            String normalized = value.replace("$", "").replace(" ", "");
            if (normalized.contains(",")) {
                normalized = normalized.replace(".", "").replace(",", ".");
            }
            return Double.parseDouble(normalized);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static List<List<String>> parseCsv(String source) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && i + 1 < source.length()
                        && source.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String requestText(String target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "text/csv");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Google Sheets respondió " + status);
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        }
        return text.toString();
    }

    void createSale(Models.Sale sale, Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", "createSale");
                body.put("token", apiToken());
                body.put("clientRequestId", sale.id);
                body.put("customerId", sale.customerId);
                body.put("customerName", sale.customerName);
                body.put("seller", sale.seller);

                JSONArray lines = new JSONArray();
                for (Models.CartLine line : sale.lines) {
                    lines.put(new JSONObject()
                            .put("productId", line.product.id)
                            .put("quantity", line.quantity)
                            .put("unitPrice", line.unitPrice));
                }
                body.put("lines", lines);

                JSONArray payments = new JSONArray();
                for (Models.Payment payment : sale.payments) {
                    payments.put(new JSONObject()
                            .put("method", payment.method)
                            .put("amount", payment.amount)
                            .put("installments", payment.installments));
                }
                body.put("payments", payments);

                JSONObject response = request("POST", apiUrl(), body);
                callback.onSuccess(response.getString("saleId"));
            } catch (Exception e) {
                callback.onError(e.getMessage() == null ? "No se pudo registrar la venta" : e.getMessage());
            }
        }).start();
    }

    private JSONObject request(String method, String target, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        JSONObject response = new JSONObject(text.toString());
        if (status < 200 || status >= 300 || !response.optBoolean("ok", false)) {
            throw new IllegalStateException(response.optString("error", "Error del servidor"));
        }
        return response;
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String apiUrl() {
        return preferences.getString("url", BuildConfig.CELFII_API_URL);
    }

    private String apiToken() {
        return preferences.getString("token", BuildConfig.CELFII_API_TOKEN);
    }
}
