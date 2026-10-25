package com.ledgermate.service;

import com.ledgermate.entity.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure accounting rules (no Spring, no database) so they can be tested in isolation.
 *
 * <pre>
 *   signed(CREDIT, x) = +x          signed(DEBIT, x) = -x
 *   Current Balance   = Opening Balance + Total Credits - Total Debits
 *   Running balance   = Opening Balance + sum of signed amounts up to and including the entry,
 *                       in chronological order (date/time, then created time, then id)
 * </pre>
 */
public final class LedgerCalculator {

    private static final int SCALE = 2;

    /** Minimal view of a posted entry that the accounting rules need. */
    public record Posting(UUID id, TransactionType type, BigDecimal amount, Instant at, Instant createdAt) {}

    public record Row(Posting posting, BigDecimal signedAmount, BigDecimal runningBalance) {}

    /** Stable chronological ordering: transaction time, then creation time, then unique id. */
    public static final Comparator<Posting> CHRONOLOGICAL = Comparator
            .comparing(Posting::at)
            .thenComparing(Posting::createdAt)
            .thenComparing(Posting::id);

    private LedgerCalculator() {}

    public static BigDecimal signed(TransactionType type, BigDecimal amount) {
        BigDecimal scaled = amount.setScale(SCALE, RoundingMode.HALF_UP);
        return type == TransactionType.CREDIT ? scaled : scaled.negate();
    }

    public static BigDecimal currentBalance(BigDecimal opening, BigDecimal credits, BigDecimal debits) {
        return opening.add(credits).subtract(debits).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Rows in chronological order, each carrying the balance after that entry. */
    public static List<Row> runningBalances(BigDecimal opening, List<Posting> postings) {
        List<Posting> ordered = new ArrayList<>(postings);
        ordered.sort(CHRONOLOGICAL);
        BigDecimal running = opening.setScale(SCALE, RoundingMode.HALF_UP);
        List<Row> rows = new ArrayList<>(ordered.size());
        for (Posting p : ordered) {
            BigDecimal signed = signed(p.type(), p.amount());
            running = running.add(signed);
            rows.add(new Row(p, signed, running));
        }
        return rows;
    }
}
