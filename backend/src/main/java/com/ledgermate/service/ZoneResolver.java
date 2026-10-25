package com.ledgermate.service;

import com.ledgermate.entity.User;
import com.ledgermate.repository.UserRepository;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Looks up the user's configured timezone (used for dates, date filters and exports). */
@Component
public class ZoneResolver {

    private final UserRepository users;

    public ZoneResolver(UserRepository users) {
        this.users = users;
    }

    public ZoneId zoneOf(UUID userId) {
        return users.findById(userId)
                .map(User::getTimezone)
                .map(ZoneResolver::safeZone)
                .orElse(ZoneId.of("UTC"));
    }

    private static ZoneId safeZone(String id) {
        try {
            return ZoneId.of(id);
        } catch (RuntimeException ex) {
            return ZoneId.of("UTC");
        }
    }
}
