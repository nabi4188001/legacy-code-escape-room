package com.rustybank.legacy;

/**
 * MONEY HELPERS
 * DO NOT TOUCH - used by everything
 */
public class MoneyUtil {

    public static double parse(String amount) {
        if (amount == null) {
            throw new IllegalArgumentException("INVALID AMOUNT");
        }
        double d = Double.parseDouble(amount.trim());
        if (d <= 0) {
            throw new IllegalArgumentException("INVALID AMOUNT: " + amount);
        }
        return d;
    }

    /** Prints an amount for the teller screen, 2 decimals. */
    public static String format(double amount) {
        long cents = Math.round(amount * 100);
        long whole = cents / 100;
        long frac = cents % 100;
        String s = whole + ".";
        if (frac < 10) {
            s = s + "0";
        }
        return s + frac;
    }
}
