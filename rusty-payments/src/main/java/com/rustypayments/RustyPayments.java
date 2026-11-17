package com.rustypayments;

import com.rustypayments.legacy.LegacyPaymentCalendar;

/**
 * Front door of the payments wing. The tests only call {@link #openCalendar()}, so its
 * signature has to survive any refactoring. What it returns is fair game.
 */
public final class RustyPayments {

    private RustyPayments() {
    }

    public static PaymentCalendar openCalendar() {
        return new LegacyPaymentCalendar();
    }
}
