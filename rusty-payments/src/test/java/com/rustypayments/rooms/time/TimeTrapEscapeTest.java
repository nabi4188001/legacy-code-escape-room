package com.rustypayments.rooms.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rustypayments.PaymentCalendar;
import com.rustypayments.RustyPayments;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * ROOM 2 - THE TIME TRAP - the lock on the door.
 *
 * <p><b>LOCKED FILE.</b> Every test here fails against the legacy code, which trusts the
 * clock of the machine it runs on and treats every day as 24 hours long. The room is
 * escaped when all of them pass and the golden master is still green.
 */
@Tag("escape")
@DisplayName("Time Trap: the days that go wrong")
class TimeTrapEscapeTest {

    private static final String[] SERVER_ZONES =
            {"Europe/Warsaw", "UTC", "America/New_York", "Asia/Tokyo", "Pacific/Auckland"};

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
    @DisplayName("the cut-off is 16:00:00 exactly, so 16:00:01 is already tomorrow")
    void cutOffIsToTheSecond() {
        assertEquals("2026-03-25", calendar.processingDate("2026-03-25T16:00:00+01:00"));
        assertEquals("2026-03-26", calendar.processingDate("2026-03-25T16:00:01+01:00"));
        assertEquals("2026-03-26", calendar.processingDate("2026-03-25T16:00:59+01:00"));
    }

    @Test
    @DisplayName("the cut-off is Warsaw time, wherever the server is")
    void cutOffDoesNotDependOnTheServerZone() {
        for (String zone : SERVER_ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            // Winter: 15:30 UTC is 16:30 in Warsaw, too late for Friday.
            assertEquals("2026-03-30", calendar.processingDate("2026-03-27T15:30:00Z"), zone);
            // Winter: 14:30 UTC is 15:30 in Warsaw, in time.
            assertEquals("2026-03-27", calendar.processingDate("2026-03-27T14:30:00Z"), zone);
            // Summer: 14:00:00 UTC is 16:00:00 in Warsaw, 14:00:01 is not.
            assertEquals("2026-07-01", calendar.processingDate("2026-07-01T14:00:00Z"), zone);
            assertEquals("2026-07-02", calendar.processingDate("2026-07-01T14:00:01Z"), zone);
            // 23:30 UTC on Thursday is already half past midnight on Friday in Warsaw.
            assertEquals("2026-03-27", calendar.processingDate("2026-03-26T23:30:00Z"), zone);
        }
    }

    @Test
    @DisplayName("6 January is a bank holiday")
    void epiphanyIsABankHoliday() {
        assertFalse(calendar.isBusinessDay("2026-01-06"));
        assertEquals("2026-01-07", calendar.processingDate("2026-01-06T10:00:00+01:00"));
        assertEquals("2026-01-07", calendar.settlementDate("2026-01-05T10:00:00+01:00"));
    }

    @Test
    @DisplayName("settlement over Christmas and into the new year")
    void settlementAtTheEndOfTheYear() {
        // 25 and 26 December are holidays, then a weekend.
        assertEquals("2026-12-28", calendar.settlementDate("2026-12-24T10:00:00+01:00"));
        assertEquals("2026-12-30", calendar.settlementDate("2026-12-29T10:00:00+01:00"));
        // 1 January is a holiday, then a weekend.
        assertEquals("2027-01-04", calendar.settlementDate("2026-12-31T10:00:00+01:00"));
        assertEquals("2026-12-30", calendar.processingDate("2026-12-30T10:00:00+01:00"));
    }

    @Test
    @DisplayName("days late are calendar days, even when the clocks change")
    void daysLateThroughTheClockChange() {
        // Clocks go forward on Sunday 29 March 2026: that day has 23 hours.
        assertEquals(3, calendar.daysLate("2026-03-27", "2026-03-30T00:30:00+02:00"));
        assertEquals(2, calendar.daysLate("2026-03-28", "2026-03-30T00:15:00+02:00"));
        // Clocks go back on Sunday 25 October 2026: that day has 25 hours.
        assertEquals(1, calendar.daysLate("2026-10-24", "2026-10-25T23:30:00+01:00"));
        assertEquals(2, calendar.daysLate("2026-10-24", "2026-10-26T00:10:00+01:00"));
    }

