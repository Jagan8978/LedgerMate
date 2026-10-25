package com.ledgermate.controller;

import com.ledgermate.dto.PersonDtos.DuplicateCheckResponse;
import com.ledgermate.dto.PersonDtos.LedgerSummaryResponse;
import com.ledgermate.dto.PersonDtos.OverviewResponse;
import com.ledgermate.dto.PersonDtos.PersonRequest;
import com.ledgermate.dto.PersonDtos.PersonResponse;
import com.ledgermate.security.AuthenticatedUser;
import com.ledgermate.service.PersonService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
    }

    @GetMapping
    public List<PersonResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                     @RequestParam(defaultValue = "active") String status) {
        return service.list(user, status);
    }

    @PostMapping
    public ResponseEntity<PersonResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @Valid @RequestBody PersonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, request));
    }

    @GetMapping("/overview")
    public OverviewResponse overview(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.overview(user);
    }

    @GetMapping("/duplicate-check")
    public DuplicateCheckResponse duplicateCheck(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @RequestParam String name) {
        return service.duplicateCheck(user, name);
    }

    @GetMapping("/{personId}")
    public PersonResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID personId) {
        return service.get(user, personId);
    }

    @PutMapping("/{personId}")
    public PersonResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID personId,
                                 @Valid @RequestBody PersonRequest request) {
        return service.update(user, personId, request);
    }

    @PatchMapping("/{personId}/archive")
    public PersonResponse archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID personId) {
        return service.setArchived(user, personId, true);
    }

    @PatchMapping("/{personId}/restore")
    public PersonResponse restore(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID personId) {
        return service.setArchived(user, personId, false);
    }

    @GetMapping("/{personId}/summary")
    public LedgerSummaryResponse summary(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID personId) {
        return service.summary(user, personId);
    }
}
