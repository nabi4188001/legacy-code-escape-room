package com.rustybank.legacy;

/**
 * SPLIT PAYMENTS (standing orders to several payees)
 */
public class PaymentSplitter {

    /** Share per payee, cut down to whole cents. */
    public double shareOf(double total, int payees) {
        return Math.floor(total / payees * 100) / 100;
    }
}