    @Test
    @DisplayName("days late are counted in Warsaw time, wherever the server is")
    void daysLateDoesNotDependOnTheServerZone() {
        for (String zone : SERVER_ZONES) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            // 23:00 UTC on 10 June is 01:00 on 11 June in Warsaw: one day late.
            assertEquals(1, calendar.daysLate("2026-06-10", "2026-06-10T23:00:00Z"), zone);
            // 21:30 UTC is 23:30 in Warsaw, still the due date.
            assertEquals(0, calendar.daysLate("2026-06-10", "2026-06-10T21:30:00Z"), zone);
        }
    }

    @Test
    @DisplayName("a month end stays a month end")
    void monthEndStaysMonthEnd() {
        assertEquals("2026-03-31", calendar.addMonths("2026-02-28", 1));
        assertEquals("2026-05-31", calendar.addMonths("2026-04-30", 1));
        assertEquals("2026-04-30", calendar.addMonths("2026-03-31", 1));
        assertEquals("2024-03-31", calendar.addMonths("2024-02-29", 1));
        assertEquals("2026-11-30", calendar.addMonths("2026-02-28", 9));
        // 28 February 2024 is not a month end, the leap day is.
        assertEquals("2024-03-28", calendar.addMonths("2024-02-28", 1));
    }

    @Test
    @DisplayName("dates that do not exist, or are not written yyyy-MM-dd, are refused")
    void impossibleDatesAreRefused() {
        String[] bad = {"2026-02-30", "2026-02-29", "2026-04-31", "2026-13-01", "2026-00-10",
                "25-03-2026", "2026-3-5"};
        for (String date : bad) {
            assertThrows(IllegalArgumentException.class, () -> calendar.isBusinessDay(date), date);
            assertThrows(IllegalArgumentException.class, () -> calendar.addMonths(date, 1), date);
            assertThrows(IllegalArgumentException.class,
                    () -> calendar.daysLate(date, "2026-06-10T10:00:00+02:00"), date);
        }
    }

    @Test
    @DisplayName("many threads at once get the right answers")
    void manyThreadsAtOnce() throws Exception {
        String[][] cases = {
            {"processing", "2026-03-25T10:15:00+01:00", "2026-03-25"},
            {"processing", "2026-03-25T17:30:00+01:00", "2026-03-26"},
            {"processing", "2026-03-27T17:00:00+01:00", "2026-03-30"},
            {"settlement", "2026-03-27T10:00:00+01:00", "2026-03-30"},
            {"settlement", "2026-04-30T10:00:00+02:00", "2026-05-04"},
            {"addMonths", "2026-01-15", "2026-02-15"},
            {"addMonths", "2026-06-15", "2026-07-15"},
        };
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> outcomes = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Callable<String> worker = () -> {
                go.await();
                for (int round = 0; round < 3000; round++) {
                    for (String[] c : cases) {
                        String got;
                        try {
                            got = switch (c[0]) {
                                case "processing" -> calendar.processingDate(c[1]);
                                case "settlement" -> calendar.settlementDate(c[1]);
                                default -> calendar.addMonths(c[1], 1);
                            };
                        } catch (Throwable e) {
                            return c[0] + "(" + c[1] + ") threw " + e;
                        }
                        if (!c[2].equals(got)) {
                            return c[0] + "(" + c[1] + ") gave " + got + ", expected " + c[2];
                        }
                    }
                }
                return null;
            };
            outcomes.add(pool.submit(worker));
        }
        go.countDown();
        List<String> problems = new ArrayList<>();
        try {
            for (Future<String> outcome : outcomes) {
                String problem = outcome.get(120, TimeUnit.SECONDS);
                if (problem != null) {
                    problems.add(problem);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(List.of(), problems.stream().limit(3).toList());
    }
}
