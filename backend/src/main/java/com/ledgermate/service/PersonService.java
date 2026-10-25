package com.ledgermate.service;

import com.ledgermate.dto.PersonDtos.DuplicateCheckResponse;
import com.ledgermate.dto.PersonDtos.LedgerSummaryResponse;
import com.ledgermate.dto.PersonDtos.OverviewResponse;
import com.ledgermate.dto.PersonDtos.PersonRequest;
import com.ledgermate.dto.PersonDtos.PersonResponse;
import com.ledgermate.entity.Person;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.exception.BusinessRuleException;
import com.ledgermate.exception.NotFoundException;
import com.ledgermate.repository.LedgerTransactionRepository;
import com.ledgermate.repository.PersonRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.util.Money;
import com.ledgermate.util.Text;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** People (ledgers) management and balance summaries. Every method is scoped to the caller. */
@Service
public class PersonService {

    private final PersonRepository people;
    private final LedgerTransactionRepository txs;
    private final AuditService audit;

    public PersonService(PersonRepository people, LedgerTransactionRepository txs, AuditService audit) {
        this.people = people;
        this.txs = txs;
        this.audit = audit;
    }

    @Transactional
    public PersonResponse create(AuthenticatedUser user, PersonRequest req) {
        String name = Text.clean(req.name());
        if (name == null) {
            throw new BusinessRuleException("Name is required.");
        }
        Person person = new Person(user.id(), name);
        apply(person, req);
        people.save(person);
        audit.record(user, "PERSON", person.getId(), "CREATE", null, snapshot(person), null);
        return toResponse(person, LedgerTotals.EMPTY, null);
    }

