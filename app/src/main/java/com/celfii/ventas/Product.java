package com.celfii.ventas;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class Product {
    final String id, name, category, photo, photo2, photo3, photoUrl, code, backupCode,
            type, description, brand, compatibleModels, color, supplier, quality,
            warrantyInfo, imei, memory, condition, battery, observations, equipmentStatus,
            reservationCustomer, reservationPhone, reservationDate, reservationExpiry;
    final double cashPrice, creditPrice, cost, reservationDeposit;
    final int stock, minimumStock;

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String code, String backupCode) {
        this(id, name, category, cashPrice, creditPrice, stock, photo, "", "", "", code,
                backupCode, "Accesorio", "", 0, "", "", "", "", "", "", "", "",
                "", "", "", "Disponible", "", "", 0, "", "", 0);
    }

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String code, String backupCode,
            String type, String description, int minimumStock) {
        this(id, name, category, cashPrice, creditPrice, stock, photo, "", "", "", code,
                backupCode, type, description, minimumStock, "", "", "", "", "", "", "",
                "", "", "", "", "Disponible", "", "", 0, "", "", 0);
    }

    Product(String id, String name, String category, double cashPrice,
            double creditPrice, int stock, String photo, String photoUrl,
            String code, String backupCode, String type, String description, int minimumStock) {
        this(id, name, category, cashPrice, creditPrice, stock, photo, "", "", photoUrl,
                code, backupCode, type, description, minimumStock, "", "", "", "", "", "",
                "", "", "", "", "", "Disponible", "", "", 0, "", "", 0);
    }

    Product(String id, String name, String category, double cashPrice, double creditPrice,
            int stock, String photo, String photo2, String photo3, String photoUrl,
            String code, String backupCode, String type, String description, int minimumStock,
            String brand, String compatibleModels, String color, String supplier, String quality,
            String warrantyInfo, String imei, String memory, String condition, String battery,
            String observations, String equipmentStatus, String reservationCustomer,
            String reservationPhone, double reservationDeposit, String reservationDate,
            String reservationExpiry, double cost) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.cashPrice = cashPrice;
        this.creditPrice = creditPrice;
        this.stock = stock;
        this.photo = photo;
        this.photo2 = photo2;
        this.photo3 = photo3;
        this.photoUrl = photoUrl;
        this.code = code;
        this.backupCode = backupCode;
        this.type = type;
        this.description = description;
        this.minimumStock = minimumStock;
        this.brand = brand;
        this.compatibleModels = compatibleModels;
        this.color = color;
        this.supplier = supplier;
        this.quality = quality;
        this.warrantyInfo = warrantyInfo;
        this.imei = imei;
        this.memory = memory;
        this.condition = condition;
        this.battery = battery;
        this.observations = observations;
        this.equipmentStatus = equipmentStatus == null || equipmentStatus.isBlank()
                ? "Disponible" : equipmentStatus;
        this.reservationCustomer = reservationCustomer;
        this.reservationPhone = reservationPhone;
        this.reservationDeposit = reservationDeposit;
        this.reservationDate = reservationDate;
        this.reservationExpiry = reservationExpiry;
        this.cost = cost;
    }

    String searchable() {
        return (id + " " + name + " " + category + " " + code + " " + backupCode + " "
                + type + " " + brand + " " + compatibleModels + " " + color + " " + imei
                + " " + memory).toLowerCase();
    }

    boolean isEquipment() { return "Equipo".equalsIgnoreCase(type); }
    boolean isSold() { return isEquipment() && "Vendido".equalsIgnoreCase(equipmentStatus); }
    boolean isReserved() { return isEquipment() && "Reservado".equalsIgnoreCase(equipmentStatus); }
    boolean isUnavailableEquipment() {
        return isEquipment() && (stock <= 0 || isSold());
    }

    Product withReservation(String status, String customer, String phone, double deposit,
                            String date, String expiry) {
        return new Product(id, name, category, cashPrice, creditPrice, stock, photo, photo2,
                photo3, photoUrl, code, backupCode, type, description, minimumStock, brand,
                compatibleModels, color, supplier, quality, warrantyInfo, imei, memory,
                condition, battery, observations, status, customer, phone, deposit, date,
                expiry, cost);
    }

    String imageUrl() {
        if (photo == null || photo.trim().isEmpty()) return "";
        return "https://www.appsheet.com/template/gettablefileurl"
                + "?appName=Cel-Fii1-645565216&tableName=Articulos&fileName="
                + URLEncoder.encode(photo, StandardCharsets.UTF_8);
    }
}
