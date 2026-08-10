package com.celfii.ventas;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class Product {
    final String id, name, category, photo, photoUrl, code, backupCode, type, description;
    final double cashPrice, creditPrice;
    final int stock, minimumStock;

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String code, String backupCode) {
        this(id, name, category, cashPrice, creditPrice, stock, photo, "", code,
                backupCode, "Accesorio", "", 0);
    }

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String code, String backupCode,
            String type, String description, int minimumStock) {
        this(id, name, category, cashPrice, creditPrice, stock, photo, "", code,
                backupCode, type, description, minimumStock);
    }

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String photoUrl,
            String code, String backupCode, String type, String description, int minimumStock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.cashPrice = cashPrice;
        this.creditPrice = creditPrice;
        this.stock = stock;
        this.photo = photo;
        this.photoUrl = photoUrl;
        this.code = code;
        this.backupCode = backupCode;
        this.type = type;
        this.description = description;
        this.minimumStock = minimumStock;
    }

    String searchable() {
        return (id + " " + name + " " + category + " " + code + " " + backupCode).toLowerCase();
    }

    String imageUrl() {
        if (photo == null || photo.trim().isEmpty()) return "";
        return "https://www.appsheet.com/template/gettablefileurl"
                + "?appName=Cel-Fii1-645565216&tableName=Articulos&fileName="
                + URLEncoder.encode(photo, StandardCharsets.UTF_8);
    }
}
