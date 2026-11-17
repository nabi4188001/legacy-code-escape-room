package com.rustypayments;

/**
 * The payments calendar of Rusty Bank: when a payment is processed, when it settles, how
 * late it is, and how to count months.
 *
 * <p><b>LOCKED FILE.</b> This interface is the contract the room is tested through. An
 * escaping agent may rewrite anything behind it, but may not change this file.
 *
 * <h2>Formats</h2>
 * A <i>date</i> is exactly {@code yyyy-MM-dd}, e.g. {@code 2026-03-27}, and must be a real
 * calendar date: {@code 2026-02-30} is not a date. An <i>instant</i> is ISO-8601 to the
 * second with an offset, e.g. {@code 2026-03-27T16:45:00+01:00} or
 * {@code 2026-03-27T15:45:00Z}. An instant without an offset is ambiguous and is refused.
 * A null, or anything not in these formats, throws {@link IllegalArgumentException}.
 *
 * <h2>Bank time</h2>
 * Every decision is made in the bank's time zone, {@code Europe/Warsaw}, summer time
 * included, whatever time zone or locale the server happens to run in.
 *
 * <h2>Business days</h2>
 * Monday to Friday, except bank holidays. The bank observes only these fixed-date
 * holidays: 1 Jan, 6 Jan, 1 May, 3 May, 15 Aug, 1 Nov, 11 Nov, 25 Dec and 26 Dec. A holiday
 * that falls on a weekend is not moved to another day.
 *
 * <h2>Threads</h2>
 * One calendar instance may be used by many threads at the same time.
 */
public interface PaymentCalendar {

    /** Whether the date is a business day. */
    boolean isBusinessDay(String date);

    /**
     * The day the bank starts processing a payment submitted at the given instant.
     *
     * <p>Convert the instant to bank time. If that local date is a business day and the
     * local time is 16:00:00 or earlier, the payment is processed that same date. A payment
     * at 16:00:01, or on a non-business day, is processed on the next business day after
     * that local date.
     */
    String processingDate(String submittedAt);

    /**
     * The day the money arrives: the first business day strictly after the processing
     * date of the same instant.
     */
    String settlementDate(String submittedAt);

    /**
     * How many calendar days late a payment is: the number of days, in bank time, from the
     * due date to the local date on which the payment was made. Paying at any time on the
     * due date itself is not late (0). Paying early is also 0, never negative.
     */
    int daysLate(String dueDate, String paidAt);

    /**
     * The date a number of months later (or earlier, for a negative number).
     *
     * <p>The day of the month is kept where the target month has it, otherwise the last
     * day of the target month is used. Month ends stay month ends: if the starting date is
     * the last day of its month, the result is the last day of the target month.
     */
    String addMonths(String date, int months);
}
