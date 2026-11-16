package com.rustybank.rooms.penny;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rustybank.RustyBank;
import com.rustybank.TellerTerminal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 1 - THE PENNY ROOM - the lock on the door.
 *
 * <p><b>LOCKED FILE.</b> Every test here fails against the legacy code, because Rusty
 * Bank keeps its money in {@code double}. The room is escaped when all of them pass
 * and the golden master is still green.
 */
@Tag("escape")
@DisplayName("Penny Room: the missing pennies")
class PennyRoomEscapeTest {

    private static TellerTerminal bankWith(String... accountIds) {
        TellerTerminal terminal = RustyBank.openTerminal();
        for (String id : accountIds) {
            terminal.openAccount(id);
        }
        return terminal;
    }

    @Test
    @DisplayName("a customer can withdraw everything they paid in")
    void customerCanWithdrawWholeBalance() {
        TellerTerminal terminal = bankWith("ACC-1");
        terminal.deposit("ACC-1", "0.10");
        terminal.deposit("ACC-1", "0.70");
        assertEquals("0.80", terminal.balance("ACC-1"));
        terminal.withdraw("ACC-1", "0.80");
        assertEquals("0.00", terminal.balance("ACC-1"));
    }

    @Test
    @DisplayName("ten deposits of 0.10 are worth exactly 1.00")
    void tenDimesMakeADollar() {
        TellerTerminal terminal = bankWith("ACC-1");
        for (int i = 0; i < 10; i++) {
            terminal.deposit("ACC-1", "0.10");
        }
        terminal.withdraw("ACC-1", "1.00");
        assertEquals("0.00", terminal.balance("ACC-1"));
    }

    @Test
    @DisplayName("interest of exactly half a cent rounds up: 110.00 at 3% is 0.28")
    void halfCentOfInterestRoundsUp() {
        TellerTerminal terminal = bankWith("SAVER");
        terminal.deposit("SAVER", "110.00");
        // 110.00 x 3 / 100 / 12 = 0.275 exactly
        assertEquals("0.28", terminal.postMonthlyInterest("SAVER", "3"));
        assertEquals("110.28", terminal.balance("SAVER"));
    }

    @Test
    @DisplayName("interest of exactly half a cent rounds up: 294.00 at 5% is 1.23")
    void halfCentOfInterestRoundsUpAtAnotherRate() {
        TellerTerminal terminal = bankWith("SAVER");
        terminal.deposit("SAVER", "294.00");
        // 294.00 x 5 / 100 / 12 = 1.225 exactly
        assertEquals("1.23", terminal.postMonthlyInterest("SAVER", "5"));
        assertEquals("295.23", terminal.balance("SAVER"));
    }

    @Test
    @DisplayName("the month-end interest run pays the branch what it is owed")
    void monthEndRunAddsUp() {
        String[] balances = {"177.00", "181.00", "203.00", "207.00", "245.00"};
        TellerTerminal terminal = RustyBank.openTerminal();
        for (int i = 0; i < balances.length; i++) {
            terminal.openAccount("SAVER-" + i);
            terminal.deposit("SAVER-" + i, balances[i]);
        }
        assertEquals("1013.00", terminal.totalHoldings());
        for (int i = 0; i < balances.length; i++) {
            terminal.postMonthlyInterest("SAVER-" + i, "6");
        }
        // 0.885 + 0.905 + 1.015 + 1.035 + 1.225, each rounded half-up = 5.09
        assertEquals("1018.09", terminal.totalHoldings());
    }

    @Test
    @DisplayName("splitting 100.00 three ways loses nothing")
    void unevenSplitConservesMoney() {
        TellerTerminal terminal = bankWith("PAYER", "P1", "P2", "P3");
        terminal.deposit("PAYER", "100.00");
        List<String> shares = terminal.splitPayment("PAYER", "100.00", List.of("P1", "P2", "P3"));
        assertEquals(List.of("33.34", "33.33", "33.33"), shares);
        assertEquals("33.34", terminal.balance("P1"));
        assertEquals("33.33", terminal.balance("P3"));
        assertEquals("0.00", terminal.balance("PAYER"));
        assertEquals("100.00", terminal.totalHoldings());
    }

    @Test
    @DisplayName("leftover cents go to the front of the payee list")
    void leftoverCentsGoToTheFront() {
        TellerTerminal terminal = bankWith("PAYER", "P1", "P2", "P3", "P4");
        terminal.deposit("PAYER", "10.03");
        List<String> shares =
                terminal.splitPayment("PAYER", "10.03", List.of("P1", "P2", "P3", "P4"));
        assertEquals(List.of("2.51", "2.51", "2.51", "2.50"), shares);
        assertEquals("10.03", terminal.totalHoldings());
    }

    @Test
    @DisplayName("a payment to a single payee arrives in full")
    void singlePayeeGetsEverything() {
        TellerTerminal terminal = bankWith("PAYER", "LANDLORD");
        terminal.deposit("PAYER", "64.07");
        List<String> shares = terminal.splitPayment("PAYER", "64.07", List.of("LANDLORD"));
        assertEquals(List.of("64.07"), shares);
        assertEquals("64.07", terminal.balance("LANDLORD"));
        assertEquals("64.07", terminal.totalHoldings());
    }

    @Test
    @DisplayName("one cent still counts next to ten quadrillion")
    void aCentIsNeverTooSmall() {
        TellerTerminal terminal = bankWith("TREASURY");
        terminal.deposit("TREASURY", "10000000000000000.00");
        terminal.deposit("TREASURY", "0.01");
        assertEquals("10000000000000000.01", terminal.balance("TREASURY"));
    }
}
