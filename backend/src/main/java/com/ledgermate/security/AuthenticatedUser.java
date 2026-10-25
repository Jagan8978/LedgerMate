package com.ledgermate.security;

import java.util.UUID;

/** Principal placed in the SecurityContext after the JWT has been validated. */
public record AuthenticatedUser(UUID id, String email) {}
