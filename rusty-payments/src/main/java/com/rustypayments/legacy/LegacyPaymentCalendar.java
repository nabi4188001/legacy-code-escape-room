package com.rustypayments.legacy;

import com.rustypayments.PaymentCalendar;

import java.util.Calendar;
import java.util.Date;

/**
 * PAYMENT CALENDAR
 * CREATED 03.05.2002
 * CHG 30.08.2008 - cut-off moved from 15:00 to 16:00
 * CHG 09.09.2009 - migrated from the green screens, logic unchanged
 */
public class LegacyPaymentCalendar implements PaymentCalendar {

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    public boolean isBusinessDay(String date) {
        Calendar c = Calendar.getInstance();
        c.setTime(DateUtil.parseDay(date));
        return isBusinessDay(c);
    }

    public String processingDate(String submittedAt) {
        return DateUtil.formatDay(processing(submittedAt).getTime());
    }

    public String settlementDate(String submittedAt) {
        Calendar processing = processing(submittedAt);
        return DateUtil.formatDay(nextBusinessDay(processing).getTime());
    }

    public int daysLate(String dueDate, String paidAt) {
        Date due = DateUtil.parseDay(dueDate);
        Date paid = DateUtil.parseInstant(paidAt);
        long late = (paid.getTime() - due.getTime()) / DAY_MILLIS;
        return late < 0 ? 0 : (int) late;
    }

    public String addMonths(String date, int months) {
        Calendar c = Calendar.getInstance();
        c.setTime(DateUtil.parseDay(date));
        c.add(Calendar.MONTH, months);
        return DateUtil.formatDay(c.getTime());
    }

    private Calendar processing(String submittedAt) {
        Calendar c = Calendar.getInstance();
        c.setTime(DateUtil.parseInstant(submittedAt));
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        boolean inTime = hour < 16 || (hour == 16 && minute == 0);
        if (isBusinessDay(c) && inTime) {
            return c;
        }
        return nextBusinessDay(c);
    }

    private Calendar nextBusinessDay(Calendar from) {
        Calendar c = (Calendar) from.clone();
        do {
            c.add(Calendar.DATE, 1);
        } while (!isBusinessDay(c));
        return c;
    }

    private boolean isBusinessDay(Calendar c) {
        int dayOfWeek = c.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return false;
        }
        return !HolidayTable.isHoliday(c);
    }
}
