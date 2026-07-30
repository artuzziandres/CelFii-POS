package com.celfii.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class PosDatabase extends SQLiteOpenHelper {
    PosDatabase(Context context) {
        super(context, "celfii_ventas.db", null, 2);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (" +
                "id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT, cash REAL, card REAL," +
                "stock INTEGER, photo TEXT, code TEXT, backup TEXT, search TEXT)");
        db.execSQL("CREATE INDEX product_search ON products(search)");
        db.execSQL("CREATE TABLE sales (" +
                "id TEXT PRIMARY KEY, created TEXT, total REAL, seller TEXT, customer TEXT," +
                "payments TEXT, lines TEXT, sync_status TEXT)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS products");
        db.execSQL("DROP TABLE IF EXISTS sales");
        onCreate(db);
    }

    void replaceProducts(List<Models.Product> products) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("products", null, null);
            for (Models.Product product : products) {
                ContentValues value = new ContentValues();
                value.put("id", product.id);
                value.put("name", product.name);
                value.put("category", product.category);
                value.put("cash", product.cashPrice);
                value.put("card", product.cardPrice);
                value.put("stock", product.stock);
                value.put("photo", product.photo);
                value.put("code", product.code);
                value.put("backup", product.backupCode);
                value.put("search", (product.id + " " + product.name + " " + product.category
                        + " " + product.code + " " + product.backupCode).toLowerCase());
                db.insertWithOnConflict("products", null, value, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Models.Product> products(String query, boolean includeOutOfStock, int limit) {
        List<Models.Product> result = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase();
        String where = "";
        List<String> args = new ArrayList<>();
        if (!needle.isEmpty()) {
            where = "search LIKE ?";
            args.add("%" + needle + "%");
        }
        if (!includeOutOfStock && needle.isEmpty()) {
            where = "stock > 0";
        }
        try (Cursor cursor = getReadableDatabase().query(
                "products", null, where.isEmpty() ? null : where,
                args.toArray(new String[0]), null, null,
                "CASE WHEN stock > 0 THEN 0 ELSE 1 END, name COLLATE NOCASE", String.valueOf(limit))) {
            while (cursor.moveToNext()) result.add(product(cursor));
        }
        return result;
    }

    int productCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM products", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    void saveSale(Models.Sale sale, String status) {
        try {
            JSONArray lines = new JSONArray();
            for (Models.CartLine line : sale.lines) {
                lines.put(new JSONObject()
                        .put("id", line.product.id).put("name", line.product.name)
                        .put("quantity", line.quantity).put("price", line.unitPrice));
            }
            JSONArray payments = new JSONArray();
            for (Models.Payment payment : sale.payments) {
                payments.put(new JSONObject().put("method", payment.method)
                        .put("amount", payment.amount).put("installments", payment.installments));
            }
            ContentValues value = new ContentValues();
            value.put("id", sale.id);
            value.put("created", sale.date);
            value.put("total", sale.total());
            value.put("seller", sale.seller);
            value.put("customer", sale.customerName);
            value.put("payments", payments.toString());
            value.put("lines", lines.toString());
            value.put("sync_status", status);
            getWritableDatabase().insertWithOnConflict(
                    "sales", null, value, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) {}
    }

    void markSynced(String id) {
        ContentValues value = new ContentValues();
        value.put("sync_status", "Sincronizada");
        getWritableDatabase().update("sales", value, "id=?", new String[]{id});
    }

    List<SaleSummary> recentSales() {
        List<SaleSummary> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "sales", new String[]{"id", "created", "total", "payments", "sync_status"},
                null, null, null, null, "rowid DESC", "100")) {
            while (cursor.moveToNext()) {
                result.add(new SaleSummary(cursor.getString(0), cursor.getString(1),
                        cursor.getDouble(2), cursor.getString(3), cursor.getString(4)));
            }
        }
        return result;
    }

    private Models.Product product(Cursor cursor) {
        return new Models.Product(
                cursor.getString(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("cash")),
                cursor.getDouble(cursor.getColumnIndexOrThrow("card")),
                cursor.getInt(cursor.getColumnIndexOrThrow("stock")),
                cursor.getString(cursor.getColumnIndexOrThrow("photo")),
                cursor.getString(cursor.getColumnIndexOrThrow("code")),
                cursor.getString(cursor.getColumnIndexOrThrow("backup")));
    }

    static final class SaleSummary {
        final String id, created, payments, status;
        final double total;
        SaleSummary(String id, String created, double total, String payments, String status) {
            this.id = id; this.created = created; this.total = total;
            this.payments = payments; this.status = status;
        }
    }
}
