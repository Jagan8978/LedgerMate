package com.ledgermate.util;

import java.util.Locale;

public final class Text {

    private Text() {}

    /** Trims; returns null when the result is empty. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static boolean containsIgnoreCase(String haystack, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
