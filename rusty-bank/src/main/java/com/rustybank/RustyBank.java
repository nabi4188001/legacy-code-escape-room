package com.rustybank;

import com.rustybank.legacy.AccountStore;
import com.rustybank.legacy.LegacyTellerTerminal;

/**
 * Front door of Rusty Bank. Every call opens a brand new, empty bank.
 *
 * <p>The tests only ever call {@link #openTerminal()}, so its signature has to survive
 * any refactoring. What it returns is fair game.
 */
public final class RustyBank {

    private RustyBank() {
    }

    public static TellerTerminal openTerminal() {
        return new LegacyTellerTerminal(new AccountStore());
    }
}
