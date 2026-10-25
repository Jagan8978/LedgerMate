package com.ledgermate.repository;

import com.ledgermate.entity.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByOwnerIdAndEntityIdOrderByCreatedAtAsc(UUID ownerId, UUID entityId);

    List<AuditEvent> findByOwnerIdAndActionOrderByCreatedAtAsc(UUID ownerId, String action);
}
