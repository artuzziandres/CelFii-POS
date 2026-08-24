package com.celfii.ventas;

final class Order {
    final String ticket, date, status, customerName, customerPhone, delivery, address,
            details, saleId;
    final double total;
    final boolean proofAttached;

    Order(String ticket, String date, String status, String customerName,
          String customerPhone, String delivery, String address, double total,
          boolean proofAttached, String details, String saleId) {
        this.ticket = ticket;
        this.date = date;
        this.status = status;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.delivery = delivery;
        this.address = address;
        this.total = total;
        this.proofAttached = proofAttached;
        this.details = details;
        this.saleId = saleId;
    }

    boolean isClosed() {
        return "Confirmado".equalsIgnoreCase(status) || "Cancelado".equalsIgnoreCase(status);
    }
}
