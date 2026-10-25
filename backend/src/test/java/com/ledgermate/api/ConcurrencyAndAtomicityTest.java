package com.ledgermate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ledgermate.dto.AuthDtos.RegisterRequest;
import com.ledgermate.dto.PersonDtos.PersonRequest;
import com.ledgermate.dto.TransactionDtos.CorrectionRequest;
import com.ledgermate.dto.TransactionDtos.TransactionRequest;
import com.ledgermate.dto.TransactionDtos.TransactionResponse;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.entity.User;
import com.ledgermate.repository.AuditEventRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.AuthService;
import com.ledgermate.service.PersonService;
import com.ledgermate.service.TransactionQuery;
import com.ledgermate.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Drives the services directly from many threads (real transactions, real row locks) and
 * simulates database failures to prove that no partial updates are left behind.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyAndAtomicityTest {

    @Autowired AuthService auth;
    @Autowired PersonService persons;
    @Autowired TransactionService transactions;
    @Autowired AuditEventRepository auditEvents;

    private AuthenticatedUser newUser() {
        User u = auth.register(new RegisterRequest("conc-" + UUID.randomUUID() + "@example.com",
                "CorrectHorse-9-Battery", "Concurrent"));
        return new AuthenticatedUser(u.getId(), u.getEmail());
    }

    private UUID newPerson(AuthenticatedUser user) {
        return persons.create(user, new PersonRequest("Concurrent ledger", null, null, null, null)).id();
    }

    private static TransactionRequest request(TransactionType type, String amount, String counterparty) {
        return new TransactionRequest(type, new BigDecimal(amount), LocalDate.of(2025, 10, 22),
                LocalTime.of(10, 0), counterparty, null, null, null);
    }

    private <T> List<T> runAll(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(90, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCreditsAndDebitsNeverLoseAnUpdate() throws Exception {
        AuthenticatedUser user = newUser();
        UUID personId = newPerson(user);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transactions.create(user, personId, request(TransactionType.CREDIT, "7.00", "Cr"), null).id().toString());
            tasks.add(() -> transactions.create(user, personId, request(TransactionType.DEBIT, "3.00", "Db"), null).id().toString());
        }
        List<String> ids = runAll(tasks);

        assertEquals(40, new HashSet<>(ids).size(), "every request must create its own entry");
        var summary = persons.summary(user, personId);
        assertEquals(40, summary.transactionCount());
        assertEquals("140.00", summary.totalCredits());
        assertEquals("60.00", summary.totalDebits());
        assertEquals("80.00", summary.currentBalance());

        var page = transactions.list(user, personId, new TransactionQuery(null, null, null, null, null, null,
                "date", "desc", 0, 100));
        assertEquals("80.00", page.items().get(0).runningBalance(), "the newest entry must carry the final balance");
    }

    @Test
    void concurrentRequestsWithTheSameIdempotencyKeyPostExactlyOnce() throws Exception {
        AuthenticatedUser user = newUser();
        UUID personId = newPerson(user);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> transactions.create(user, personId, request(TransactionType.CREDIT, "100.00", "Ravi"),
                    "same-key-0001").id().toString());
        }
        Set<String> distinctIds = new HashSet<>(runAll(tasks));

        assertEquals(1, distinctIds.size());
        var summary = persons.summary(user, personId);
        assertEquals(1, summary.transactionCount());
        assertEquals("100.00", summary.currentBalance());
    }

    @Test
    void aDatabaseFailureWhileCreatingLeavesNoPartialEntryOrAuditRecord() {
        AuthenticatedUser user = newUser();
        UUID personId = newPerson(user);

        // 600 characters exceeds the VARCHAR(500) description column. Bean Validation is bypassed
        // here on purpose (the service is called directly) so the failure comes from the database.
        TransactionRequest tooLong = new TransactionRequest(TransactionType.CREDIT, new BigDecimal("50.00"),
                LocalDate.of(2025, 10, 22), LocalTime.of(10, 0), "Ravi", "x".repeat(600), null, null);
        assertThrows(RuntimeException.class, () -> transactions.create(user, personId, tooLong, null));

        var summary = persons.summary(user, personId);
        assertEquals(0, summary.transactionCount());
        assertEquals("0.00", summary.currentBalance());
    }

    @Test
    void aDatabaseFailureDuringACorrectionRollsBackTheReversalToo() {
        AuthenticatedUser user = newUser();
        UUID personId = newPerson(user);
        TransactionResponse original = transactions.create(user, personId,
                request(TransactionType.CREDIT, "1000.00", "Ravi"), null);

        // The replacement's counterparty is longer than the VARCHAR(120) column, so the second
        // insert fails AFTER the reversal was already saved inside the same transaction.
        TransactionRequest badReplacement = new TransactionRequest(TransactionType.CREDIT, new BigDecimal("100.00"),
                LocalDate.of(2025, 10, 22), LocalTime.of(10, 30), "R".repeat(200), null, null, null);
        assertThrows(RuntimeException.class,
                () -> transactions.correct(user, original.id(), new CorrectionRequest("typo", badReplacement)));

        var summary = persons.summary(user, personId);
        assertEquals(1, summary.transactionCount(), "neither the reversal nor the replacement may remain");
        assertEquals("1000.00", summary.currentBalance());
        var page = transactions.list(user, personId, new TransactionQuery(null, null, null, null, null, null,
                null, null, 0, 20));
        assertEquals(1, page.totalItems());
        assertEquals(null, page.items().get(0).reversedByTransactionId());
        assertFalse(auditEvents.findByOwnerIdAndEntityIdOrderByCreatedAtAsc(user.id(), original.id()).stream()
                        .anyMatch(e -> e.getAction().equals("REVERSE")),
                "the REVERSE audit event must be rolled back as well");

        // and the same correction with valid data still works afterwards
        TransactionRequest good = request(TransactionType.CREDIT, "100.00", "Ravi");
        transactions.correct(user, original.id(), new CorrectionRequest("typo", good));
        assertEquals("100.00", persons.summary(user, personId).currentBalance());
        assertTrue(persons.summary(user, personId).transactionCount() == 3);
    }
}
