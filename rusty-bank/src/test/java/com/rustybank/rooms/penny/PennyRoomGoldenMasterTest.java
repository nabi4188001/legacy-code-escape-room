package com.rustybank.rooms.penny;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rustybank.RustyBank;
import com.rustybank.TellerTerminal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 1 - THE PENNY ROOM - golden master.
 *
 * <p><b>LOCKED FILE.</b> These tests pin down behaviour that already works today. They
 * pass before the escape and must still pass after it. If one of them goes red, the
 * refactoring broke the bank.
 */
@Tag("golden")
@DisplayName("Penny Room: behaviour that must survive")
class PennyRoomGoldenMasterTest {

    private static TellerTerminal bankWith(String... accountIds) {
        TellerTerminal terminal = RustyBank.openTerminal();
        for (String id : accountIds) {
            terminal.openAccount(id);
        }
        return terminal;
    }

    @Test
    @DisplayName("a new account starts at 0.00")
    void newAccountIsEmpty() {
        TellerTerminal terminal = bankWith("ACC-1");
        assertEquals("0.00", terminal.balance("ACC-1"));
        assertEquals("0.00", terminal.totalHoldings());
    }

    @Test
    @DisplayName("deposits add up")
    void depositsAddUp() {
        TellerTerminal terminal = bankWith("ACC-1");
        terminal.deposit("ACC-1", "100.50");
        terminal.deposit("ACC-1", "200.25");
        assertEquals("300.75", terminal.balance("ACC-1"));
    }

    @Test
    @DisplayName("balances always show two decimals")
    void balancesShowTwoDecimals() {
        TellerTerminal terminal = bankWith("A", "B", "C");
        terminal.deposit("A", "5");
        terminal.deposit("B", "0.5");
        terminal.deposit("C", "1234.56");
        assertEquals("5.00", terminal.balance("A"));
        assertEquals("0.50", terminal.balance("B"));
        assertEquals("1234.56", terminal.balance("C"));
    }

    @Test
    @DisplayName("a single deposit is shown exactly as paid in")
    void singleDepositIsShownExactly() {
        for (String amount : List.of("19.99", "4.35", "1.15", "8.20", "0.07", "0.01")) {
            TellerTerminal terminal = bankWith("ACC-1");
            terminal.deposit("ACC-1", amount);
            assertEquals(amount, terminal.balance("ACC-1"));
        }
    }

    @Test
    @DisplayName("withdrawals reduce the balance")
    void withdrawalsReduceTheBalance() {
        TellerTerminal terminal = bankWith("ACC-1");
        terminal.deposit("ACC-1", "300.75");
        terminal.withdraw("ACC-1", "100.25");
        assertEquals("200.50", terminal.balance("ACC-1"));
    }

    @Test
    @DisplayName("an overdraft is refused and nothing moves")
    void overdraftIsRefused() {
        TellerTerminal terminal = bankWith("ACC-1");
        terminal.deposit("ACC-1", "50.00");
        assertThrows(IllegalStateException.class, () -> terminal.withdraw("ACC-1", "50.25"));
        assertEquals("50.00", terminal.balance("ACC-1"));
    }

    @Test
    @DisplayName("unknown accounts are rejected")
    void unknownAccountsAreRejected() {
        TellerTerminal terminal = bankWith("ACC-1");
        assertThrows(IllegalArgumentException.class, () -> terminal.balance("NOPE"));
        assertThrows(IllegalArgumentException.class, () -> terminal.deposit("NOPE", "1.00"));
        assertThrows(IllegalArgumentException.class, () -> terminal.withdraw("NOPE", "1.00"));
    }

    @Test
    @DisplayName("an account id can only be opened once")
    void duplicateAccountIsRejected() {
        TellerTerminal terminal = bankWith("ACC-1");
        assertThrows(IllegalStateException.class, () -> terminal.openAccount("ACC-1"));
    }

    @Test
    @DisplayName("zero, negative and garbage amounts are rejected")
    void badAmountsAreRejected() {
        TellerTerminal terminal = bankWith("ACC-1");
        terminal.deposit("ACC-1", "10.00");
        assertThrows(IllegalArgumentException.class, () -> terminal.deposit("ACC-1", "0"));
        assertThrows(IllegalArgumentException.class, () -> terminal.deposit("ACC-1", "-5.00"));
        assertThrows(IllegalArgumentException.class, () -> terminal.deposit("ACC-1", "ten"));
        assertThrows(IllegalArgumentException.class, () -> terminal.withdraw("ACC-1", "-1.00"));
        assertEquals("10.00", terminal.balance("ACC-1"));
    }

    @Test
    @DisplayName("monthly interest on a round balance")
    void monthlyInterestOnRoundBalance() {
        TellerTerminal terminal = bankWith("SAVER");
        terminal.deposit("SAVER", "1200.00");
        assertEquals("5.00", terminal.postMonthlyInterest("SAVER", "5"));
        assertEquals("1205.00", terminal.balance("SAVER"));
    }

    @Test
    @DisplayName("a payment that divides evenly is split evenly")
    void evenSplit() {
        TellerTerminal terminal = bankWith("PAYER", "P1", "P2", "P3");
        terminal.deposit("PAYER", "100.00");
        List<String> shares = terminal.splitPayment("PAYER", "90.00", List.of("P1", "P2", "P3"));
        assertEquals(List.of("30.00", "30.00", "30.00"), shares);
        assertEquals("10.00", terminal.balance("PAYER"));
        assertEquals("30.00", terminal.balance("P2"));
        assertEquals("100.00", terminal.totalHoldings());
    }

    @Test
    @DisplayName("a split needs payees and enough money")
    void splitIsValidated() {
        TellerTerminal terminal = bankWith("PAYER", "P1");
        terminal.deposit("PAYER", "10.00");
        assertThrows(IllegalArgumentException.class,
                () -> terminal.splitPayment("PAYER", "5.00", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> terminal.splitPayment("PAYER", "5.00", List.of("NOPE")));
        assertThrows(IllegalStateException.class,
                () -> terminal.splitPayment("PAYER", "10.50", List.of("P1")));
        assertEquals("10.00", terminal.balance("PAYER"));
        assertEquals("0.00", terminal.balance("P1"));
    }

    @Test
    @DisplayName("every terminal is its own bank")
    void terminalsAreIsolated() {
        TellerTerminal first = bankWith("ACC-1");
        first.deposit("ACC-1", "75.00");
        TellerTerminal second = bankWith("ACC-1");
        assertEquals("0.00", second.balance("ACC-1"));
        assertEquals("75.00", first.totalHoldings());
    }
}
