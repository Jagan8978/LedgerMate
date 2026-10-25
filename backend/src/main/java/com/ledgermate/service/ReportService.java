package com.ledgermate.service;

import com.ledgermate.dto.ReportDtos.MonthlySummary;
import com.ledgermate.dto.ReportDtos.ReportSummaryResponse;
import com.ledgermate.dto.ReportDtos.StatementResponse;
import com.ledgermate.dto.TransactionDtos.TransactionResponse;
import com.ledgermate.entity.LedgerTransaction;
import com.ledgermate.entity.Person;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.exception.BusinessRuleException;
import com.ledgermate.exception.NotFoundException;
import com.ledgermate.report.CsvExporter;
import com.ledgermate.repository.LedgerTransactionRepository;
import com.ledgermate.repository.PersonRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.LedgerCalculator.Posting;
import com.ledgermate.service.LedgerCalculator.Row;
import com.ledgermate.util.Money;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Period summaries, statements and CSV export. Totals come from database aggregate queries. */
@Service
public class ReportService {

    private static final Instant MIN = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("2999-12-31T00:00:00Z");
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record CsvFile(String filename, byte[] content) {}

    private final PersonRepository people;
    private final LedgerTransactionRepository txs;
    private final TransactionService transactions;
    private final ZoneResolver zones;
    private final AuditService audit;

    public ReportService(PersonRepository people, LedgerTransactionRepository txs,
                         TransactionService transactions, ZoneResolver zones, AuditService audit) {
        this.people = people;
        this.txs = txs;
        this.transactions = transactions;
        this.zones = zones;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary(AuthenticatedUser user, UUID personId, LocalDate from, LocalDate to) {
        return buildSummary(user, owned(user, personId), from, to);
    }

    @Transactional(readOnly = true)
    public StatementResponse statement(AuthenticatedUser user, UUID personId, TransactionQuery q) {
        Person person = owned(user, personId);
        ReportSummaryResponse summary = buildSummary(user, person, q.from(), q.to());
        List<TransactionResponse> rows = transactions.listAll(user, personId, q);
        BigDecimal credits = Money.ZERO;
        BigDecimal debits = Money.ZERO;
        for (TransactionResponse r : rows) {
            BigDecimal amount = new BigDecimal(r.amount());
            if (r.type() == TransactionType.CREDIT) {
                credits = credits.add(amount);
            } else {
                debits = debits.add(amount);
            }
        }
        return new StatementResponse(summary, Money.format(credits), Money.format(debits), rows.size(), rows);
    }

    /** Export is audited, so this method needs a (writable) transaction. */
    @Transactional
    public CsvFile exportCsv(AuthenticatedUser user, UUID personId, TransactionQuery q) {
        Person person = owned(user, personId);
        ZoneId zone = zones.zoneOf(user.id());
        List<TransactionResponse> rows = transactions.listAll(user, personId, q);

        List<CsvExporter.Line> lines = new ArrayList<>(rows.size());
        for (TransactionResponse r : rows) {
            UUID related = r.reversalOfTransactionId() != null ? r.reversalOfTransactionId() : r.correctionReferenceId();
            lines.add(new CsvExporter.Line(
                    r.transactionAt().atZone(zone).format(CSV_DATE), r.type().name(), r.amount(),
                    r.signedAmount(), r.counterpartyName(), r.description(), r.referenceNumber(),
                    r.runningBalance(), r.entryKind().name(), related == null ? null : related.toString(),
                    r.reason()));
        }
        String csv = CsvExporter.build(zone.getId(), lines);
        byte[] bytes = ("﻿" + csv).getBytes(StandardCharsets.UTF_8); // BOM so Excel reads UTF-8

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("rows", rows.size());
        audit.record(user, "PERSON", person.getId(), "EXPORT_CSV", null, info, null);
        return new CsvFile("ledger-" + slug(person.getName()) + "-" + LocalDate.now(zone) + ".csv", bytes);
    }

    // ------------------------------------------------------------------ internals

    private Person owned(AuthenticatedUser user, UUID personId) {
        return people.findByIdAndOwnerId(personId, user.id())
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
    }

    private ReportSummaryResponse buildSummary(AuthenticatedUser user, Person person, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The 'from' date must not be after the 'to' date.");
        }
        ZoneId zone = zones.zoneOf(user.id());
        Instant fromInstant = from == null ? MIN : from.atStartOfDay(zone).toInstant();
        Instant toExclusive = to == null ? MAX : to.plusDays(1).atStartOfDay(zone).toInstant();

        LedgerTotals before = LedgerTotals.of(txs.totalsByTypeBetween(person.getId(), person.getOwnerId(), MIN, fromInstant));
        LedgerTotals during = LedgerTotals.of(txs.totalsByTypeBetween(person.getId(), person.getOwnerId(), fromInstant, toExclusive));
        LedgerTotals all = LedgerTotals.of(txs.totalsByType(person.getId(), person.getOwnerId()));

        BigDecimal periodOpening = LedgerCalculator.currentBalance(person.getOpeningBalance(), before.credits(), before.debits());
        BigDecimal closing = LedgerCalculator.currentBalance(periodOpening, during.credits(), during.debits());
        BigDecimal current = LedgerCalculator.currentBalance(person.getOpeningBalance(), all.credits(), all.debits());

        return new ReportSummaryResponse(from, to, zone.getId(), Money.format(periodOpening),
                Money.format(during.credits()), Money.format(during.debits()), Money.format(closing),
                during.count(), Money.format(current), monthly(person, fromInstant, toExclusive, zone));
    }

    private static final class MonthAcc {
        final BigDecimal opening;
        BigDecimal credits = Money.ZERO;
        BigDecimal debits = Money.ZERO;
        BigDecimal closing;
        long count;

        MonthAcc(BigDecimal opening) {
            this.opening = opening;
            this.closing = opening;
        }
    }

    /** One entry per calendar month (user's timezone) that has activity inside the period. */
    private List<MonthlySummary> monthly(Person person, Instant from, Instant toExclusive, ZoneId zone) {
        List<LedgerTransaction> all = txs.findByPersonIdAndOwnerId(person.getId(), person.getOwnerId());
        List<Posting> postings = new ArrayList<>(all.size());
        for (LedgerTransaction t : all) {
            postings.add(new Posting(t.getId(), t.getTransactionType(), t.getAmount(), t.getTransactionAt(), t.getCreatedAt()));
        }
        Map<YearMonth, MonthAcc> months = new TreeMap<>();
        for (Row r : LedgerCalculator.runningBalances(person.getOpeningBalance(), postings)) {
            Instant at = r.posting().at();
            if (at.isBefore(from) || !at.isBefore(toExclusive)) {
                continue;
            }
            YearMonth ym = YearMonth.from(at.atZone(zone));
            MonthAcc acc = months.computeIfAbsent(ym, k -> new MonthAcc(r.runningBalance().subtract(r.signedAmount())));
            if (r.posting().type() == TransactionType.CREDIT) {
                acc.credits = acc.credits.add(r.posting().amount());
            } else {
                acc.debits = acc.debits.add(r.posting().amount());
            }
            acc.closing = r.runningBalance();
            acc.count++;
        }
        List<MonthlySummary> result = new ArrayList<>();
        months.forEach((ym, a) -> result.add(new MonthlySummary(ym.toString(), Money.format(a.opening),
                Money.format(a.credits), Money.format(a.debits), Money.format(a.closing), a.count)));
        return result;
    }

    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.isEmpty()) {
            return "ledger";
        }
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
