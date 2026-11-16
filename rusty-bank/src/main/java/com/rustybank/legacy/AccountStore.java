package com.rustybank.legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ACCOUNT MASTER FILE (in memory since the mainframe was switched off)
 */
public class AccountStore {

    private Map<String, Account> accounts = new HashMap<String, Account>();

    public void add(String id) {
        if (id == null || id.trim().length() == 0) {
            throw new IllegalArgumentException("INVALID ACCOUNT ID");
        }
        if (accounts.containsKey(id)) {
            throw new IllegalStateException("ACCOUNT EXISTS: " + id);
        }
        accounts.put(id, new Account(id));
    }

    public Account find(String id) {
        Account a = accounts.get(id);
        if (a == null) {
            throw new IllegalArgumentException("UNKNOWN ACCOUNT: " + id);
        }
        return a;
    }

    public List<Account> all() {
        return new ArrayList<Account>(accounts.values());
    }
}
