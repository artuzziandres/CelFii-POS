package com.celfii.ventas;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    void catalogPhotos(Callback<JSONObject> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("GET", BuildConfig.CELFII_API_URL
                        + "?action=catalog&_=" + System.currentTimeMillis(), null);
                JSONArray rows = response.getJSONArray("products");
                JSONObject photos = new JSONObject();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String id = row.optString("id");
                    if (!id.isEmpty()) photos.put(id, row.optString("photo"));
                }
                callback.success(photos);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-catalog-photos").start();
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
                            row.optString("photo"), row.optString("photo2"), row.optString("photo3"),
                            row.optString("photoUrl"), row.optString("code"), row.optString("backupCode"),
                            row.optString("type", "Accesorio"), row.optString("description"),
                            row.optInt("minimumStock"), row.optString("brand"),
                            row.optString("compatibleModels"), row.optString("color"),
                            row.optString("supplier"), row.optString("quality"),
                            row.optString("warrantyInfo"), row.optString("imei"),
                            row.optString("memory"), row.optString("condition"),
                            row.optString("battery"), row.optString("observations"),
                            row.optString("equipmentStatus", "Disponible"),
                            row.optString("reservationCustomer"), row.optString("reservationPhone"),
                            row.optDouble("reservationDeposit"), row.optString("reservationDate"),
                            row.optString("reservationExpiry"), row.optDouble("cost")));
                }
                callback.success(result);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-products").start();
    }

    void createSale(String requestId, String seller, String payment, String customerName,
                    String customerPhone, int warrantyDays,
                    Iterable<MainActivity.CartLine> cart, Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONArray lines = new JSONArray();
                double total = 0;
                double priorDeposit = 0;
                for (MainActivity.CartLine line : cart) {
                    lines.put(new JSONObject().put("productId", line.product.id)
                            .put("quantity", line.quantity)
                            .put("originalPrice", line.product.cashPrice)
                            .put("unitPrice", line.unitPrice));
                    total += line.total();
                    if (line.product.isReserved()) priorDeposit += line.product.reservationDeposit;
                }
                JSONArray payments = new JSONArray();
                if (priorDeposit > 0) payments.put(new JSONObject()
                        .put("method", "Seña previa").put("amount", priorDeposit));
                payments.put(new JSONObject().put("method", payment)
                        .put("amount", Math.max(0, total - priorDeposit)).put("installments", 0));
                JSONObject body = base("createSale").put("clientRequestId", requestId)
                        .put("seller", seller).put("lines", lines)
                        .put("customerName", customerName).put("customerPhone", customerPhone)
                        .put("warrantyDays", warrantyDays).put("payments", payments);
                // Persist the exact payload before sending. An uncertain response must
                // reuse the same request ID, including after an app restart.
                body.remove("clientRequestId");
                String fingerprint = body.toString();
                synchronized (preferences) {
                    String previous = preferences.getString("pending_sale_payload", "");
                    String stableId = previous.equals(fingerprint)
                            ? preferences.getString("pending_sale_id", requestId) : requestId;
                    if (!preferences.edit().putString("pending_sale_payload", fingerprint)
                            .putString("pending_sale_id", stableId).commit()) {
                        throw new IllegalStateException("No se pudo proteger el reintento de la venta");
                    }
                    body.put("clientRequestId", stableId);
                }
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL, body);
                String saleId = response.getString("saleId");
                preferences.edit().remove("pending_sale_payload").remove("pending_sale_id").commit();
                callback.success(saleId);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-sale").start();
    }

    void sales(Callback<List<SaleStore.Entry>> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("GET", BuildConfig.CELFII_API_URL
                        + "?action=sales&token=" + encoded(token()), null);
                JSONArray rows = response.getJSONArray("sales");
                List<SaleStore.Entry> result = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    JSONArray lines = row.optJSONArray("details");
                    StringBuilder details = new StringBuilder();
                    int items = 0;
                    if (lines != null) for (int lineIndex = 0;
                                             lineIndex < lines.length(); lineIndex++) {
                        JSONObject line = lines.getJSONObject(lineIndex);
                        int quantity = line.optInt("quantity");
                        items += quantity;
                        if (details.length() > 0) details.append("\n\n");
                        details.append(line.optString("name"))
                                .append("\n").append(quantity).append(" × ")
                                .append(amount(line.optDouble("unitPrice")))
                                .append(" = ").append(amount(line.optDouble("total")));
                        double original = line.optDouble("originalPrice", line.optDouble("unitPrice"));
                        double difference = line.optDouble("difference");
                        if (Math.abs(difference) > .01) details.append("\nOriginal: ")
                                .append(amount(original)).append(" · Diferencia: ")
                                .append(amount(difference));
                    }
                    result.add(new SaleStore.Entry(row.optString("id"),
                            row.optString("date"), row.optDouble("total"),
                            row.optString("payment"), items, details.toString(),
                            row.optString("seller", "Sin asignar"),
                            row.optString("month", "Sin fecha"), row.optLong("timestamp")));
                }
                callback.success(result);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-sales").start();
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
                value.put("brand", product.brand).put("compatibleModels", product.compatibleModels)
                        .put("color", product.color).put("supplier", product.supplier)
                        .put("quality", product.quality).put("warrantyInfo", product.warrantyInfo)
                        .put("imei", product.imei).put("memory", product.memory)
                        .put("condition", product.condition).put("battery", product.battery)
                        .put("observations", product.observations)
                        .put("equipmentStatus", product.equipmentStatus)
                        .put("reservationCustomer", product.reservationCustomer)
                        .put("reservationPhone", product.reservationPhone)
                        .put("reservationDeposit", product.reservationDeposit)
                        .put("reservationDate", product.reservationDate)
                        .put("reservationExpiry", product.reservationExpiry)
                        .put("cost", product.cost);
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL,
                        base(creating ? "createProduct" : "updateProduct").put("product", value));
                callback.success(response.getString("productId"));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-product-save").start();
    }

    void deleteProduct(String productId, String reason, Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL,
                        base("deleteProduct").put("productId", productId).put("reason", reason));
                callback.success(response.getString("productId"));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-product-delete").start();
    }

    void orders(Callback<List<Order>> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("GET", BuildConfig.CELFII_API_URL
                        + "?action=orders&token=" + encoded(token()), null);
                JSONArray rows = response.getJSONArray("orders");
                List<Order> result = new ArrayList<>();
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    JSONArray lines = row.optJSONArray("lines");
                    StringBuilder details = new StringBuilder();
                    if (lines != null) for (int x = 0; x < lines.length(); x++) {
                        JSONObject line = lines.getJSONObject(x);
                        if (details.length() > 0) details.append("\n");
                        details.append(line.optInt("quantity")).append(" × ")
                                .append(line.optString("name"));
                    }
                    result.add(new Order(row.optString("ticket"), row.optString("date"),
                            row.optString("status"), row.optString("customerName"),
                            row.optString("customerPhone"), row.optString("delivery"),
                            row.optString("address"), row.optDouble("total"),
                            row.optBoolean("proofAttached"), details.toString(),
                            row.optString("saleId")));
                }
                callback.success(result);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-orders").start();
    }

    void confirmOrder(String ticket, String seller, Callback<String> callback) {
        changeOrder("confirmOrder", ticket, seller, callback);
    }

    void cancelOrder(String ticket, Callback<String> callback) {
        changeOrder("cancelOrder", ticket, "", callback);
    }

    private void changeOrder(String action, String ticket, String seller,
                             Callback<String> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL,
                        base(action).put("ticket", ticket).put("seller", seller));
                callback.success(response.optString("saleId", ticket));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-order-change").start();
    }

    void orderProof(String ticket, Callback<Bitmap> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("GET", BuildConfig.CELFII_API_URL
                        + "?action=orderProof&ticket=" + encoded(ticket)
                        + "&token=" + encoded(token()), null);
                byte[] bytes = Base64.decode(response.getString("base64"), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) throw new IllegalStateException("Comprobante inválido");
                callback.success(bitmap);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-order-proof").start();
    }

    void uploadPhoto(String productId, Bitmap bitmap, Callback<String> callback) {
        uploadPhoto(productId, bitmap, 1, callback);
    }

    void uploadPhoto(String productId, Bitmap bitmap, int slot, Callback<String> callback) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, bytes);
                JSONObject body = base("uploadProductPhoto").put("productId", productId)
                        .put("slot", slot)
                        .put("mimeType", "image/jpeg")
                        .put("base64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
                JSONObject response = request("POST", BuildConfig.CELFII_API_URL, body);
                callback.success(response.getString("path"));
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-photo").start();
    }

    void productPhoto(String productId, Callback<Bitmap> callback) {
        productPhoto(productId, 1, callback);
    }

    void productPhoto(String productId, int slot, Callback<Bitmap> callback) {
        new Thread(() -> {
            try {
                JSONObject response = request("GET", BuildConfig.CELFII_API_URL
                        + "?action=productPhoto&productId=" + encoded(productId)
                        + "&slot=" + slot
                        + "&token=" + encoded(token()), null);
                byte[] bytes = Base64.decode(response.getString("base64"), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) throw new IllegalStateException("La foto no es válida");
                callback.success(bitmap);
            } catch (Exception error) { callback.error(message(error)); }
        }, "celfii-product-photo").start();
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

    private static String amount(double value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"))
                .format(value).replace(",00", "");
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "No se pudo conectar con Google Sheets" : error.getMessage();
    }
}
