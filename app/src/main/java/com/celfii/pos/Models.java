package com.celfii.pos;

import java.util.ArrayList;
import java.util.List;

final class Models {
    static final class Product {
        final String id;
        final String name;
        final String category;
        final double cashPrice;
        final double cardPrice;
        final int stock;
        final String photo;

        Product(String id, String name, String category, double cashPrice,
                double cardPrice, int stock, String photo) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.cashPrice = cashPrice;
            this.cardPrice = cardPrice;
            this.stock = stock;
            this.photo = photo;
        }
    }

    static final class CartLine {
        final Product product;
        int quantity;
        double unitPrice;

        CartLine(Product product, int quantity, double unitPrice) {
            this.product = product;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        double total() { return quantity * unitPrice; }
    }

    static final class Payment {
        final String method;
        final double amount;
        final int installments;

        Payment(String method, double amount, int installments) {
            this.method = method;
            this.amount = amount;
            this.installments = installments;
        }
    }

    static final class Sale {
        String id;
        String date;
        String customerId = "";
        String customerName = "Consumidor final";
        String seller = "";
        final List<CartLine> lines = new ArrayList<>();
        final List<Payment> payments = new ArrayList<>();

        double total() {
            double value = 0;
            for (CartLine line : lines) value += line.total();
            return value;
        }

        double paid() {
            double value = 0;
            for (Payment payment : payments) value += payment.amount;
            return value;
        }
    }

    private Models() {}
}
