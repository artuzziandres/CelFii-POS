package com.celfii.ventas;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class PhotoMap {
    private final JSONObject files;

    PhotoMap(Context context) {
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
        if (product.photoUrl != null && !product.photoUrl.trim().isEmpty()) {
            return product.photoUrl.trim();
        }
        if (product.photo == null || product.photo.trim().isEmpty()) return "";
        String name = product.photo;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        String fileId = files.optString(name, "");
        if (fileId.isEmpty() && product.id != null && name.startsWith(product.id)) {
            fileId = files.optString(name.substring(product.id.length()), "");
        }
        if (fileId.isEmpty()) return product.imageUrl();
        return "https://drive.google.com/thumbnail?id=" + fileId + "&sz=w300";
    }
}
