package com.example.sec_api.util;

import java.time.LocalDate;
import java.util.*;

public class QuarterUtils {
    public static List<LocalDate> getLast3Quarters(LocalDate period) {
        List<LocalDate> last3Quarters = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            last3Quarters.add(period.minusMonths(3L * i).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth()));
        }
        return last3Quarters;
    }

    public static LocalDate parseQuarter(String quarterStr) {
        // Keeps existing logic for 2024-Q1 format if still used elsewhere
        String yearPart = quarterStr.substring(2, 6);
        int year = Integer.parseInt(yearPart);
        int q = Integer.parseInt(quarterStr.substring(7, 8));

        int month = q * 3;
        return LocalDate.of(year, month, 1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
    }

    public static LocalDate nextQuarter(LocalDate current) {
        return current.plusMonths(3).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
    }

    public static LocalDate standardizeStartDate(LocalDate date) {
        // Enforce always being the 1st of the month
        return date.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth());
    }

    public static LocalDate standardizeDate(LocalDate date) {
        // Simply snap to the end of the month to consolidate tags within the same
        // filing period.
        // We no longer force dates to 3/31, 6/30, 9/30, 12/31.
        // This supports NVDA (ends in Jan/Apr/July/Oct) and other non-standard cycles.
        return date.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
    }
}