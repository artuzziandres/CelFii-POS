package com.celfii.ventas;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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

final class CelFiiApi {
    private static final String SETTINGS = "celfii_connection";
    private static final String TOKEN = "api_token";
    private final SharedPreferences preferences;

    interface Callback<T> {
        void success(T value);
        void error(String message);
    }

    CelFiiApi(Context context) {
        preferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
    }

    boolean configured() {
        return !BuildConfig.CELFII_API_URL.isBlank() && !token().isBlank();
    }

    void configureToken(String candidate, Callback<Boolean> callback) {
        new Thread(() -> {
            try {
                request("GET", BuildConfig.CELFII_API_URL
                        + "?action=health&token=" + encoded(candidate), null);
                preferences.edit().putString(TOKEN, candidate).apply();
                callback.success(true);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-token").start();
    }

    void products(Callback<List<Product>> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("GET", BuildConfig.CELFII_API_URL
                        + "?action=products&token=" + encoded(token()), null);
                JSONArray rows = response.getJSONArray("products");
                List<Product> result = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    result.add(new Product(row.optString("id"), row.optString("name"),
                            row.optString("category"), row.optDouble("cashPrice"),
                            row.optDouble("cardPrice"), row.optInt("stock"),
                            row.optString("photo"), row.optString("code"),
                            row.optString("backupCode"), row.optString("type", "Accesorio"),
                            row.optString("description"), row.optInt("minimumStock")));
                }
                callback.success(result);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-products").start();
    }

    void createSale(String requestId, String seller, String payment,
                    Iterable<MainActivity.CartLine> cart, Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONArray lines = new JSONArray();
                double total = 0;
                for (MainActivity.CartLine line : cart) {
                    lines.put(new JSONObject().put("productId", line.product.id)
                            .put("quantity", line.quantity)
                            .put("unitPrice", line.product.cashPrice));
                    total += line.total();
                }
                JSONObject body = base("createSale").put("clientRequestId", requestId)
                        .put("seller", seller).put("lines", lines)
                        .put("payments", new JSONArray().put(new JSONObject()
                                .put("method", payment).put("amount", total)
                                .put("installments", 0)));
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL, body);
                callback.success(response.getString("saleId"));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-sale").start();
    }

    void saveProduct(Product product, int initialStock, boolean creating,
                     Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONObject value = new JSONObject().put("id", product.id)
                        .put("name", product.name).put("category", product.category)
                        .put("cashPrice", product.cashPrice).put("cardPrice", product.creditPrice)
                        .put("code", product.code).put("backupCode", product.backupCode)
                        .put("type", product.type).put("description", product.description)
                        .put("minimumStock", product.minimumStock)
                        .put("initialStock", initialStock);
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL,
                        base(creating ? "createProduct" : "updateProduct").put("product", value));
                callback.success(response.getString("productId"));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-product-save").start();
    }

    void uploadPhoto(String productId, Bitmap bitmap, Callback<String> callback) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, bytes);
                JSONObject body = base("uploadProductPhoto").put("productId", productId)
                        .put("mimeType", "image/jpeg")
                        .put("base64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL, body);
                callback.success(response.getString("path"));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-photo").start();
    }

    private JSONObject base(String action) throws Exception {
        return new JSONObject().put("action", action).put("token", token());
    }

    private String token() {
        return preferences.getString(TOKEN, "").trim();
    }

    private static JSONObject request(String method, String target, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(45000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setInstanceFollowRedirects(true);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 400
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream,
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        } finally { connection.disconnect(); }
        JSONObject response = new JSONObject(text.toString());
        if (!response.optBoolean("ok", false)) {
            throw new IllegalStateException(response.optString("error", "Error de Google Sheets"));
        }
        return response;
    }

    private static String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "No se pudo conectar con Google Sheets" : error.getMessage();
    }
}
