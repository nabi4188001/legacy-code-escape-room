package com.rustyledger;

import com.rustyledger.legacy.TransactionManager;

/**
 * Front door of the engine. The tests only call {@link #openLedger()}, so its signature
 * has to survive any refactoring. What it returns is fair game.
 */
public final class RustyLedger {

    private RustyLedger() {
    }

    public static Ledger openLedger() {
        TransactionManager.reset();
        return new TransactionManager();
    }
}
