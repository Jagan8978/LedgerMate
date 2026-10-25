package com.ledgermate.repository;

import com.ledgermate.entity.Person;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Person> findByOwnerIdOrderByNameAsc(UUID ownerId);

    List<Person> findByOwnerIdAndArchivedAtIsNullOrderByNameAsc(UUID ownerId);

    List<Person> findByOwnerIdAndArchivedAtIsNotNullOrderByNameAsc(UUID ownerId);

    long countByOwnerIdAndArchivedAtIsNull(UUID ownerId);

    long countByOwnerIdAndNameIgnoreCaseAndArchivedAtIsNull(UUID ownerId, String name);

    /**
     * Row lock on the ledger. Every operation that changes a ledger takes this lock first, which
     * serialises concurrent writers on the same ledger and keeps the lock order consistent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.id = :id and p.ownerId = :ownerId")
    Optional<Person> findForUpdate(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
