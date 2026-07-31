package com.celfii.ventas;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ImageLoader {
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(24 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount() / 1024;
        }
    };

    void load(ImageView view, String url) {
        view.setImageResource(R.drawable.logo_celfii);
        view.setTag(url);
        if (url == null || url.isEmpty()) return;
        Bitmap saved = cache.get(url);
        if (saved != null) {
            view.setImageBitmap(saved);
            return;
        }
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Cel-Fii-Ventas-Android");
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) return;
                Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                if (bitmap == null) return;
                cache.put(url, bitmap);
                view.post(() -> {
                    if (url.equals(view.getTag())) view.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
