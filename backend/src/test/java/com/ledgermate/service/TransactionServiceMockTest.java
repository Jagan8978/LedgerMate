package com.ledgermate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgermate.dto.TransactionDtos.ReverseRequest;
import com.ledgermate.dto.TransactionDtos.TransactionRequest;
import com.ledgermate.dto.TransactionDtos.TransactionResponse;
import com.ledgermate.entity.EntryKind;
import com.ledgermate.entity.LedgerTransaction;
import com.ledgermate.entity.Person;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.exception.BusinessRuleException;
import com.ledgermate.exception.ConflictException;
import com.ledgermate.exception.NotFoundException;
import com.ledgermate.repository.LedgerTransactionRepository;
import com.ledgermate.repository.PersonRepository;
import com.ledgermate.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** Business-rule tests with the repositories mocked (no Spring context, no database). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceMockTest {

    @Mock PersonRepository people;
    @Mock LedgerTransactionRepository txs;
    @Mock AuditService audit;
    @Mock ZoneResolver zones;

    private TransactionService service;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();
    private final AuthenticatedUser user = new AuthenticatedUser(ownerId, "owner@example.com");
    private Person person;
    private final List<LedgerTransaction> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new TransactionService(people, txs, audit, zones);
        person = new Person(ownerId, "Person A");
        ReflectionTestUtils.setField(person, "id", personId);
        when(people.findForUpdate(personId, ownerId)).thenReturn(Optional.of(person));
        when(people.findByIdAndOwnerId(personId, ownerId)).thenReturn(Optional.of(person));
        when(zones.zoneOf(ownerId)).thenReturn(ZoneId.of("UTC"));
        when(txs.save(any(LedgerTransaction.class))).thenAnswer(inv -> {
            LedgerTransaction t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            }
            if (t.getCreatedAt() == null) {
                ReflectionTestUtils.setField(t, "createdAt", Instant.now());
            }
            saved.add(t);
            return t;
        });
        when(txs.findByPersonIdAndOwnerId(personId, ownerId)).thenAnswer(inv -> new ArrayList<>(saved));
    }

    private LedgerTransaction existing(TransactionType type, String amount) {
        LedgerTransaction t = new LedgerTransaction(personId, ownerId, ownerId, type, new BigDecimal(amount),
                Instant.parse("2026-01-01T10:00:00Z"), "Ravi");
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(t, "createdAt", Instant.parse("2026-01-01T10:00:00Z"));
        saved.add(t);
        when(txs.findByIdAndOwnerId(t.getId(), ownerId)).thenReturn(Optional.of(t));
        return t;
    }

    private TransactionRequest request(String amount) {
        return new TransactionRequest(TransactionType.CREDIT, new BigDecimal(amount),
                LocalDate.of(2025, 1, 2), LocalTime.of(9, 0), "Ravi", "x", null, null);
    }

    @Test
    void createWithAnAlreadyUsedIdempotencyKeyReturnsTheOriginalEntryAndSavesNothing() {
        LedgerTransaction first = existing(TransactionType.CREDIT, "500.00");
        first.setIdempotencyKey("key-12345678");
        saved.clear();
        saved.add(first);
        when(txs.findByOwnerIdAndIdempotencyKey(ownerId, "key-12345678")).thenReturn(Optional.of(first));

        TransactionResponse response = service.create(user, personId, request("500.00"), "key-12345678");

        assertEquals(first.getId(), response.id());
        verify(txs, never()).save(any(LedgerTransaction.class));
    }

    @Test
    void createRejectsAMalformedIdempotencyKey() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(user, personId, request("5.00"), "bad key with spaces!"));
        verify(txs, never()).save(any(LedgerTransaction.class));
    }

    @Test
    void createOnAnArchivedLedgerIsRejected() {
        person.setArchivedAt(Instant.now());
        assertThrows(ConflictException.class, () -> service.create(user, personId, request("5.00"), null));
        verify(txs, never()).save(any(LedgerTransaction.class));
    }

    @Test
    void createOnAnUnknownOrForeignLedgerIsNotFound() {
        UUID foreign = UUID.randomUUID();
        when(people.findForUpdate(foreign, ownerId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.create(user, foreign, request("5.00"), null));
    }

    @Test
    void createStoresThePositiveAmountWithTheChosenTypeAndAuditsIt() {
        TransactionRequest debit = new TransactionRequest(TransactionType.DEBIT, new BigDecimal("1500"),
                LocalDate.of(2025, 1, 2), LocalTime.of(9, 0), "  Suresh  ", "Money lent", null, null);

        TransactionResponse response = service.create(user, personId, debit, null);

        LedgerTransaction stored = saved.get(0);
        assertEquals(new BigDecimal("1500.00"), stored.getAmount());
        assertEquals(TransactionType.DEBIT, stored.getTransactionType());
        assertEquals("Suresh", stored.getCounterpartyName());
        assertEquals("1500.00", response.amount());
        assertEquals("-1500.00", response.signedAmount());
        assertEquals("-1500.00", response.runningBalance());
        verify(audit).record(eq(user), eq("TRANSACTION"), any(), eq("CREATE"), any(), any(), any());
    }

    @Test
    void reversePostsAnOppositeEntryLinkedToTheOriginal() {
        LedgerTransaction original = existing(TransactionType.CREDIT, "1000.00");

        TransactionResponse response = service.reverse(user, original.getId(), new ReverseRequest("Entered twice"));

        LedgerTransaction reversal = saved.get(saved.size() - 1);
        assertEquals(TransactionType.DEBIT, reversal.getTransactionType());
        assertEquals(new BigDecimal("1000.00"), reversal.getAmount());
        assertEquals(EntryKind.REVERSAL, reversal.getEntryKind());
        assertEquals(original.getId(), reversal.getReversalOfTransactionId());
        assertEquals("Entered twice", reversal.getReason());
        assertEquals("0.00", response.runningBalance());
        verify(audit).record(eq(user), eq("TRANSACTION"), eq(original.getId()), eq("REVERSE"), any(), any(), eq("Entered twice"));
    }

    @Test
    void reversingAnAlreadyReversedEntryIsAConflict() {
        LedgerTransaction original = existing(TransactionType.CREDIT, "1000.00");
        LedgerTransaction earlierReversal = existing(TransactionType.DEBIT, "1000.00");
        when(txs.findByReversalOfTransactionId(original.getId())).thenReturn(Optional.of(earlierReversal));
        assertThrows(ConflictException.class, () -> service.reverse(user, original.getId(), new ReverseRequest("again")));
    }

    @Test
    void aReversalEntryCannotItselfBeReversed() {
        LedgerTransaction reversal = existing(TransactionType.DEBIT, "1000.00");
        reversal.setEntryKind(EntryKind.REVERSAL);
        reversal.setReversalOfTransactionId(UUID.randomUUID());
        assertThrows(BusinessRuleException.class, () -> service.reverse(user, reversal.getId(), new ReverseRequest("no")));
    }

    @Test
    void reverseRequiresAReason() {
        LedgerTransaction original = existing(TransactionType.CREDIT, "10.00");
        assertThrows(BusinessRuleException.class, () -> service.reverse(user, original.getId(), new ReverseRequest("   ")));
    }

    @Test
    void farFutureDatesAreRejected() {
        ZoneId utc = ZoneId.of("UTC");
        assertThrows(BusinessRuleException.class,
                () -> TransactionService.resolveInstant(utc, LocalDate.now(utc).plusDays(5), LocalTime.NOON));
    }

    @Test
    void anExplicitTimeIsUsedAsGivenInTheUsersTimezone() {
        Instant at = TransactionService.resolveInstant(ZoneId.of("Asia/Kolkata"), LocalDate.of(2025, 10, 22), LocalTime.of(10, 0));
        assertEquals(Instant.parse("2025-10-22T04:30:00Z"), at);
    }
}
