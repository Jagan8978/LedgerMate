package com.ledgermate.service;

import com.ledgermate.entity.TransactionType;
import com.ledgermate.util.Money;
import java.math.BigDecimal;
import java.util.List;

/** Credits, debits and entry count read from the database with aggregate queries. */
public record LedgerTotals(BigDecimal credits, BigDecimal debits, long count) {

    public static final LedgerTotals EMPTY = new LedgerTotals(Money.ZERO, Money.ZERO, 0);

    /** Parses rows shaped [TransactionType, sum(amount), count]. */
    public static LedgerTotals of(List<Object[]> rows) {
        BigDecimal credits = Money.ZERO;
        BigDecimal debits = Money.ZERO;
        long count = 0;
        for (Object[] row : rows) {
            TransactionType type = (TransactionType) row[0];
            BigDecimal sum = Money.scale(new BigDecimal(row[1].toString()));
            count += ((Number) row[2]).longValue();
            if (type == TransactionType.CREDIT) {
                credits = credits.add(sum);
            } else {
                debits = debits.add(sum);
            }
        }
        return new LedgerTotals(credits, debits, count);
    }

    public BigDecimal net() {
        return credits.subtract(debits);
    }
}
