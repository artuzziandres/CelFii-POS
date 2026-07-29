package com.celfii.pos;

import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.Locale;

final class ReceiptFormatter {
    private static final int WIDTH = 32;
    private static final NumberFormat MONEY =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"));

    static byte[] format(Models.Sale sale) {
        StringBuilder out = new StringBuilder();
        out.append(center("CEL-FII TECNOLOGIA")).append('\n');
        out.append(center("San Rafael, Mendoza")).append('\n');
        out.append(repeat('-')).append('\n');
        out.append("Venta: ").append(sale.id).append('\n');
        out.append("Fecha: ").append(sale.date).append('\n');
        out.append("Vendedor: ").append(sale.seller).append('\n');
        out.append("Cliente: ").append(sale.customerName).append('\n');
        out.append(repeat('-')).append('\n');

        for (Models.CartLine line : sale.lines) {
            out.append(wrap(line.product.name)).append('\n');
            String left = line.quantity + " x " + money(line.unitPrice);
            out.append(twoColumns(left, money(line.total()))).append('\n');
        }

        out.append(repeat('-')).append('\n');
        out.append(twoColumns("TOTAL", money(sale.total()))).append('\n');
        out.append("PAGO:\n");
        for (Models.Payment payment : sale.payments) {
            String name = payment.method;
            if ("Crédito".equals(payment.method) && payment.installments > 0) {
                name += " " + payment.installments + " cuotas";
            }
            out.append(twoColumns(name, money(payment.amount))).append('\n');
        }
        out.append(repeat('-')).append('\n');
        out.append(center("Gracias por elegir Cel-Fii")).append('\n');
        out.append(center("www.cel-fii.com")).append("\n\n\n");

        byte[] init = new byte[]{0x1B, 0x40};
        byte[] content = out.toString().getBytes(Charset.forName("CP850"));
        byte[] feed = new byte[]{0x1B, 0x64, 0x03};
        byte[] result = new byte[init.length + content.length + feed.length];
        System.arraycopy(init, 0, result, 0, init.length);
        System.arraycopy(content, 0, result, init.length, content.length);
        System.arraycopy(feed, 0, result, init.length + content.length, feed.length);
        return result;
    }

    private static String money(double value) {
        return MONEY.format(value).replace(",00", "");
    }

    private static String center(String value) {
        int spaces = Math.max(0, (WIDTH - value.length()) / 2);
        return " ".repeat(spaces) + value;
    }

    private static String repeat(char character) {
        return String.valueOf(character).repeat(WIDTH);
    }

    private static String twoColumns(String left, String right) {
        int room = WIDTH - right.length() - 1;
        String clipped = left.length() > room ? left.substring(0, room) : left;
        return clipped + " ".repeat(Math.max(1, WIDTH - clipped.length() - right.length())) + right;
    }

    private static String wrap(String text) {
        return text.length() <= WIDTH ? text : text.substring(0, WIDTH);
    }

    private ReceiptFormatter() {}
}
