package com.mcgee.smp.economy;

import java.util.Locale;

/** Formatting and parsing for money amounts, e.g. "$1,250" and "1.5k". */
public final class Money {
    private Money() {}

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static String format(double v) {
        v = round(v);
        if (v == Math.floor(v)) return "$" + String.format(Locale.US, "%,.0f", v);
        return "$" + String.format(Locale.US, "%,.2f", v);
    }

    /** Accepts 250, 2,500, $3000, 1.5k, 2m, 1b. Returns -1 when it can't be read. */
    public static double parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "");
        if (s.isEmpty()) return -1;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') mult = 1_000;
        else if (last == 'm') mult = 1_000_000;
        else if (last == 'b') mult = 1_000_000_000;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return -1;
            return round(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
