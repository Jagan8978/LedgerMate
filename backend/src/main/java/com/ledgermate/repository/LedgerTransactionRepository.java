package com.ledgermate.repository;

import com.ledgermate.entity.LedgerTransaction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    List<LedgerTransaction> findByPersonIdAndOwnerId(UUID personId, UUID ownerId);

    Optional<LedgerTransaction> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<LedgerTransaction> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    Optional<LedgerTransaction> findByReversalOfTransactionId(UUID originalId);

    /** Rows of [transactionType, sum(amount), count] for one ledger. */
    @Query("select t.transactionType, sum(t.amount), count(t) from LedgerTransaction t "
            + "where t.personId = :personId and t.ownerId = :ownerId group by t.transactionType")
    List<Object[]> totalsByType(@Param("personId") UUID personId, @Param("ownerId") UUID ownerId);

    @Query("select max(t.transactionAt) from LedgerTransaction t "
            + "where t.personId = :personId and t.ownerId = :ownerId")
    Instant lastTransactionAt(@Param("personId") UUID personId, @Param("ownerId") UUID ownerId);

    /** Same as totalsByType but limited to from (inclusive) .. to (exclusive). */
    @Query("select t.transactionType, sum(t.amount), count(t) from LedgerTransaction t "
            + "where t.personId = :personId and t.ownerId = :ownerId "
            + "and t.transactionAt >= :rangeStart and t.transactionAt < :rangeEnd group by t.transactionType")
    List<Object[]> totalsByTypeBetween(@Param("personId") UUID personId, @Param("ownerId") UUID ownerId,
                                       @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd);

    /** Rows of [personId, transactionType, sum(amount), count, max(transactionAt)] for a user. */
    @Query("select t.personId, t.transactionType, sum(t.amount), count(t), max(t.transactionAt) "
            + "from LedgerTransaction t where t.ownerId = :ownerId group by t.personId, t.transactionType")
    List<Object[]> totalsByPerson(@Param("ownerId") UUID ownerId);
}
