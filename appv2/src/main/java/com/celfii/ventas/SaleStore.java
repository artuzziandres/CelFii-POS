package com.celfii.ventas;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SaleStore {
    private final SharedPreferences preferences;
    SaleStore(Context context) {
        preferences = context.getSharedPreferences("celfii_ventas_nueva", Context.MODE_PRIVATE);
    }

    void add(String id, String date, double total, String payment, int itemCount,
             String details, String seller) {
        try {
            JSONArray current = new JSONArray(preferences.getString("sales", "[]"));
            JSONArray next = new JSONArray();
            next.put(new JSONObject().put("id", id).put("date", date).put("total", total)
                    .put("payment", payment).put("items", itemCount)
                    .put("details", details).put("seller", seller));
            for (int i = 0; i < current.length() && i < 99; i++) next.put(current.get(i));
            preferences.edit().putString("sales", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    List<Entry> all() {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(preferences.getString("sales", "[]"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                result.add(new Entry(row.optString("id"), row.optString("date"),
                        row.optDouble("total"), row.optString("payment"), row.optInt("items"),
                        row.optString("details"), row.optString("seller", "Sin asignar")));
            }
        } catch (Exception ignored) {}
        return result;
    }

    static final class Entry {
        final String id, date, payment, details, seller;
        final double total;
        final int items;
        Entry(String id, String date, double total, String payment, int items,
              String details, String seller) {
            this.id = id; this.date = date; this.total = total;
            this.payment = payment; this.items = items; this.details = details;
            this.seller = seller;
        }
    }
}
