package com.rustybank;

import java.util.List;

/**
 * The teller terminal: the only way the outside world talks to Rusty Bank.
 *
 * <p><b>LOCKED FILE.</b> This interface is the contract every room is tested through.
 * An escaping agent may rewrite anything behind it, but may not change this file.
 *
 * <p>All amounts travel as plain decimal strings with exactly two decimal places on the
 * way out, e.g. {@code "1234.50"}. No currency symbols, no thousands separators.
 */
public interface TellerTerminal {

    /** Opens an empty account. Throws {@link IllegalStateException} if the id is taken. */
    void openAccount(String accountId);

    /**
     * Pays money in. Throws {@link IllegalArgumentException} for an unknown account or an
     * amount that is not a positive number.
     */
    void deposit(String accountId, String amount);

    /**
     * Pays money out. Throws {@link IllegalStateException} if the account cannot cover it,
     * {@link IllegalArgumentException} for an unknown account or a non-positive amount.
     */
    void withdraw(String accountId, String amount);

    /** Current balance, always with two decimals, e.g. {@code "0.80"}. */
    String balance(String accountId);

    /**
     * Credits one month of interest: balance x annualRatePercent / 100 / 12, rounded
     * half-up to the cent. Returns the interest that was credited.
     */
    String postMonthlyInterest(String accountId, String annualRatePercent);

    /**
     * Splits one payment equally between payees. Shares are whole cents; any cents left
     * over go one each to the payees at the front of the list. Returns each payee's share
     * in list order. No money may appear or disappear.
     */
    List<String> splitPayment(String fromAccountId, String amount, List<String> payeeIds);

    /** Sum of every balance in the bank. */
    String totalHoldings();
}
