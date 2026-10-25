package com.ledgermate.service;

import com.ledgermate.dto.TransactionDtos.CorrectionRequest;
import com.ledgermate.dto.TransactionDtos.CorrectionResponse;
import com.ledgermate.dto.TransactionDtos.DescriptiveUpdateRequest;
import com.ledgermate.dto.TransactionDtos.PageResponse;
import com.ledgermate.dto.TransactionDtos.ReverseRequest;
import com.ledgermate.dto.TransactionDtos.TransactionRequest;
import com.ledgermate.dto.TransactionDtos.TransactionResponse;
import com.ledgermate.entity.EntryKind;
import com.ledgermate.entity.LedgerTransaction;
import com.ledgermate.entity.Person;
import com.ledgermate.exception.BusinessRuleException;
import com.ledgermate.exception.ConflictException;
import com.ledgermate.exception.NotFoundException;
import com.ledgermate.repository.LedgerTransactionRepository;
import com.ledgermate.repository.PersonRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.LedgerCalculator.Posting;
import com.ledgermate.service.LedgerCalculator.Row;
import com.ledgermate.util.Money;
import com.ledgermate.util.Text;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts, reads, reverses and corrects ledger entries.
 *
 * <p>Concurrency: every write first takes a row lock on the owning ledger (see
 * {@code PersonRepository#findForUpdate}). Balances are never stored; they are always derived
 * from the persisted entries, so concurrent writers cannot leave a wrong balance behind.
 */
@Service
public class TransactionService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final PersonRepository people;
    private final LedgerTransactionRepository txs;
    private final AuditService audit;
    private final ZoneResolver zones;

    public TransactionService(PersonRepository people, LedgerTransactionRepository txs,
                              AuditService audit, ZoneResolver zones) {
        this.people = people;
        this.txs = txs;
        this.audit = audit;
        this.zones = zones;
    }

    // ------------------------------------------------------------------ create

    /**
     * Posts a credit or debit. When an idempotency key is supplied, a repeated request with the
     * same key returns the originally created entry instead of posting a second one.
     */
    @Transactional
    public TransactionResponse create(AuthenticatedUser user, UUID personId, TransactionRequest req,
                                      String idempotencyKey) {
        String key = Text.clean(idempotencyKey);
        if (key != null && !KEY_PATTERN.matcher(key).matches()) {
            throw new BusinessRuleException("Idempotency-Key must be 1-64 characters: letters, digits, '-' or '_'.");
        }
        Person person = lockedPerson(user, personId);
        ensureWritable(person);

        if (key != null) {
            Optional<LedgerTransaction> existing = txs.findByOwnerIdAndIdempotencyKey(user.id(), key);
            if (existing.isPresent()) {
                if (!existing.get().getPersonId().equals(personId)) {
                    throw new ConflictException("This idempotency key was already used for a different ledger.");
                }
                return respond(person, existing.get().getId());
            }
        }

        LedgerTransaction t = newEntry(user, person, req, EntryKind.NORMAL, zones.zoneOf(user.id()));
        t.setIdempotencyKey(key);
        txs.save(t);
        audit.record(user, "TRANSACTION", t.getId(), "CREATE", null, snapshot(t), null);
        return respond(person, t.getId());
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(AuthenticatedUser user, UUID personId, TransactionQuery q) {
        Person person = ownedPerson(user, personId);
        LedgerView view = view(person);
        List<LedgerTransaction> filtered = filterAndSort(view, q, zones.zoneOf(user.id()));

        int size = Math.min(Math.max(q.size(), 1), TransactionQuery.MAX_SIZE);
        int page = Math.max(q.page(), 0);
        int total = filtered.size();
        int fromIndex = (int) Math.min((long) page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        int totalPages = (total + size - 1) / size;

        List<TransactionResponse> items = new ArrayList<>();
        for (LedgerTransaction t : filtered.subList(fromIndex, toIndex)) {
            items.add(toResponse(t, view));
        }
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    /** All matching rows without pagination (statements and CSV export). */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listAll(AuthenticatedUser user, UUID personId, TransactionQuery q) {
        Person person = ownedPerson(user, personId);
        LedgerView view = view(person);
        List<TransactionResponse> rows = new ArrayList<>();
        for (LedgerTransaction t : filterAndSort(view, q, zones.zoneOf(user.id()))) {
            rows.add(toResponse(t, view));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(AuthenticatedUser user, UUID transactionId) {
        LedgerTransaction t = ownedTransaction(user, transactionId);
        Person person = ownedPerson(user, t.getPersonId());
        return respond(person, t.getId());
    }

    // ------------------------------------------------------------------ descriptive edit

    /** Changes only descriptive fields. Amount, type and date can never be changed here. */
    @Transactional
    public TransactionResponse updateDescriptive(AuthenticatedUser user, UUID transactionId,
                                                 DescriptiveUpdateRequest req) {
        LedgerTransaction t = ownedTransaction(user, transactionId);
        Person person = lockedPerson(user, t.getPersonId());
        Map<String, Object> before = detailSnapshot(t);
        boolean changed = false;

        if (req.description() != null) {
            String v = Text.clean(req.description());
            if (!Objects.equals(v, t.getDescription())) {
                t.setDescription(v);
                changed = true;
            }
        }
        if (req.notes() != null) {
            String v = Text.clean(req.notes());
            if (!Objects.equals(v, t.getNotes())) {
                t.setNotes(v);
                changed = true;
            }
        }
        if (req.referenceNumber() != null) {
            String v = Text.clean(req.referenceNumber());
            if (!Objects.equals(v, t.getReferenceNumber())) {
                t.setReferenceNumber(v);
                changed = true;
            }
        }
        if (req.counterpartyName() != null) {
            String v = Text.clean(req.counterpartyName());
            if (v == null) {
                throw new BusinessRuleException("Counterparty name cannot be empty.");
            }
            if (!Objects.equals(v, t.getCounterpartyName())) {
                t.setCounterpartyName(v);
                changed = true;
            }
        }
        if (changed) {
            txs.save(t);
            audit.record(user, "TRANSACTION", t.getId(), "UPDATE_DETAILS", before, detailSnapshot(t),
                    Text.clean(req.reason()));
        }
        return respond(person, t.getId());
    }

    // ------------------------------------------------------------------ reverse / correct

    /** Posts an offsetting entry. The original stays in the ledger, linked to its reversal. */
    @Transactional
    public TransactionResponse reverse(AuthenticatedUser user, UUID transactionId, ReverseRequest req) {
        LedgerTransaction original = ownedTransaction(user, transactionId);
        Person person = lockedPerson(user, original.getPersonId());
        ensureWritable(person);
        assertReversible(original);
        String reason = requireReason(req.reason());

        LedgerTransaction reversal = buildReversal(user, original, reason);
        txs.save(reversal);
        audit.record(user, "TRANSACTION", original.getId(), "REVERSE", snapshot(original), snapshot(reversal), reason);
        return respond(person, reversal.getId());
    }

    /**
     * Reverses the incorrect original and posts the corrected entry in ONE database transaction.
     * If any step fails, neither new entry is saved.
     */
    @Transactional
    public CorrectionResponse correct(AuthenticatedUser user, UUID transactionId, CorrectionRequest req) {
        LedgerTransaction original = ownedTransaction(user, transactionId);
        Person person = lockedPerson(user, original.getPersonId());
        ensureWritable(person);
        assertReversible(original);
        String reason = requireReason(req.reason());

        LedgerTransaction reversal = buildReversal(user, original, reason);
        txs.save(reversal);
        audit.record(user, "TRANSACTION", original.getId(), "REVERSE", snapshot(original), snapshot(reversal), reason);

        LedgerTransaction replacement = newEntry(user, person, req.replacement(), EntryKind.CORRECTION,
                zones.zoneOf(user.id()));
        replacement.setCorrectionReferenceId(original.getId());
        replacement.setReason(reason);
        txs.save(replacement);
        audit.record(user, "TRANSACTION", replacement.getId(), "CORRECT", snapshot(original), snapshot(replacement), reason);

        LedgerView view = view(person);
        return new CorrectionResponse(toResponse(reversal, view), toResponse(replacement, view));
    }

    // ------------------------------------------------------------------ internals

    private Person lockedPerson(AuthenticatedUser user, UUID personId) {
        return people.findForUpdate(personId, user.id())
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
    }

    private Person ownedPerson(AuthenticatedUser user, UUID personId) {
        return people.findByIdAndOwnerId(personId, user.id())
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
    }

    private LedgerTransaction ownedTransaction(AuthenticatedUser user, UUID transactionId) {
        return txs.findByIdAndOwnerId(transactionId, user.id())
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
    }

    private static void ensureWritable(Person person) {
        if (person.isArchived()) {
            throw new ConflictException("This ledger is archived. Restore it before changing it.");
        }
    }

    private void assertReversible(LedgerTransaction original) {
        if (original.getEntryKind() == EntryKind.REVERSAL) {
            throw new BusinessRuleException("A reversal entry cannot be reversed or corrected.");
        }
        if (txs.findByReversalOfTransactionId(original.getId()).isPresent()) {
            throw new ConflictException("This transaction has already been reversed.");
        }
    }

    private static String requireReason(String reason) {
        String cleaned = Text.clean(reason);
        if (cleaned == null) {
            throw new BusinessRuleException("A reason is required.");
        }
        return cleaned;
    }

    private LedgerTransaction buildReversal(AuthenticatedUser user, LedgerTransaction original, String reason) {
        LedgerTransaction r = new LedgerTransaction(original.getPersonId(), original.getOwnerId(), user.id(),
                original.getTransactionType().opposite(), original.getAmount(), Instant.now(),
                original.getCounterpartyName());
        r.setEntryKind(EntryKind.REVERSAL);
        r.setReversalOfTransactionId(original.getId());
        r.setReason(reason);
        String about = original.getDescription() != null ? original.getDescription() : original.getCounterpartyName();
        r.setDescription(Text.truncate("Reversal of: " + about, 500));
        r.setReferenceNumber(original.getReferenceNumber());
        return r;
    }

    private LedgerTransaction newEntry(AuthenticatedUser user, Person person, TransactionRequest req,
                                       EntryKind kind, ZoneId zone) {
        String counterparty = Text.clean(req.counterpartyName());
        if (counterparty == null) {
            throw new BusinessRuleException("Counterparty name is required.");
        }
        // Bean Validation already limits the value to 2 decimal places; UNNECESSARY makes any
        // bypass fail loudly instead of silently rounding money.
        BigDecimal amount = req.amount().setScale(Money.SCALE, RoundingMode.UNNECESSARY);
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("Amount must be greater than zero.");
        }
        Instant at = resolveInstant(zone, req.transactionDate(), req.transactionTime());
        LedgerTransaction t = new LedgerTransaction(person.getId(), user.id(), user.id(), req.type(),
                amount, at, counterparty);
        t.setDescription(Text.clean(req.description()));
        t.setReferenceNumber(Text.clean(req.referenceNumber()));
        t.setNotes(Text.clean(req.notes()));
        t.setEntryKind(kind);
        return t;
    }

    static Instant resolveInstant(ZoneId zone, LocalDate date, LocalTime time) {
        LocalDate today = LocalDate.now(zone);
        if (date.isAfter(today.plusDays(1))) {
            throw new BusinessRuleException("Transaction date cannot be in the future.");
        }
        LocalTime t = time;
        if (t == null) {
            t = date.equals(today) ? LocalTime.now(zone).truncatedTo(ChronoUnit.SECONDS) : LocalTime.MIDNIGHT;
        }
        return ZonedDateTime.of(date, t, zone).toInstant();
    }

    // ------------------------------------------------------------------ ledger view

    private record LedgerView(List<LedgerTransaction> chronological, Map<UUID, LedgerTransaction> byId,
                              Map<UUID, BigDecimal> running, Map<UUID, UUID> reversedBy,
                              Map<UUID, UUID> correctedBy) {}

    /** Loads the COMPLETE ledger and computes running balances before any filtering happens. */
    private LedgerView view(Person person) {
        List<LedgerTransaction> all = txs.findByPersonIdAndOwnerId(person.getId(), person.getOwnerId());
        Map<UUID, LedgerTransaction> byId = new HashMap<>();
        Map<UUID, UUID> reversedBy = new HashMap<>();
        Map<UUID, UUID> correctedBy = new HashMap<>();
        List<Posting> postings = new ArrayList<>(all.size());
        for (LedgerTransaction t : all) {
            byId.put(t.getId(), t);
            postings.add(new Posting(t.getId(), t.getTransactionType(), t.getAmount(),
                    t.getTransactionAt(), t.getCreatedAt()));
            if (t.getReversalOfTransactionId() != null) {
                reversedBy.put(t.getReversalOfTransactionId(), t.getId());
            }
            if (t.getCorrectionReferenceId() != null) {
                correctedBy.put(t.getCorrectionReferenceId(), t.getId());
            }
        }
        List<Row> rows = LedgerCalculator.runningBalances(person.getOpeningBalance(), postings);
        List<LedgerTransaction> chronological = new ArrayList<>(rows.size());
        Map<UUID, BigDecimal> running = new HashMap<>();
        for (Row r : rows) {
            chronological.add(byId.get(r.posting().id()));
            running.put(r.posting().id(), r.runningBalance());
        }
        return new LedgerView(chronological, byId, running, reversedBy, correctedBy);
    }

    private TransactionResponse respond(Person person, UUID transactionId) {
        LedgerView view = view(person);
        return toResponse(view.byId().get(transactionId), view);
    }

    private TransactionResponse toResponse(LedgerTransaction t, LedgerView v) {
        return new TransactionResponse(t.getId(), t.getPersonId(), t.getTransactionType(),
                Money.format(t.getAmount()),
                Money.format(LedgerCalculator.signed(t.getTransactionType(), t.getAmount())),
                t.getTransactionAt(), t.getCounterpartyName(), t.getDescription(), t.getReferenceNumber(),
                t.getNotes(), Money.format(v.running().get(t.getId())), t.getEntryKind(),
                t.getReversalOfTransactionId(), t.getCorrectionReferenceId(),
                v.reversedBy().get(t.getId()), v.correctedBy().get(t.getId()), t.getReason(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private List<LedgerTransaction> filterAndSort(LedgerView view, TransactionQuery q, ZoneId zone) {
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to())) {
            throw new BusinessRuleException("The 'from' date must not be after the 'to' date.");
        }
        String sortBy = q.sortBy() == null ? "date" : q.sortBy().toLowerCase(Locale.ROOT);
        if (!sortBy.equals("date") && !sortBy.equals("amount")) {
            throw new BusinessRuleException("sortBy must be 'date' or 'amount'.");
        }
        String direction = q.direction() == null ? "desc" : q.direction().toLowerCase(Locale.ROOT);
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw new BusinessRuleException("direction must be 'asc' or 'desc'.");
        }
        Instant from = q.from() == null ? null : q.from().atStartOfDay(zone).toInstant();
        Instant toExclusive = q.to() == null ? null : q.to().plusDays(1).atStartOfDay(zone).toInstant();
        String counterparty = Text.clean(q.counterparty());
        String description = Text.clean(q.description());
        String search = Text.clean(q.search());

        Comparator<LedgerTransaction> chronological = Comparator
                .comparing(LedgerTransaction::getTransactionAt)
                .thenComparing(LedgerTransaction::getCreatedAt)
                .thenComparing(LedgerTransaction::getId);
        Comparator<LedgerTransaction> order = sortBy.equals("amount")
                ? Comparator.comparing(LedgerTransaction::getAmount).thenComparing(chronological)
                : chronological;
        if (direction.equals("desc")) {
            order = order.reversed();
        }

        List<LedgerTransaction> result = new ArrayList<>();
        for (LedgerTransaction t : view.chronological()) {
            if (q.type() != null && t.getTransactionType() != q.type()) continue;
            if (from != null && t.getTransactionAt().isBefore(from)) continue;
            if (toExclusive != null && !t.getTransactionAt().isBefore(toExclusive)) continue;
            if (!Text.containsIgnoreCase(t.getCounterpartyName(), counterparty)) continue;
            if (!Text.containsIgnoreCase(t.getDescription(), description)) continue;
            if (search != null
                    && !Text.containsIgnoreCase(t.getCounterpartyName(), search)
                    && !Text.containsIgnoreCase(t.getDescription(), search)
                    && !Text.containsIgnoreCase(t.getReferenceNumber(), search)) continue;
            result.add(t);
        }
        result.sort(order);
        return result;
    }

    private Map<String, Object> snapshot(LedgerTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", t.getTransactionType().name());
        m.put("amount", Money.format(t.getAmount()));
        m.put("transactionAt", t.getTransactionAt().toString());
        m.put("counterparty", t.getCounterpartyName());
        m.put("entryKind", t.getEntryKind().name());
        return m;
    }

    private Map<String, Object> detailSnapshot(LedgerTransaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("counterparty", t.getCounterpartyName());
        m.put("description", t.getDescription());
        m.put("referenceNumber", t.getReferenceNumber());
        m.put("notes", Text.truncate(t.getNotes(), 500));
        return m;
    }
}