    @Transactional(readOnly = true)
    public List<PersonResponse> list(AuthenticatedUser user, String status) {
        List<Person> found = switch (status == null ? "active" : status.toLowerCase()) {
            case "active" -> people.findByOwnerIdAndArchivedAtIsNullOrderByNameAsc(user.id());
            case "archived" -> people.findByOwnerIdAndArchivedAtIsNotNullOrderByNameAsc(user.id());
            case "all" -> people.findByOwnerIdOrderByNameAsc(user.id());
            default -> throw new BusinessRuleException("status must be active, archived or all.");
        };
        Map<UUID, Aggregate> aggregates = aggregatesByPerson(user.id());
        List<PersonResponse> result = new ArrayList<>(found.size());
        for (Person p : found) {
            Aggregate a = aggregates.get(p.getId());
            result.add(a == null ? toResponse(p, LedgerTotals.EMPTY, null)
                    : toResponse(p, new LedgerTotals(a.credits, a.debits, a.count), a.last));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PersonResponse get(AuthenticatedUser user, UUID personId) {
        Person person = owned(user, personId);
        return toResponse(person, totals(person), txs.lastTransactionAt(person.getId(), user.id()));
    }

    @Transactional
    public PersonResponse update(AuthenticatedUser user, UUID personId, PersonRequest req) {
        Person person = people.findForUpdate(personId, user.id())
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
        String name = Text.clean(req.name());
        if (name == null) {
            throw new BusinessRuleException("Name is required.");
        }
        Map<String, Object> before = snapshot(person);
        apply(person, req);
        people.save(person);
        audit.record(user, "PERSON", person.getId(), "UPDATE", before, snapshot(person), null);
        return toResponse(person, totals(person), txs.lastTransactionAt(person.getId(), user.id()));
    }

    /** Archiving hides the ledger from the dashboard; financial history is never deleted. */
    @Transactional
    public PersonResponse setArchived(AuthenticatedUser user, UUID personId, boolean archive) {
        Person person = people.findForUpdate(personId, user.id())
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
        if (person.isArchived() != archive) {
            person.setArchivedAt(archive ? Instant.now() : null);
            people.save(person);
            audit.record(user, "PERSON", person.getId(), archive ? "ARCHIVE" : "RESTORE", null, null, null);
        }
        return toResponse(person, totals(person), txs.lastTransactionAt(person.getId(), user.id()));
    }

    @Transactional(readOnly = true)
    public LedgerSummaryResponse summary(AuthenticatedUser user, UUID personId) {
        Person person = owned(user, personId);
        LedgerTotals t = totals(person);
        return new LedgerSummaryResponse(person.getId(), person.getName(),
                Money.format(person.getOpeningBalance()), Money.format(t.credits()), Money.format(t.debits()),
                Money.format(LedgerCalculator.currentBalance(person.getOpeningBalance(), t.credits(), t.debits())),
                t.count(), txs.lastTransactionAt(person.getId(), user.id()));
    }

    /** Aggregate totals across ACTIVE ledgers (opening balances are part of the net figure). */
    @Transactional(readOnly = true)
    public OverviewResponse overview(AuthenticatedUser user) {
        List<Person> active = people.findByOwnerIdAndArchivedAtIsNullOrderByNameAsc(user.id());
        Map<UUID, Aggregate> aggregates = aggregatesByPerson(user.id());
        BigDecimal credits = Money.ZERO;
        BigDecimal debits = Money.ZERO;
        BigDecimal openings = Money.ZERO;
        for (Person p : active) {
            openings = openings.add(p.getOpeningBalance());
            Aggregate a = aggregates.get(p.getId());
            if (a != null) {
                credits = credits.add(a.credits);
                debits = debits.add(a.debits);
            }
        }
        return new OverviewResponse(active.size(), Money.format(credits), Money.format(debits),
                Money.format(LedgerCalculator.currentBalance(openings, credits, debits)));
    }

    @Transactional(readOnly = true)
    public DuplicateCheckResponse duplicateCheck(AuthenticatedUser user, String name) {
        String cleaned = Text.clean(name);
        if (cleaned == null) {
            return new DuplicateCheckResponse(false, 0);
        }
        long matches = people.countByOwnerIdAndNameIgnoreCaseAndArchivedAtIsNull(user.id(), cleaned);
        return new DuplicateCheckResponse(matches > 0, matches);
    }

    // ------------------------------------------------------------------ helpers

    private Person owned(AuthenticatedUser user, UUID personId) {
        return people.findByIdAndOwnerId(personId, user.id())
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
    }

    private LedgerTotals totals(Person person) {
        return LedgerTotals.of(txs.totalsByType(person.getId(), person.getOwnerId()));
    }

    private void apply(Person person, PersonRequest req) {
        person.setName(Text.clean(req.name()));
        person.setPhone(Text.clean(req.phone()));
        person.setDescription(Text.clean(req.description()));
        person.setNotes(Text.clean(req.notes()));
        person.setOpeningBalance(req.openingBalance() == null ? Money.ZERO : Money.scale(req.openingBalance()));
    }

    private Map<String, Object> snapshot(Person p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", p.getName());
        m.put("phone", p.getPhone());
        m.put("description", p.getDescription());
        m.put("openingBalance", Money.format(p.getOpeningBalance()));
        return m;
    }

    private PersonResponse toResponse(Person p, LedgerTotals t, Instant last) {
        BigDecimal balance = LedgerCalculator.currentBalance(p.getOpeningBalance(), t.credits(), t.debits());
        return new PersonResponse(p.getId(), p.getName(), p.getPhone(), p.getDescription(), p.getNotes(),
                Money.format(p.getOpeningBalance()), Money.format(t.credits()), Money.format(t.debits()),
                Money.format(balance), t.count(), last, p.isArchived(), p.getArchivedAt(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    private static final class Aggregate {
        BigDecimal credits = Money.ZERO;
        BigDecimal debits = Money.ZERO;
        long count;
        Instant last;
    }

    /** One grouped query for the whole dashboard instead of one query per person. */
    private Map<UUID, Aggregate> aggregatesByPerson(UUID ownerId) {
        Map<UUID, Aggregate> map = new HashMap<>();
        for (Object[] row : txs.totalsByPerson(ownerId)) {
            UUID personId = (UUID) row[0];
            TransactionType type = (TransactionType) row[1];
            BigDecimal sum = Money.scale(new BigDecimal(row[2].toString()));
            long n = ((Number) row[3]).longValue();
            Instant max = (Instant) row[4];
            Aggregate a = map.computeIfAbsent(personId, k -> new Aggregate());
            if (type == TransactionType.CREDIT) {
                a.credits = a.credits.add(sum);
            } else {
                a.debits = a.debits.add(sum);
            }
            a.count += n;
            if (a.last == null || (max != null && max.isAfter(a.last))) {
                a.last = max;
            }
        }
        return map;
    }
}
