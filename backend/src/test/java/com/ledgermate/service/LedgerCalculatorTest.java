package com.ledgermate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ledgermate.entity.TransactionType;
import com.ledgermate.service.LedgerCalculator.Posting;
import com.ledgermate.service.LedgerCalculator.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerCalculatorTest {

    private static Posting posting(TransactionType type, String amount, String at, String createdAt, UUID id) {
        return new Posting(id, type, new BigDecimal(amount), Instant.parse(at), Instant.parse(createdAt));
    }

    private static Posting posting(TransactionType type, String amount, String at) {
        return posting(type, amount, at, at, UUID.randomUUID());
    }

    /** The worked example from the specification. */
    private static List<Posting> specExample() {
        return List.of(
                posting(TransactionType.CREDIT, "5000", "2026-10-22T04:30:00Z"),  // from Ravi
                posting(TransactionType.DEBIT, "1500", "2026-10-22T05:30:00Z"),   // to Suresh
                posting(TransactionType.CREDIT, "2000", "2026-10-23T04:30:00Z"),  // from Kumar
                posting(TransactionType.DEBIT, "500", "2026-10-23T09:30:00Z"));   // to Ravi
    }

    @Test
    void creditIsPositiveAndDebitIsNegative() {
        assertEquals(new BigDecimal("1000.00"), LedgerCalculator.signed(TransactionType.CREDIT, new BigDecimal("1000")));
        assertEquals(new BigDecimal("-1000.00"), LedgerCalculator.signed(TransactionType.DEBIT, new BigDecimal("1000")));
    }

    @Test
    void specExampleRunningBalancesAre5000_3500_5500_5000() {
        List<Row> rows = LedgerCalculator.runningBalances(BigDecimal.ZERO, specExample());
        assertEquals(List.of(new BigDecimal("5000.00"), new BigDecimal("3500.00"),
                        new BigDecimal("5500.00"), new BigDecimal("5000.00")),
                rows.stream().map(Row::runningBalance).toList());
    }

    @Test
    void specExampleTotalsGiveBalance5000() {
        BigDecimal credits = new BigDecimal("7000.00");
        BigDecimal debits = new BigDecimal("2000.00");
        assertEquals(new BigDecimal("5000.00"), LedgerCalculator.currentBalance(BigDecimal.ZERO, credits, debits));
    }

    @Test
    void openingBalanceIsIncludedInEveryRunningBalance() {
        List<Row> rows = LedgerCalculator.runningBalances(new BigDecimal("1000.00"), specExample());
        assertEquals(new BigDecimal("6000.00"), rows.get(0).runningBalance());
        assertEquals(new BigDecimal("6000.00"), rows.get(3).runningBalance());
        assertEquals(new BigDecimal("6000.00"),
                LedgerCalculator.currentBalance(new BigDecimal("1000.00"), new BigDecimal("7000.00"), new BigDecimal("2000.00")));
    }

    @Test
    void balanceCanGoNegative() {
        List<Row> rows = LedgerCalculator.runningBalances(BigDecimal.ZERO,
                List.of(posting(TransactionType.DEBIT, "2000", "2026-01-01T00:00:00Z")));
        assertEquals(new BigDecimal("-2000.00"), rows.get(0).runningBalance());
    }

    @Test
    void inputOrderDoesNotMatterRowsAreChronological() {
        List<Posting> shuffled = List.of(specExample().get(3), specExample().get(0), specExample().get(2), specExample().get(1));
        List<Row> rows = LedgerCalculator.runningBalances(BigDecimal.ZERO, shuffled);
        assertEquals(List.of(new BigDecimal("5000.00"), new BigDecimal("3500.00"),
                        new BigDecimal("5500.00"), new BigDecimal("5000.00")),
                rows.stream().map(Row::runningBalance).toList());
    }

    @Test
    void sameTimestampIsOrderedByCreatedAtThenId() {
        String at = "2026-02-01T10:00:00Z";
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Posting laterCreated = posting(TransactionType.DEBIT, "40", at, "2026-02-01T10:00:02Z", low);
        Posting earlierCreated = posting(TransactionType.CREDIT, "100", at, "2026-02-01T10:00:01Z", high);

        List<Row> byCreated = LedgerCalculator.runningBalances(BigDecimal.ZERO, List.of(laterCreated, earlierCreated));
        assertEquals(earlierCreated.id(), byCreated.get(0).posting().id());
        assertEquals(new BigDecimal("60.00"), byCreated.get(1).runningBalance());

        Posting a = posting(TransactionType.CREDIT, "10", at, at, low);
        Posting b = posting(TransactionType.CREDIT, "20", at, at, high);
        List<Row> byId = LedgerCalculator.runningBalances(BigDecimal.ZERO, List.of(b, a));
        assertEquals(low, byId.get(0).posting().id());
        assertEquals(new BigDecimal("10.00"), byId.get(0).runningBalance());
        assertEquals(new BigDecimal("30.00"), byId.get(1).runningBalance());
    }

    @Test
    void reversalNetsTheOriginalToZero() {
        List<Row> rows = LedgerCalculator.runningBalances(BigDecimal.ZERO, List.of(
                posting(TransactionType.CREDIT, "1000.00", "2026-03-01T10:00:00Z"),
                posting(TransactionType.DEBIT, "1000.00", "2026-03-01T11:00:00Z")));
        assertEquals(new BigDecimal("1000.00"), rows.get(0).runningBalance());
        assertEquals(new BigDecimal("0.00"), rows.get(1).runningBalance());
    }

    @Test
    void decimalAmountsAreExact() {
        List<Row> rows = LedgerCalculator.runningBalances(BigDecimal.ZERO, List.of(
                posting(TransactionType.CREDIT, "0.10", "2026-03-01T10:00:00Z"),
                posting(TransactionType.CREDIT, "0.20", "2026-03-01T11:00:00Z")));
        // 0.1 + 0.2 is 0.30000000000000004 with doubles; with BigDecimal it is exactly 0.30
        assertEquals(new BigDecimal("0.30"), rows.get(1).runningBalance());
    }
}
