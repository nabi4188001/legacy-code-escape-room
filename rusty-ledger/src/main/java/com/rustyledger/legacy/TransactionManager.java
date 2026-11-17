package com.rustyledger.legacy;

import com.rustyledger.Ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * TRANSACTION MANAGER
 * CREATED 04.11.1998
 * CHG 19.02.2001 - fees by tier
 * CHG 07.07.2004 - foreign currency (EUR, USD)
 * CHG 30.01.2008 - reversals, "same day only, ask Marek"
 * CHG 12.05.2011 - daily report for the branch managers
 * CHG 23.09.2015 - overdraft for premium customers
 * DO NOT SPLIT. THE ORDER OF THE CHECKS MATTERS.
 */
public class TransactionManager implements Ledger {

    // there is only one bank
    static Map<String, Object[]> accounts = new HashMap<String, Object[]>();
    static Map<String, BigDecimal> used = new HashMap<String, BigDecimal>();
    static Map<String, String[]> txs = new LinkedHashMap<String, String[]>();
    static List<String> audit = new ArrayList<String>();
    static Map<String, int[]> counts = new HashMap<String, int[]>();
    static Map<String, Map<String, BigDecimal>> fees =
            new HashMap<String, Map<String, BigDecimal>>();
    static int auditSeq = 0;
    static int txSeq = 0;

    public static void reset() {
        accounts.clear();
        used.clear();
        txs.clear();
        audit.clear();
        counts.clear();
        fees.clear();
        auditSeq = 0;
        txSeq = 0;
    }

