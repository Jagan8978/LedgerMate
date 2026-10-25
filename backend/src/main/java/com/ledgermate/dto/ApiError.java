package com.ledgermate.dto;

import java.time.Instant;
import java.util.List;

/** Uniform error body. Never contains stack traces or database details. */
public record ApiError(Instant timestamp, int status, String error, String message, String path,
                       List<FieldIssue> fieldErrors) {

    public record FieldIssue(String field, String message) {}
}
