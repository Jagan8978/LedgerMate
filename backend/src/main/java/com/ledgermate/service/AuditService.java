package com.ledgermate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgermate.entity.AuditEvent;
import com.ledgermate.repository.AuditEventRepository;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.util.Text;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit events. It must join the caller's database transaction
 * (MANDATORY) so a financial change and its audit record commit or roll back together.
 * Callers only pass whitelisted business fields, never credentials.
 */
@Service
public class AuditService {

    private final AuditEventRepository events;
    private final ObjectMapper mapper;

    public AuditService(AuditEventRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuthenticatedUser actor, String entityType, UUID entityId, String action,
                       Map<String, Object> previous, Map<String, Object> next, String reason) {
        events.save(new AuditEvent(actor.id(), entityType, entityId, action,
                Text.truncate(json(previous), 4000), Text.truncate(json(next), 4000),
                Text.truncate(reason, 500), actor.email()));
    }

    private String json(Map<String, Object> values) {
        if (values == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise audit record", ex);
        }
    }
}