    public void openAccount(String id, String tier, String currency, String openingBalance) {
        if (id == null || id.trim().length() == 0 || id.indexOf('|') >= 0) {
            throw new IllegalArgumentException("INVALID ACCOUNT ID");
        }
        if (!"STANDARD".equals(tier) && !"PREMIUM".equals(tier) && !"STAFF".equals(tier)) {
            throw new IllegalArgumentException("INVALID TIER: " + tier);
        }
        if (!"PLN".equals(currency) && !"EUR".equals(currency) && !"USD".equals(currency)) {
            throw new IllegalArgumentException("INVALID CURRENCY: " + currency);
        }
        if (openingBalance == null || !openingBalance.matches("\\d+(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("INVALID AMOUNT: " + openingBalance);
        }
        if (accounts.containsKey(id)) {
            throw new IllegalStateException("ACCOUNT EXISTS: " + id);
        }
        accounts.put(id, new Object[] {tier, currency,
                new BigDecimal(openingBalance).setScale(2)});
    }

    public String balance(String id) {
        Object[] a = accounts.get(id);
        if (a == null) {
            throw new IllegalArgumentException("UNKNOWN ACCOUNT: " + id);
        }
        return a[2] + " " + a[1];
    }

    public List<String> auditLog() {
        return new ArrayList<String>(audit);
    }

    public String dailyReport(String date) {
        int[] c = counts.get(date);
        int ok = c == null ? 0 : c[0];
        int rejected = c == null ? 0 : c[1];
        Map<String, BigDecimal> f = fees.get(date);
        String feeText = "none";
        if (f != null && f.size() > 0) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, BigDecimal> e : f.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(e.getValue()).append(" ").append(e.getKey());
            }
            feeText = sb.toString();
        }
        return "REPORT " + date + "\nok: " + ok + "\nrejected: " + rejected + "\nfees: " + feeText;
    }

    public String submit(String line) {
        if (line == null || line.trim().length() == 0) {
            return reject("???", null, "BAD_INSTRUCTION", "empty instruction");
        }
        String[] f = line.split("\\|", -1);
        String verb = f[0];
        int want;
        if (verb.equals("TRANSFER")) {
            want = 6;
        } else if (verb.equals("WITHDRAW")) {
            want = 4;
        } else if (verb.equals("DEPOSIT")) {
            want = 4;
        } else if (verb.equals("REVERSE")) {
            want = 3;
        } else {
            return reject("???", null, "BAD_INSTRUCTION", "unknown verb " + verb);
        }
        if (f.length != want) {
            return reject(verb, null, "BAD_INSTRUCTION", verb + " needs " + want + " fields");
        }
        String date = f[1];
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return reject(verb, null, "BAD_DATE", date);
        }
        if (verb.equals("REVERSE")) {
            return doReverse(date, f[2]);
        }
        String amountText = verb.equals("TRANSFER") ? f[4] : f[3];
        if (!amountText.matches("\\d+(\\.\\d{1,2})?")) {
            return reject(verb, date, "BAD_AMOUNT", amountText);
        }
        BigDecimal amount = new BigDecimal(amountText).setScale(2);
        if (amount.signum() <= 0) {
            return reject(verb, date, "BAD_AMOUNT", amountText);
        }
        if (verb.equals("TRANSFER")) {
            return doTransfer(date, f[2], f[3], amount, f[5]);
        } else if (verb.equals("WITHDRAW")) {
            return doWithdraw(date, f[2], amount);
        }
        return doDeposit(date, f[2], amount);
    }

    private String doTransfer(String date, String fromId, String toId, BigDecimal amount,
            String currency) {
        Object[] from = accounts.get(fromId);
        if (from == null) {
            return reject("TRANSFER", date, "UNKNOWN_ACCOUNT", fromId);
        }
        Object[] to = accounts.get(toId);
        if (to == null) {
            return reject("TRANSFER", date, "UNKNOWN_ACCOUNT", toId);
        }
        if (fromId.equals(toId)) {
            return reject("TRANSFER", date, "SAME_ACCOUNT", "cannot transfer to the same account");
        }
        String fromCur = (String) from[1];
        if (!fromCur.equals(currency)) {
            return reject("TRANSFER", date, "CURRENCY_MISMATCH", fromId + " holds " + fromCur);
        }
        String tier = (String) from[0];
        BigDecimal fee;
        if (tier.equals("STANDARD")) {
            fee = amount.multiply(new BigDecimal("0.005")).setScale(2, RoundingMode.HALF_UP);
            fee = fee.max(new BigDecimal("0.20"));
        } else if (tier.equals("PREMIUM")) {
            fee = amount.multiply(new BigDecimal("0.0025")).setScale(2, RoundingMode.HALF_UP);
            fee = fee.max(new BigDecimal("0.10")).min(new BigDecimal("5.00"));
        } else {
            fee = new BigDecimal("0.00");
        }
        String rejection = checkLimitAndFunds(date, fromId, from, amount, fee);
        if (rejection != null) {
            return reject("TRANSFER", date, rejection.substring(0, rejection.indexOf(':')),
                    rejection.substring(rejection.indexOf(':') + 2));
        }
        BigDecimal credited = amount;
        String toCur = (String) to[1];
        if (!fromCur.equals(toCur)) {
            credited = convert(amount, fromCur, toCur, tier);
        }
        from[2] = ((BigDecimal) from[2]).subtract(amount.add(fee));
        to[2] = ((BigDecimal) to[2]).add(credited);
        used.put(fromId + "|" + date, usedToday(fromId, date).add(amount));
        txSeq++;
        String txId = String.format("TX-%06d", txSeq);
        txs.put(txId, new String[] {"TRANSFER", date, fromId, toId, amount.toString(), fromCur,
                fee.toString(), credited.toString(), "N"});
        addFee(date, fromCur, fee);
        return accept("TRANSFER", date, txId, "fee=" + fee + " " + fromCur);
    }

    private String doWithdraw(String date, String id, BigDecimal amount) {
        Object[] a = accounts.get(id);
        if (a == null) {
            return reject("WITHDRAW", date, "UNKNOWN_ACCOUNT", id);
        }
        String tier = (String) a[0];
        BigDecimal fee = tier.equals("STANDARD") ? new BigDecimal("1.00") : new BigDecimal("0.00");
        String rejection = checkLimitAndFunds(date, id, a, amount, fee);
        if (rejection != null) {
            return reject("WITHDRAW", date, rejection.substring(0, rejection.indexOf(':')),
                    rejection.substring(rejection.indexOf(':') + 2));
        }
        a[2] = ((BigDecimal) a[2]).subtract(amount.add(fee));
        used.put(id + "|" + date, usedToday(id, date).add(amount));
        txSeq++;
        String txId = String.format("TX-%06d", txSeq);
        txs.put(txId, new String[] {"WITHDRAW", date, id, "", amount.toString(), (String) a[1],
                fee.toString(), amount.toString(), "N"});
        addFee(date, (String) a[1], fee);
        return accept("WITHDRAW", date, txId, "fee=" + fee + " " + a[1]);
    }

    private String doDeposit(String date, String id, BigDecimal amount) {
        Object[] a = accounts.get(id);
        if (a == null) {
            return reject("DEPOSIT", date, "UNKNOWN_ACCOUNT", id);
        }
        a[2] = ((BigDecimal) a[2]).add(amount);
        txSeq++;
        String txId = String.format("TX-%06d", txSeq);
        txs.put(txId, new String[] {"DEPOSIT", date, id, "", amount.toString(), (String) a[1],
                "0.00", amount.toString(), "N"});
        return accept("DEPOSIT", date, txId, "fee=0.00 " + a[1]);
    }

    private String doReverse(String date, String txId) {
        String[] tx = txs.get(txId);
        if (tx == null) {
            return reject("REVERSE", date, "UNKNOWN_TX", txId);
        }
        if (!tx[0].equals("TRANSFER")) {
            return reject("REVERSE", date, "NOT_REVERSIBLE", txId + " is a " + tx[0]);
        }
        if (tx[8].equals("Y")) {
            return reject("REVERSE", date, "ALREADY_REVERSED", txId);
        }
        if (!tx[1].equals(date)) {
            return reject("REVERSE", date, "REVERSAL_WINDOW", txId + " was made on " + tx[1]);
        }
        Object[] from = accounts.get(tx[2]);
        Object[] to = accounts.get(tx[3]);
        BigDecimal fee = new BigDecimal(tx[6]);
        to[2] = ((BigDecimal) to[2]).subtract(new BigDecimal(tx[7]));
        BigDecimal back = new BigDecimal(tx[4]);
        if (from[0].equals("PREMIUM")) {
            back = back.add(fee);
            if (fee.signum() != 0) {
                addFee(date, (String) from[1], fee.negate());
            }
        }
        from[2] = ((BigDecimal) from[2]).add(back);
        tx[8] = "Y";
        txSeq++;
        String newId = String.format("TX-%06d", txSeq);
        txs.put(newId, new String[] {"REVERSE", date, tx[2], tx[3], tx[4], tx[5], "0.00", tx[7],
                "Y"});
        return accept("REVERSE", date, newId, "reversed " + txId);
    }

    private String checkLimitAndFunds(String date, String id, Object[] a, BigDecimal amount,
            BigDecimal fee) {
        String tier = (String) a[0];
        BigDecimal limit;
        BigDecimal overdraft = new BigDecimal("0.00");
        if (tier.equals("STANDARD")) {
            limit = new BigDecimal("5000.00");
        } else if (tier.equals("PREMIUM")) {
            limit = new BigDecimal("20000.00");
            overdraft = new BigDecimal("500.00");
        } else {
            limit = new BigDecimal("50000.00");
        }
        if (usedToday(id, date).add(amount).compareTo(limit) > 0) {
            return "LIMIT_EXCEEDED: daily limit " + limit + " for " + tier;
        }
        BigDecimal balance = (BigDecimal) a[2];
        BigDecimal needed = amount.add(fee);
        if (balance.subtract(needed).compareTo(overdraft.negate()) < 0) {
            return "INSUFFICIENT_FUNDS: balance " + balance + " " + a[1] + ", needed " + needed
                    + " " + a[1];
        }
        return null;
    }

    private BigDecimal usedToday(String id, String date) {
        BigDecimal u = used.get(id + "|" + date);
        return u == null ? new BigDecimal("0.00") : u;
    }

    private BigDecimal rate(String currency) {
        if (currency.equals("EUR")) {
            return new BigDecimal("4.30");
        } else if (currency.equals("USD")) {
            return new BigDecimal("4.00");
        }
        return new BigDecimal("1.00");
    }

    private BigDecimal convert(BigDecimal amount, String from, String to, String tier) {
        BigDecimal inPln = amount.multiply(rate(from)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal result = inPln.divide(rate(to), 4, RoundingMode.HALF_UP);
        if (!tier.equals("STAFF")) {
            result = result.multiply(new BigDecimal("0.985"));
        }
        return result.setScale(2, RoundingMode.HALF_DOWN);
    }

    private void addFee(String date, String currency, BigDecimal delta) {
        if (delta.signum() == 0) {
            return;
        }
        Map<String, BigDecimal> m = fees.get(date);
        if (m == null) {
            m = new TreeMap<String, BigDecimal>();
            fees.put(date, m);
        }
        BigDecimal old = m.get(currency);
        m.put(currency, old == null ? delta : old.add(delta));
    }

    private String accept(String verb, String date, String txId, String extra) {
        count(date, 0);
        record(verb, date, "OK " + txId);
        return "OK " + txId + " " + extra;
    }

    private String reject(String verb, String date, String code, String message) {
        if (date != null) {
            count(date, 1);
        }
        record(verb, date == null ? "????-??-??" : date, "REJECTED " + code);
        return "REJECTED " + code + ": " + message;
    }

    private void count(String date, int slot) {
        int[] c = counts.get(date);
        if (c == null) {
            c = new int[2];
            counts.put(date, c);
        }
        c[slot]++;
    }

    private void record(String verb, String date, String outcome) {
        auditSeq++;
        audit.add(String.format("%03d  %s %s %s", auditSeq, verb, date, outcome));
    }
}
