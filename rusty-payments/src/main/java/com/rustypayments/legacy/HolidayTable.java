package com.rustypayments.legacy;

import java.util.Calendar;

/**
 * BANK HOLIDAYS (fixed dates only)
 * CHG 02.01.2005 - added 3 May after branch 0017 stayed open
 */
public class HolidayTable {

    /** Month is 0-based, the way Calendar wants it. */
    private static final int[][] HOLIDAYS = {
        {0, 1},    // New Year
        {4, 1},    // Labour Day
        {4, 3},    // Constitution Day
        {7, 15},   // Assumption
        {10, 1},   // All Saints
        {10, 11},  // Independence Day
        {11, 25},  // Christmas
        {11, 26}   // Boxing Day
    };

    public static boolean isHoliday(Calendar day) {
        int month = day.get(Calendar.MONTH);
        int date = day.get(Calendar.DAY_OF_MONTH);
        for (int i = 0; i < HOLIDAYS.length; i++) {
            if (HOLIDAYS[i][0] == month && HOLIDAYS[i][1] == date) {
                return true;
            }
        }
        return false;
    }
}
