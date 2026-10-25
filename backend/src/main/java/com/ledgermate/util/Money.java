package com.ledgermate.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money helpers. All monetary maths in the application uses BigDecimal with scale 2. */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {}

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Plain (never scientific) two-decimal string, e.g. "5000.00" or "-1500.00". */
    public static String format(BigDecimal value) {
        return scale(value).toPlainString();
    }
}
