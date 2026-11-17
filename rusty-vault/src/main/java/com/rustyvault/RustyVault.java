package com.rustyvault;

import com.rustyvault.legacy.Db;
import com.rustyvault.legacy.LegacyCustomerDirectory;

/**
 * Front door of the vault. Every call opens a brand new, empty database.
 *
 * <p>The tests only call {@link #openDirectory()}, so its signature has to survive any
 * refactoring. What it returns is fair game.
 */
public final class RustyVault {

    private RustyVault() {
    }

    public static CustomerDirectory openDirectory() {
        return new LegacyCustomerDirectory(Db.openEmpty());
    }
}
