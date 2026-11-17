package com.rustyledger.rooms.god;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rustyledger.Ledger;
import com.rustyledger.RustyLedger;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 4 - THE GOD CLASS - golden master.
 *
 * <p><b>LOCKED FILE.</b> These tests are the only complete description of what the engine
 * does, odd corners included. They pass before the escape and must still pass after it.
 * If one of them goes red, the refactoring changed the bank.
 */
@Tag("golden")
@DisplayName("God Class: behaviour that must survive")
class LedgerGoldenMasterTest {

    private static final String D = "2026-03-02";

    /** Six accounts. PL-STD, PL-PRE and PL-STF hold 1000.00 PLN each. */
    private static Ledger ledger() {
        Ledger ledger = RustyLedger.openLedger();
        ledger.openAccount("PL-STD", "STANDARD", "PLN", "1000.00");
        ledger.openAccount("PL-PRE", "PREMIUM", "PLN", "1000.00");
        ledger.openAccount("PL-STF", "STAFF", "PLN", "1000.00");
        ledger.openAccount("EU-STD", "STANDARD", "EUR", "500.00");
        ledger.openAccount("EU-STF", "STAFF", "EUR", "100.00");
        ledger.openAccount("US-PRE", "PREMIUM", "USD", "200.00");
        return ledger;
    }

    private static String transfer(String date, String from, String to, String amount, String cur) {
        return "TRANSFER|" + date + "|" + from + "|" + to + "|" + amount + "|" + cur;
    }

    @Test
    @DisplayName("accounts show their balance with two decimals and a currency")
    void balances() {
        Ledger ledger = ledger();
        assertEquals("1000.00 PLN", ledger.balance("PL-STD"));
        assertEquals("500.00 EUR", ledger.balance("EU-STD"));
        ledger.openAccount("X", "STAFF", "USD", "7");
        ledger.openAccount("Y", "STAFF", "USD", "0.5");
        assertEquals("7.00 USD", ledger.balance("X"));
        assertEquals("0.50 USD", ledger.balance("Y"));
        assertThrows(IllegalArgumentException.class, () -> ledger.balance("NOPE"));
    }

