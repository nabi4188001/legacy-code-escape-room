package com.rustybank.legacy;

/**
 * ACCOUNT RECORD
 * CREATED 12.03.1999
 * CHG 04.11.2003 - balance changed from float to double, "should be enough now"
 */
public class Account {

    private String id;
    private double balance;

    public Account(String id) {
        this.id = id;
        this.balance = 0.0;
    }

    public String getId() {
        return id;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
