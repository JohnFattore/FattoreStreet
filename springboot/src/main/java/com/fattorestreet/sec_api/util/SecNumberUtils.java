package com.fattorestreet.sec_api.util;

public final class SecNumberUtils {

    private SecNumberUtils() {
    }

    public static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public static Double parsePositiveDouble(String text) {
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    public static boolean roughlyEqual(Double left, double right, double epsilon) {
        if (left == null) {
            return false;
        }
        return Math.abs(left - right) <= epsilon;
    }
}