    @Test
    @DisplayName("bad accounts are refused")
    void openAccountValidation() {
        Ledger ledger = ledger();
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A", "GOLD", "PLN", "1.00"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A", "STAFF", "GBP", "1.00"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A", "STAFF", "PLN", "-5"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A", "STAFF", "PLN", "abc"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A", "STAFF", "PLN", "1.234"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A", "STAFF", "PLN", null));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount("A|B", "STAFF", "PLN", "1"));
        assertThrows(IllegalArgumentException.class, () -> ledger.openAccount(" ", "STAFF", "PLN", "1"));
        assertThrows(IllegalStateException.class, () -> ledger.openAccount("PL-STD", "STAFF", "PLN", "1"));
    }

    @Test
    @DisplayName("a standard transfer costs half a percent, at least 0.20")
    void standardFee() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=0.50 PLN", ledger.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN")));
        assertEquals("899.50 PLN", ledger.balance("PL-STD"));
        assertEquals("1100.00 PLN", ledger.balance("PL-PRE"));
        assertEquals("OK TX-000002 fee=0.20 PLN", ledger.submit(transfer(D, "PL-STD", "PL-PRE", "10.00", "PLN")));
        assertEquals("OK TX-000003 fee=1.51 PLN", ledger.submit(transfer(D, "PL-STD", "PL-PRE", "301.00", "PLN")));
        assertEquals("OK TX-000004 fee=0.20 PLN", ledger.submit(transfer(D, "PL-STD", "PL-PRE", "0.01", "PLN")));
    }

    @Test
    @DisplayName("a premium transfer costs a quarter of a percent, between 0.10 and 5.00")
    void premiumFee() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=0.25 PLN", ledger.submit(transfer(D, "PL-PRE", "PL-STD", "100.00", "PLN")));
        assertEquals("OK TX-000002 fee=0.10 PLN", ledger.submit(transfer(D, "PL-PRE", "PL-STD", "10.00", "PLN")));
        assertEquals("OK TX-000003 fee=0.00 PLN", ledger.submit("DEPOSIT|" + D + "|PL-PRE|9000.00"));
        assertEquals("OK TX-000004 fee=5.00 PLN", ledger.submit(transfer(D, "PL-PRE", "PL-STD", "4000.00", "PLN")));
    }

    @Test
    @DisplayName("staff pay no fee")
    void staffFee() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=0.00 PLN", ledger.submit(transfer(D, "PL-STF", "PL-STD", "200.00", "PLN")));
        assertEquals("800.00 PLN", ledger.balance("PL-STF"));
    }

    @Test
    @DisplayName("premium accounts may go 500.00 below zero, fee included")
    void overdraft() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=2.50 PLN", ledger.submit(transfer(D, "PL-PRE", "PL-STD", "1000.00", "PLN")));
        assertEquals("-2.50 PLN", ledger.balance("PL-PRE"));
        assertEquals("REJECTED INSUFFICIENT_FUNDS: balance -2.50 PLN, needed 498.24 PLN",
                ledger.submit(transfer(D, "PL-PRE", "PL-STD", "497.00", "PLN")));
        assertEquals("OK TX-000002 fee=1.24 PLN", ledger.submit(transfer(D, "PL-PRE", "PL-STD", "495.00", "PLN")));
        assertEquals("-498.74 PLN", ledger.balance("PL-PRE"));
    }

    @Test
    @DisplayName("withdrawals cost 1.00 for standard accounts and nothing for the others")
    void withdrawals() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=1.00 PLN", ledger.submit("WITHDRAW|" + D + "|PL-STD|100.00"));
        assertEquals("899.00 PLN", ledger.balance("PL-STD"));
        assertEquals("OK TX-000002 fee=0.00 PLN", ledger.submit("WITHDRAW|" + D + "|PL-PRE|100.00"));
        assertEquals("900.00 PLN", ledger.balance("PL-PRE"));
        assertEquals("REJECTED INSUFFICIENT_FUNDS: balance 899.00 PLN, needed 900.00 PLN",
                ledger.submit("WITHDRAW|" + D + "|PL-STD|899.00"));
    }

    @Test
    @DisplayName("deposits are free and unlimited")
    void deposits() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=0.00 PLN", ledger.submit("DEPOSIT|" + D + "|PL-STD|100000.50"));
        assertEquals("101000.50 PLN", ledger.balance("PL-STD"));
        assertEquals("OK TX-000002 fee=0.00 EUR", ledger.submit("DEPOSIT|" + D + "|EU-STD|1"));
    }

    @Test
    @DisplayName("each account has a daily limit on what leaves it, counted per date")
    void dailyLimits() {
        Ledger ledger = ledger();
        ledger.submit("DEPOSIT|" + D + "|PL-STD|10000.00");
        assertEquals("OK TX-000002 fee=25.00 PLN", ledger.submit(transfer(D, "PL-STD", "PL-PRE", "5000.00", "PLN")));
        assertEquals("REJECTED LIMIT_EXCEEDED: daily limit 5000.00 for STANDARD",
                ledger.submit(transfer(D, "PL-STD", "PL-PRE", "0.01", "PLN")));
        assertEquals("REJECTED LIMIT_EXCEEDED: daily limit 5000.00 for STANDARD",
                ledger.submit("WITHDRAW|" + D + "|PL-STD|1.00"));
        assertEquals("OK TX-000003 fee=0.50 PLN", ledger.submit(transfer("2026-03-03", "PL-STD", "PL-PRE", "100.00", "PLN")));
        assertEquals("REJECTED LIMIT_EXCEEDED: daily limit 20000.00 for PREMIUM",
                ledger.submit(transfer(D, "PL-PRE", "PL-STD", "20000.01", "PLN")));
        assertEquals("REJECTED LIMIT_EXCEEDED: daily limit 50000.00 for STAFF",
                ledger.submit(transfer(D, "PL-STF", "PL-STD", "50000.01", "PLN")));
    }

    @Test
    @DisplayName("checks run in a fixed order, so the first problem is the one reported")
    void checkOrder() {
        Ledger ledger = ledger();
        assertEquals("REJECTED UNKNOWN_ACCOUNT: NOPE", ledger.submit(transfer(D, "NOPE", "ALSO-NOPE", "10.00", "PLN")));
        assertEquals("REJECTED UNKNOWN_ACCOUNT: ALSO-NOPE", ledger.submit(transfer(D, "PL-STD", "ALSO-NOPE", "10.00", "PLN")));
        assertEquals("REJECTED SAME_ACCOUNT: cannot transfer to the same account",
                ledger.submit(transfer(D, "PL-STD", "PL-STD", "10.00", "EUR")));
        assertEquals("REJECTED CURRENCY_MISMATCH: PL-STD holds PLN",
                ledger.submit(transfer(D, "PL-STD", "PL-PRE", "10.00", "EUR")));
        assertEquals("REJECTED LIMIT_EXCEEDED: daily limit 5000.00 for STANDARD",
                ledger.submit(transfer(D, "PL-STD", "PL-PRE", "6000.00", "PLN")));
        assertEquals("REJECTED INSUFFICIENT_FUNDS: balance 1000.00 PLN, needed 2010.00 PLN",
                ledger.submit(transfer(D, "PL-STD", "PL-PRE", "2000.00", "PLN")));
        assertEquals("REJECTED UNKNOWN_ACCOUNT: NOPE", ledger.submit("WITHDRAW|" + D + "|NOPE|5.00"));
        assertEquals("REJECTED UNKNOWN_ACCOUNT: NOPE", ledger.submit("DEPOSIT|" + D + "|NOPE|5.00"));
    }

    @Test
    @DisplayName("foreign transfers convert through zloty, take a 1.5% spread and round half down")
    void currencyConversion() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=0.50 EUR", ledger.submit(transfer(D, "EU-STD", "PL-PRE", "100.00", "EUR")));
        assertEquals("1423.55 PLN", ledger.balance("PL-PRE"));
        assertEquals("399.50 EUR", ledger.balance("EU-STD"));
        assertEquals("OK TX-000002 fee=0.25 PLN", ledger.submit(transfer(D, "PL-PRE", "EU-STD", "100.00", "PLN")));
        assertEquals("422.41 EUR", ledger.balance("EU-STD"));
        assertEquals("1323.30 PLN", ledger.balance("PL-PRE"));
    }

    @Test
    @DisplayName("staff convert without the spread")
    void staffConversion() {
        Ledger ledger = ledger();
        ledger.submit(transfer(D, "PL-STF", "EU-STD", "100.00", "PLN"));
        assertEquals("523.26 EUR", ledger.balance("EU-STD"));
        // 0.05 EUR is 0.215 PLN: exactly half a grosz rounds down
        ledger.submit(transfer(D, "EU-STF", "PL-PRE", "0.05", "EUR"));
        assertEquals("1000.21 PLN", ledger.balance("PL-PRE"));
    }

    @Test
    @DisplayName("dollars to euros go through zloty too")
    void crossConversion() {
        Ledger ledger = ledger();
        assertEquals("OK TX-000001 fee=0.10 USD", ledger.submit(transfer(D, "US-PRE", "EU-STD", "40.00", "USD")));
        assertEquals("536.65 EUR", ledger.balance("EU-STD"));
        assertEquals("159.90 USD", ledger.balance("US-PRE"));
    }

    @Test
    @DisplayName("a same-day reversal undoes a transfer, and only once")
    void reversals() {
        Ledger ledger = ledger();
        ledger.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN"));
        assertEquals("OK TX-000002 reversed TX-000001", ledger.submit("REVERSE|" + D + "|TX-000001"));
        assertEquals("999.50 PLN", ledger.balance("PL-STD"));
        assertEquals("1000.00 PLN", ledger.balance("PL-PRE"));
        assertEquals("REJECTED ALREADY_REVERSED: TX-000001", ledger.submit("REVERSE|" + D + "|TX-000001"));
    }

    @Test
    @DisplayName("a premium customer gets the fee back on a reversal")
    void premiumReversalRefundsTheFee() {
        Ledger ledger = ledger();
        ledger.submit(transfer(D, "PL-PRE", "PL-STD", "1000.00", "PLN"));
        assertEquals("-2.50 PLN", ledger.balance("PL-PRE"));
        ledger.submit("REVERSE|" + D + "|TX-000001");
        assertEquals("1000.00 PLN", ledger.balance("PL-PRE"));
        assertEquals("1000.00 PLN", ledger.balance("PL-STD"));
    }

    @Test
    @DisplayName("reversals are refused for other days, other kinds and unknown transactions")
    void reversalRules() {
        Ledger ledger = ledger();
        ledger.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN"));
        ledger.submit("WITHDRAW|" + D + "|PL-STD|5.00");
        ledger.submit("DEPOSIT|" + D + "|PL-STD|5.00");
        assertEquals("OK TX-000004 reversed TX-000001", ledger.submit("REVERSE|" + D + "|TX-000001"));
        assertEquals("REJECTED NOT_REVERSIBLE: TX-000002 is a WITHDRAW", ledger.submit("REVERSE|" + D + "|TX-000002"));
        assertEquals("REJECTED NOT_REVERSIBLE: TX-000003 is a DEPOSIT", ledger.submit("REVERSE|" + D + "|TX-000003"));
        assertEquals("REJECTED NOT_REVERSIBLE: TX-000004 is a REVERSE", ledger.submit("REVERSE|" + D + "|TX-000004"));
        assertEquals("REJECTED UNKNOWN_TX: TX-000099", ledger.submit("REVERSE|" + D + "|TX-000099"));
        ledger.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN"));
        assertEquals("REJECTED REVERSAL_WINDOW: TX-000005 was made on 2026-03-02",
                ledger.submit("REVERSE|2026-03-03|TX-000005"));
    }

    @Test
    @DisplayName("a reversal ignores whether the receiver still has the money, and the limit stays used")
    void reversalQuirks() {
        Ledger ledger = ledger();
        ledger.submit(transfer(D, "PL-PRE", "PL-STD", "1000.00", "PLN"));
        ledger.submit(transfer(D, "PL-STD", "PL-STF", "1900.00", "PLN"));
        assertEquals("90.50 PLN", ledger.balance("PL-STD"));
        assertEquals("OK TX-000003 reversed TX-000001", ledger.submit("REVERSE|" + D + "|TX-000001"));
        assertEquals("-909.50 PLN", ledger.balance("PL-STD"));

        Ledger other = ledger();
        other.submit("DEPOSIT|" + D + "|PL-STD|10000.00");
        other.submit(transfer(D, "PL-STD", "PL-PRE", "5000.00", "PLN"));
        other.submit("REVERSE|" + D + "|TX-000002");
        assertEquals("REJECTED LIMIT_EXCEEDED: daily limit 5000.00 for STANDARD",
                other.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN")));
    }

    @Test
    @DisplayName("badly formed instructions are rejected with a reason and never throw")
    void badInstructions() {
        Ledger ledger = ledger();
        assertEquals("REJECTED BAD_INSTRUCTION: empty instruction", ledger.submit(null));
        assertEquals("REJECTED BAD_INSTRUCTION: empty instruction", ledger.submit(""));
        assertEquals("REJECTED BAD_INSTRUCTION: empty instruction", ledger.submit("   "));
        assertEquals("REJECTED BAD_INSTRUCTION: unknown verb PAY", ledger.submit("PAY|" + D + "|PL-STD"));
        assertEquals("REJECTED BAD_INSTRUCTION: unknown verb transfer", ledger.submit("transfer|" + D));
        assertEquals("REJECTED BAD_INSTRUCTION: TRANSFER needs 6 fields", ledger.submit("TRANSFER|" + D + "|A|B"));
        assertEquals("REJECTED BAD_INSTRUCTION: DEPOSIT needs 4 fields", ledger.submit("DEPOSIT|" + D + "|A|1|2"));
        assertEquals("REJECTED BAD_INSTRUCTION: REVERSE needs 3 fields", ledger.submit("REVERSE|" + D));
        assertEquals("REJECTED BAD_DATE: 2026-3-2", ledger.submit("DEPOSIT|2026-3-2|PL-STD|10.00"));
        assertEquals("REJECTED BAD_DATE: tomorrow", ledger.submit("REVERSE|tomorrow|TX-000001"));
    }

    @Test
    @DisplayName("amounts must be positive with at most two decimals")
    void badAmounts() {
        Ledger ledger = ledger();
        for (String bad : new String[] {"0", "0.00", "-5", "1.234", "abc", "", "1,50", ".5", "5."}) {
            assertEquals("REJECTED BAD_AMOUNT: " + bad, ledger.submit("DEPOSIT|" + D + "|PL-STD|" + bad));
            assertEquals("REJECTED BAD_AMOUNT: " + bad,
                    ledger.submit(transfer(D, "PL-STD", "PL-PRE", bad, "PLN")));
        }
        assertEquals("1000.00 PLN", ledger.balance("PL-STD"));
    }

    @Test
    @DisplayName("a date only has to look like a date")
    void datesAreOnlyShapes() {
        assertEquals("OK TX-000001 fee=0.00 PLN", ledger().submit("DEPOSIT|2026-99-99|PL-STD|1.00"));
    }

    @Test
    @DisplayName("only successful instructions use up a transaction number")
    void transactionNumbers() {
        Ledger ledger = ledger();
        assertEquals("REJECTED UNKNOWN_ACCOUNT: NOPE", ledger.submit("DEPOSIT|" + D + "|NOPE|1.00"));
        assertEquals("OK TX-000001 fee=0.00 PLN", ledger.submit("DEPOSIT|" + D + "|PL-STD|1.00"));
        assertEquals("REJECTED BAD_AMOUNT: 0", ledger.submit("DEPOSIT|" + D + "|PL-STD|0"));
        assertEquals("OK TX-000002 fee=0.00 PLN", ledger.submit("DEPOSIT|" + D + "|PL-STD|1.00"));
    }

    @Test
    @DisplayName("the audit log has one numbered line for every instruction")
    void auditLog() {
        Ledger ledger = ledger();
        ledger.submit("DEPOSIT|" + D + "|PL-STD|10.00");
        ledger.submit(transfer(D, "NOPE", "PL-STD", "1.00", "PLN"));
        ledger.submit("");
        ledger.submit("TRANSFER|" + D + "|A");
        ledger.submit("WITHDRAW|soon|PL-STD|1.00");
        ledger.submit("WITHDRAW|" + D + "|PL-STD|1.00");
        ledger.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN"));
        ledger.submit("REVERSE|" + D + "|TX-000003");
        List<String> log = ledger.auditLog();
        assertEquals(List.of(
                "001  DEPOSIT 2026-03-02 OK TX-000001",
                "002  TRANSFER 2026-03-02 REJECTED UNKNOWN_ACCOUNT",
                "003  ??? ????-??-?? REJECTED BAD_INSTRUCTION",
                "004  TRANSFER ????-??-?? REJECTED BAD_INSTRUCTION",
                "005  WITHDRAW ????-??-?? REJECTED BAD_DATE",
                "006  WITHDRAW 2026-03-02 OK TX-000002",
                "007  TRANSFER 2026-03-02 OK TX-000003",
                "008  REVERSE 2026-03-02 OK TX-000004"), log);
        ledger.submit("DEPOSIT|" + D + "|PL-STD|1.00");
        assertEquals(8, log.size());
        assertEquals(9, ledger.auditLog().size());
    }

    @Test
    @DisplayName("the daily report counts, and adds up fees per currency")
    void dailyReport() {
        Ledger ledger = ledger();
        ledger.submit("DEPOSIT|" + D + "|PL-STD|1.00");
        ledger.submit(transfer(D, "PL-STD", "PL-PRE", "100.00", "PLN"));
        ledger.submit(transfer(D, "EU-STD", "PL-PRE", "10.00", "EUR"));
        ledger.submit("WITHDRAW|" + D + "|PL-STD|99999.00");
        ledger.submit("DEPOSIT|" + D + "|PL-STD|0");
        ledger.submit("DEPOSIT|bad-date|PL-STD|1.00");
        ledger.submit("NONSENSE");
        assertEquals("REPORT 2026-03-02\nok: 3\nrejected: 2\nfees: 0.20 EUR, 0.50 PLN", ledger.dailyReport(D));
        assertEquals("REPORT 2026-04-01\nok: 0\nrejected: 0\nfees: none", ledger.dailyReport("2026-04-01"));
    }

    @Test
    @DisplayName("a refunded premium fee can bring a day's fees back to zero")
    void reportAfterRefund() {
        Ledger ledger = ledger();
        ledger.submit(transfer(D, "PL-PRE", "PL-STD", "1000.00", "PLN"));
        ledger.submit("REVERSE|" + D + "|TX-000001");
        assertEquals("REPORT 2026-03-02\nok: 2\nrejected: 0\nfees: 0.00 PLN", ledger.dailyReport(D));
    }
}
