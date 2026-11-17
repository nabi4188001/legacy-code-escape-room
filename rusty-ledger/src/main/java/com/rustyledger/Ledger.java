package com.rustyledger;

import java.util.List;

/**
 * The transaction engine of Rusty Bank: accounts, instructions, fees, limits, currency
 * conversion, reversals, an audit log and a daily report.
 *
 * <p><b>LOCKED FILE.</b> This interface is the contract the room is tested through. An
 * escaping agent may rewrite anything behind it, but may not change this file.
 *
 * <p>The engine has a great many small rules, and the golden master tests are the only
 * complete description of them. Its behaviour is to be kept exactly, odd corners included.
 *
 * <h2>Instructions</h2>
 * {@link #submit(String)} takes one line and always answers with one line, either
 * {@code OK ...} or {@code REJECTED CODE: message}. It never throws.
 * <pre>
 * TRANSFER|date|fromAccount|toAccount|amount|currency
 * WITHDRAW|date|account|amount
 * DEPOSIT|date|account|amount
 * REVERSE|date|transactionId
 * </pre>
 *
 * <h2>Independent ledgers</h2>
 * Every ledger is a separate bank. Opening a second one must not disturb the first.
 */
public interface Ledger {

    /**
     * Opens an account. The tier is STANDARD, PREMIUM or STAFF, the currency PLN, EUR or
     * USD, the opening balance an amount such as {@code 250.00}. Anything else, or an id
     * containing {@code |}, throws {@link IllegalArgumentException}; an id already in use
     * throws {@link IllegalStateException}.
     */
    void openAccount(String id, String tier, String currency, String openingBalance);

    /** Runs one instruction and describes what happened. */
    String submit(String instruction);

    /** The balance as {@code amount currency}, e.g. {@code 899.50 PLN}. Unknown id: IAE. */
    String balance(String id);

    /** One line per instruction ever submitted, oldest first. */
    List<String> auditLog();

    /** A three-line summary of one day, plus its heading. */
    String dailyReport(String date);
}
