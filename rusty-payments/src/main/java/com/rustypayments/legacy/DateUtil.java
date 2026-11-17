package com.rustypayments.legacy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * DATE HELPERS
 * CREATED 03.05.2002
 * CHG 21.11.2006 - formatters made static, "was allocating thousands of them in the nightly run"
 * CHG 14.01.2011 - YYYY for the year, as the new standard asks
 */
public class DateUtil {

    private static final SimpleDateFormat DAY_IN = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DAY_OUT = new SimpleDateFormat("YYYY-MM-dd");
    private static final SimpleDateFormat INSTANT_IN =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    public static Date parseDay(String text) {
        if (text == null) {
            throw new IllegalArgumentException("INVALID DATE");
        }
        try {
            DAY_IN.setTimeZone(TimeZone.getDefault());
            return DAY_IN.parse(text.trim());
        } catch (ParseException e) {
            throw new IllegalArgumentException("INVALID DATE: " + text);
        }
    }

    public static Date parseInstant(String text) {
        if (text == null) {
            throw new IllegalArgumentException("INVALID TIMESTAMP");
        }
        try {
            INSTANT_IN.setTimeZone(TimeZone.getDefault());
            return INSTANT_IN.parse(text.trim());
        } catch (ParseException e) {
            throw new IllegalArgumentException("INVALID TIMESTAMP: " + text);
        }
    }

    public static String formatDay(Date date) {
        DAY_OUT.setTimeZone(TimeZone.getDefault());
        return DAY_OUT.format(date);
    }
}
