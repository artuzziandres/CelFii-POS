package com.celfii.ventas;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class Product {
    final String id, name, category, photo, code, backupCode;
    final double cashPrice, creditPrice;
    final int stock;

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String code, String backupCode) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.cashPrice = cashPrice;
        this.creditPrice = creditPrice;
        this.stock = stock;
        this.photo = photo;
        this.code = code;
        this.backupCode = backupCode;
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
