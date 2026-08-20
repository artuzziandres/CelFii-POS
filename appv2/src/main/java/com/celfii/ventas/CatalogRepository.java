package com.celfii.ventas;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CatalogRepository {
    private static final String PRODUCTS_URL =
            "https://docs.google.com/spreadsheets/d/"
            + "1Fpow8nljHN21D2wBsi7r6IZf5WS1O7RQTUHzGGOXRZ4"
            + "/gviz/tq?tqx=out:csv&sheet=Articulos";
    private final File cache;

    CatalogRepository(Context context) {
        cache = new File(context.getFilesDir(), "catalogo_articulos.csv");
    }

    interface Callback {
        void success(List<Product> products, boolean fromCache);
        void error(String message);
    }

    void load(Callback callback) {
        new Thread(() -> {
            if (cache.exists()) {
                try { callback.success(parse(read(cache)), true); }
                catch (Exception ignored) {}
            }
            try {
                String csv = download();
                write(csv);
                callback.success(parse(csv), false);
            } catch (Exception error) {
                if (!cache.exists()) callback.error("No se pudo descargar Articulos");
            }
        }, "catalog-sync").start();
    }

    private String download() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(PRODUCTS_URL).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "text/csv");
        if (connection.getResponseCode() != 200) {
            throw new IllegalStateException("Sheets respondió " + connection.getResponseCode());
        }
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        } finally { connection.disconnect(); }
        return text.toString();
    }

    private void write(String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(cache)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String read(File file) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        }
        return text.toString();
    }

    private List<Product> parse(String csv) {
        List<List<String>> rows = csvRows(csv);
        List<Product> result = new ArrayList<>();
        if (rows.isEmpty()) return result;
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) headers.put(rows.get(0).get(i).trim(), i);
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String id = cell(row, headers, "idArticulos");
            String name = cell(row, headers, "Nombre");
            if (id.isEmpty() || name.isEmpty()) continue;
            result.add(new Product(id, name, cell(row, headers, "Categoría"),
                    number(cell(row, headers, "Precio Efectivo")),
                    number(cell(row, headers, "Precio en 3 Cuotas")),
                    (int) number(cell(row, headers, "Stock Actual")),
                    cell(row, headers, "Foto"), cell(row, headers, "Codigo"),
                    cell(row, headers, "Codigo_Backup")));
        }
        return result;
    }

    private static String cell(List<String> row, Map<String, Integer> headers, String key) {
        Integer index = headers.get(key);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private static double number(String value) {
        try {
            String clean = value.replace("$", "").replace(" ", "");
            if (clean.contains(",")) clean = clean.replace(".", "").replace(",", ".");
            return Double.parseDouble(clean);
        } catch (Exception ignored) { return 0; }
    }

    private static List<List<String>> csvRows(String source) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                row.add(cell.toString()); cell.setLength(0);
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
                row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>();
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        return rows;
    }
}
