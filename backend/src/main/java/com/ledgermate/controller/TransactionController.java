package com.ledgermate.controller;

import com.ledgermate.dto.TransactionDtos.CorrectionRequest;
import com.ledgermate.dto.TransactionDtos.CorrectionResponse;
import com.ledgermate.dto.TransactionDtos.DescriptiveUpdateRequest;
import com.ledgermate.dto.TransactionDtos.PageResponse;
import com.ledgermate.dto.TransactionDtos.ReverseRequest;
import com.ledgermate.dto.TransactionDtos.TransactionRequest;
import com.ledgermate.dto.TransactionDtos.TransactionResponse;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.TransactionQuery;
import com.ledgermate.service.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping("/api/people/{personId}/transactions")
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID personId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user, personId, new TransactionQuery(type, from, to, counterparty, description,
                search, sortBy, direction, page, size));
    }

    @PostMapping("/api/people/{personId}/transactions")
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID personId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, personId, request, idempotencyKey));
    }

    @GetMapping("/api/transactions/{transactionId}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID transactionId) {
        return service.get(user, transactionId);
    }

    @PatchMapping("/api/transactions/{transactionId}/description")
    public TransactionResponse updateDescription(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable UUID transactionId,
                                                 @Valid @RequestBody DescriptiveUpdateRequest request) {
        return service.updateDescriptive(user, transactionId, request);
    }

    @PostMapping("/api/transactions/{transactionId}/reverse")
    public ResponseEntity<TransactionResponse> reverse(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable UUID transactionId,
                                                       @Valid @RequestBody ReverseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reverse(user, transactionId, request));
    }

    @PostMapping("/api/transactions/{transactionId}/corrections")
    public ResponseEntity<CorrectionResponse> correct(@AuthenticationPrincipal AuthenticatedUser user,
                                                      @PathVariable UUID transactionId,
                                                      @Valid @RequestBody CorrectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.correct(user, transactionId, request));
    }
}
