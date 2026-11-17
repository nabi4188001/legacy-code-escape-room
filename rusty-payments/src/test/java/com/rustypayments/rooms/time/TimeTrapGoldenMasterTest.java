package com.rustypayments.rooms.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustypayments.PaymentCalendar;
import com.rustypayments.RustyPayments;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 2 - THE TIME TRAP - golden master.
 *
 * <p><b>LOCKED FILE.</b> These tests pin down behaviour that already works today, on a
 * server that happens to sit in Warsaw. They pass before the escape and must still pass
 * after it. If one of them goes red, the refactoring broke the bank.
 */
@Tag("golden")
@DisplayName("Time Trap: behaviour that must survive")
class TimeTrapGoldenMasterTest {

    private TimeZone savedZone;
    private Locale savedLocale;
    private final PaymentCalendar calendar = RustyPayments.openCalendar();

    @BeforeEach
    void serverSitsInWarsaw() {
        savedZone = TimeZone.getDefault();
        savedLocale = Locale.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Warsaw"));
        Locale.setDefault(Locale.US);
    }

    @AfterEach
    void restoreServer() {
        TimeZone.setDefault(savedZone);
        Locale.setDefault(savedLocale);
    }

    @Test
    @DisplayName("Monday to Friday are business days")
    void weekdaysAreBusinessDays() {
        assertTrue(calendar.isBusinessDay("2026-03-25"));
        assertTrue(calendar.isBusinessDay("2026-03-27"));
        assertTrue(calendar.isBusinessDay("2026-03-30"));
    }

    @Test
    @DisplayName("weekends are not business days")
    void weekendsAreNot() {
        assertFalse(calendar.isBusinessDay("2026-03-28"));
        assertFalse(calendar.isBusinessDay("2026-03-29"));
    }

    @Test
    @DisplayName("bank holidays are not business days")
    void holidaysAreNot() {
        assertFalse(calendar.isBusinessDay("2026-01-01"));
        assertFalse(calendar.isBusinessDay("2026-05-01"));
        assertFalse(calendar.isBusinessDay("2026-11-11"));
        assertFalse(calendar.isBusinessDay("2026-12-25"));
    }

    @Test
    @DisplayName("a payment in time is processed the same day, up to 16:00:00")
    void paymentInTime() {
        assertEquals("2026-03-25", calendar.processingDate("2026-03-25T10:15:00+01:00"));
        assertEquals("2026-03-25", calendar.processingDate("2026-03-25T15:59:59+01:00"));
        assertEquals("2026-03-25", calendar.processingDate("2026-03-25T16:00:00+01:00"));
    }

    @Test
    @DisplayName("a payment after the cut-off waits for the next business day")
    void paymentAfterCutOff() {
        assertEquals("2026-03-26", calendar.processingDate("2026-03-25T16:01:00+01:00"));
        assertEquals("2026-03-26", calendar.processingDate("2026-03-25T17:30:00+01:00"));
    }

    @Test
    @DisplayName("Friday evening and weekends wait for Monday")
    void weekendsWaitForMonday() {
        assertEquals("2026-03-30", calendar.processingDate("2026-03-27T17:00:00+01:00"));
        assertEquals("2026-03-30", calendar.processingDate("2026-03-28T10:00:00+01:00"));
        assertEquals("2026-03-30", calendar.processingDate("2026-03-29T10:00:00+02:00"));
    }

    @Test
    @DisplayName("holidays are skipped")
    void holidaysAreSkipped() {
        assertEquals("2026-11-12", calendar.processingDate("2026-11-10T17:00:00+01:00"));
        assertEquals("2026-11-12", calendar.processingDate("2026-11-11T09:00:00+01:00"));
    }

    @Test
    @DisplayName("instants may come in any offset")
    void anyOffset() {
        assertEquals("2026-06-15", calendar.processingDate("2026-06-15T08:00:00Z"));
        assertEquals("2026-06-16", calendar.processingDate("2026-06-15T13:30:00-05:00"));
    }

    @Test
    @DisplayName("settlement is one business day after processing")
    void settlement() {
        assertEquals("2026-03-26", calendar.settlementDate("2026-03-25T10:00:00+01:00"));
        assertEquals("2026-03-30", calendar.settlementDate("2026-03-27T10:00:00+01:00"));
        assertEquals("2026-03-31", calendar.settlementDate("2026-03-27T17:00:00+01:00"));
        assertEquals("2026-05-04", calendar.settlementDate("2026-04-30T10:00:00+02:00"));
    }

    @Test
    @DisplayName("days late are calendar days after the due date")
    void daysLate() {
        assertEquals(0, calendar.daysLate("2026-06-10", "2026-06-01T10:00:00+02:00"));
        assertEquals(0, calendar.daysLate("2026-06-10", "2026-06-10T09:00:00+02:00"));
        assertEquals(0, calendar.daysLate("2026-06-10", "2026-06-10T23:59:59+02:00"));
        assertEquals(1, calendar.daysLate("2026-06-10", "2026-06-11T00:00:00+02:00"));
        assertEquals(5, calendar.daysLate("2026-06-10", "2026-06-15T10:00:00+02:00"));
    }

    @Test
    @DisplayName("adding months")
    void addingMonths() {
        assertEquals("2026-02-15", calendar.addMonths("2026-01-15", 1));
        assertEquals("2026-06-15", calendar.addMonths("2026-06-15", 0));
        assertEquals("2027-06-15", calendar.addMonths("2026-06-15", 12));
        assertEquals("2026-02-28", calendar.addMonths("2026-03-31", -1));
    }

    @Test
    @DisplayName("a day that does not exist in a short month falls back to its last day")
    void shortMonthsClamp() {
        assertEquals("2026-02-28", calendar.addMonths("2026-01-31", 1));
        assertEquals("2026-02-28", calendar.addMonths("2026-01-30", 1));
        assertEquals("2024-02-29", calendar.addMonths("2024-01-31", 1));
    }

    @Test
    @DisplayName("garbage is refused")
    void garbageIsRefused() {
        for (String bad : new String[] {null, "", "tomorrow", "2026/03/25"}) {
            assertThrows(IllegalArgumentException.class, () -> calendar.isBusinessDay(bad));
            assertThrows(IllegalArgumentException.class, () -> calendar.addMonths(bad, 1));
        }
        for (String bad : new String[] {null, "", "yesterday", "2026-03-25",
                "2026-03-25T10:00:00"}) {
            assertThrows(IllegalArgumentException.class, () -> calendar.processingDate(bad));
            assertThrows(IllegalArgumentException.class, () -> calendar.settlementDate(bad));
            assertThrows(IllegalArgumentException.class,
                    () -> calendar.daysLate("2026-06-10", bad));
        }
    }
}
