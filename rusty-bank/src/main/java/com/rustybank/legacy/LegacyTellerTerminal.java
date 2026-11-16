package com.rustybank.legacy;

import com.rustybank.TellerTerminal;

import java.util.ArrayList;
import java.util.List;

/**
 * TELLER TERMINAL PROGRAM
 * CREATED 12.03.1999
 * CHG 09.09.2009 - migrated from the green screens, logic unchanged
 */
public class LegacyTellerTerminal implements TellerTerminal {

    private AccountStore store;
    private InterestCalculator interestCalculator = new InterestCalculator();
    private PaymentSplitter paymentSplitter = new PaymentSplitter();

    public LegacyTellerTerminal(AccountStore store) {
        this.store = store;
    }

    public void openAccount(String accountId) {
        store.add(accountId);
    }

    public void deposit(String accountId, String amount) {
        Account a = store.find(accountId);
        double d = MoneyUtil.parse(amount);
        a.setBalance(a.getBalance() + d);
    }

    public void withdraw(String accountId, String amount) {
        Account a = store.find(accountId);
        double d = MoneyUtil.parse(amount);
        if (d > a.getBalance()) {
            throw new IllegalStateException("INSUFFICIENT FUNDS");
        }
        a.setBalance(a.getBalance() - d);
    }

    public String balance(String accountId) {
        return MoneyUtil.format(store.find(accountId).getBalance());
    }

    public String postMonthlyInterest(String accountId, String annualRatePercent) {
        Account a = store.find(accountId);
        double rate = MoneyUtil.parse(annualRatePercent);
        double interest = interestCalculator.monthlyInterest(a.getBalance(), rate);
        a.setBalance(a.getBalance() + interest);
        return MoneyUtil.format(interest);
    }

    public List<String> splitPayment(String fromAccountId, String amount, List<String> payeeIds) {
        Account from = store.find(fromAccountId);
        if (payeeIds == null || payeeIds.size() == 0) {
            throw new IllegalArgumentException("NO PAYEES");
        }
        List<Account> payees = new ArrayList<Account>();
        for (int i = 0; i < payeeIds.size(); i++) {
            payees.add(store.find(payeeIds.get(i)));
        }
        double total = MoneyUtil.parse(amount);
        if (total > from.getBalance()) {
            throw new IllegalStateException("INSUFFICIENT FUNDS");
        }
        double share = paymentSplitter.shareOf(total, payees.size());
        from.setBalance(from.getBalance() - total);
        List<String> shares = new ArrayList<String>();
        for (int i = 0; i < payees.size(); i++) {
            Account p = payees.get(i);
            p.setBalance(p.getBalance() + share);
            shares.add(MoneyUtil.format(share));
        }
        return shares;
    }

    public String totalHoldings() {
        double sum = 0;
        List<Account> all = store.all();
        for (int i = 0; i < all.size(); i++) {
            sum = sum + all.get(i).getBalance();
        }
        return MoneyUtil.format(sum);
    }
}
