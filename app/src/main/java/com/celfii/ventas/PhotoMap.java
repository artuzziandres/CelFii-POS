package com.celfii.ventas;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class PhotoMap {
    private final JSONObject files;
    private final android.content.SharedPreferences preferences;
    private JSONObject published;
    private long revision = System.currentTimeMillis();

    PhotoMap(Context context) {
        preferences = context.getSharedPreferences("published_photos", Context.MODE_PRIVATE);
        try { published = new JSONObject(preferences.getString("photos", "{}")); }
        catch (Exception ignored) { published = new JSONObject(); }
        JSONObject loaded;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("photo_map.json"), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            loaded = new JSONObject(json.toString());
        } catch (Exception error) {
            loaded = new JSONObject();
        }
        files = loaded;
    }

    String urlFor(Product product) {
        if (published.has(product.id)) return webUrl(published.optString(product.id));
        if (product.photoUrl != null && !product.photoUrl.trim().isEmpty()) {
            return webUrl(product.photoUrl);
        }
        if (product.photo == null || product.photo.trim().isEmpty()) return "";
        if (product.photo.startsWith("https://")) return webUrl(product.photo);
        String name = product.photo;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        String fileId = files.optString(name, "");
        if (fileId.isEmpty() && product.id != null && name.startsWith(product.id)) {
            fileId = files.optString(name.substring(product.id.length()), "");
        }
        if (fileId.isEmpty()) return "celfii-photo://" + product.id;
        return webUrl("https://drive.google.com/uc?id=" + fileId);
    }

    void update(JSONObject photos) {
        published = photos;
        revision = System.currentTimeMillis();
        preferences.edit().putString("photos", photos.toString()).apply();
    }

    private String webUrl(String value) {
        String url = value == null ? "" : value.trim();
        java.util.regex.Matcher query = java.util.regex.Pattern.compile("[?&]id=([\\w-]+)").matcher(url);
        java.util.regex.Matcher path = java.util.regex.Pattern.compile("drive\\.google\\.com/file/d/([\\w-]+)").matcher(url);
        String id = query.find() ? query.group(1) : path.find() ? path.group(1) : "";
        if (!id.isEmpty()) return "https://lh3.googleusercontent.com/d/" + id + "=w1200?v=" + revision;
        return url;
    }
}
