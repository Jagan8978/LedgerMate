package com.ledgermate.controller;

import com.ledgermate.dto.ReportDtos.ReportSummaryResponse;
import com.ledgermate.dto.ReportDtos.StatementResponse;
import com.ledgermate.entity.TransactionType;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.ReportService;
import com.ledgermate.service.ReportService.CsvFile;
import com.ledgermate.service.TransactionQuery;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/people/{personId}/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.summary(user, personId, from, to);
    }

    @GetMapping("/transactions")
    public StatementResponse statement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID personId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String direction) {
        return service.statement(user, personId, query(type, from, to, counterparty, description, search, sortBy, direction));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID personId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String direction) {
        CsvFile file = service.exportCsv(user, personId, query(type, from, to, counterparty, description, search, sortBy, direction));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }

    private static TransactionQuery query(TransactionType type, LocalDate from, LocalDate to, String counterparty,
                                          String description, String search, String sortBy, String direction) {
        return new TransactionQuery(type, from, to, counterparty, description, search, sortBy, direction,
                0, Integer.MAX_VALUE);
    }
}
