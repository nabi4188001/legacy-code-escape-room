package com.rustybank.legacy;

/**
 * INTEREST RUN
 * CHG 17.01.2001 - rounding added after complaint from branch 0042
 */
public class InterestCalculator {

    public double monthlyInterest(double balance, double annualRatePercent) {
        double raw = balance * annualRatePercent / 100 / 12;
        return Math.round(raw * 100) / 100.0;
    }
}
