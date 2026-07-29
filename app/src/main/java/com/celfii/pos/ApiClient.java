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

final class ApiClient {
    private static final String PREFS = "celfii_server";
    private final SharedPreferences preferences;

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
                callback.onError(e.getMessage() == null ? "No se pudo cargar el catálogo" : e.getMessage());
            }
        }).start();
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
