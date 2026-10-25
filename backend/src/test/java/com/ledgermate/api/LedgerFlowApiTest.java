package com.ledgermate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgermate.entity.AuditEvent;
import com.ledgermate.repository.AuditEventRepository;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class LedgerFlowApiTest extends AbstractApiTest {

    @Autowired AuditEventRepository auditEvents;

    // ------------------------------------------------------------ people

    @Test
    void createPersonTrimsInputAndDefaultsOpeningBalanceToZero() throws Exception {
        Session s = register();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "  Ravi Kumar  ");
        body.put("phone", "  98765 43210 ");
        postAs(s, "/api/people", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Ravi Kumar"))
                .andExpect(jsonPath("$.phone").value("98765 43210"))
                .andExpect(jsonPath("$.openingBalance").value("0.00"))
                .andExpect(jsonPath("$.currentBalance").value("0.00"))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void invalidPersonDetailsAreRejectedWithFieldErrors() throws Exception {
        Session s = register();
        postAs(s, "/api/people", personBody("", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
        postAs(s, "/api/people", personBody("     ", null)).andExpect(status().isBadRequest());
        postAs(s, "/api/people", personBody("x".repeat(121), null)).andExpect(status().isBadRequest());
        postAs(s, "/api/people", personBody("Ravi", "10.123")).andExpect(status().isBadRequest());
        postAs(s, "/api/people", personBody("Ravi", "abc")).andExpect(status().isBadRequest());
        getAs(s, "/api/people").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void similarNamesAreAllowedAndTheDuplicateCheckWarns() throws Exception {
        Session s = register();
        createPerson(s, "Ravi", null);
        getAs(s, "/api/people/duplicate-check?name=%20ravi%20")
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.matches").value(1));
        postAs(s, "/api/people", personBody("Ravi", null)).andExpect(status().isCreated());
        getAs(s, "/api/people/duplicate-check?name=Someone%20Else").andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void updatingAPersonChangesDetailsAndOpeningBalanceIsPartOfTheBalance() throws Exception {
        Session s = register();
        String id = createPerson(s, "Old Name", "1000");
        addTx(s, id, "CREDIT", "250", "2025-10-22", "10:00", "Ravi", null);
        getAs(s, "/api/people/" + id)
                .andExpect(jsonPath("$.openingBalance").value("1000.00"))
                .andExpect(jsonPath("$.currentBalance").value("1250.00"));

        mvc.perform(as(put("/api/people/" + id), s).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(personBody("New Name", "500"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.openingBalance").value("500.00"))
                .andExpect(jsonPath("$.currentBalance").value("750.00"));
    }

    @Test
    void negativeOpeningBalanceIsAllowed() throws Exception {
        Session s = register();
        String id = createPerson(s, "Owes me nothing", "-300");
        getAs(s, "/api/people/" + id).andExpect(jsonPath("$.currentBalance").value("-300.00"));
    }

    // ------------------------------------------------------------ accounting

    @Test
    void specificationExampleProducesTheExpectedTotalsAndRunningBalances() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "Person A");

        getAs(s, "/api/people/" + id + "/summary")
                .andExpect(jsonPath("$.openingBalance").value("0.00"))
                .andExpect(jsonPath("$.totalCredits").value("7000.00"))
                .andExpect(jsonPath("$.totalDebits").value("2000.00"))
                .andExpect(jsonPath("$.currentBalance").value("5000.00"))
                .andExpect(jsonPath("$.transactionCount").value(4));

        // newest first by default: Oct 23 15:00, Oct 23 10:00, Oct 22 11:00, Oct 22 10:00
        getAs(s, "/api/people/" + id + "/transactions")
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.items[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.items[0].amount").value("500.00"))
                .andExpect(jsonPath("$.items[0].signedAmount").value("-500.00"))
                .andExpect(jsonPath("$.items[0].runningBalance").value("5000.00"))
                .andExpect(jsonPath("$.items[1].type").value("CREDIT"))
                .andExpect(jsonPath("$.items[1].signedAmount").value("2000.00"))
                .andExpect(jsonPath("$.items[1].runningBalance").value("5500.00"))
                .andExpect(jsonPath("$.items[2].signedAmount").value("-1500.00"))
                .andExpect(jsonPath("$.items[2].runningBalance").value("3500.00"))
                .andExpect(jsonPath("$.items[3].signedAmount").value("5000.00"))
                .andExpect(jsonPath("$.items[3].runningBalance").value("5000.00"));
    }

    @Test
    void ledgersAreIndependent() throws Exception {
        Session s = register();
        String a = createPerson(s, "Person A", null);
        String b = createPerson(s, "Person B", null);
        addTx(s, a, "CREDIT", "999.99", "2025-10-22", "10:00", "Ravi", null);

        getAs(s, "/api/people/" + a).andExpect(jsonPath("$.currentBalance").value("999.99"));
        getAs(s, "/api/people/" + b)
                .andExpect(jsonPath("$.currentBalance").value("0.00"))
                .andExpect(jsonPath("$.transactionCount").value(0));
        getAs(s, "/api/people/" + b + "/transactions").andExpect(jsonPath("$.totalItems").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "-0.01", "abc", "10.999", "1.234", "1000000000000000.00", ""})
    void invalidAmountsAreRejectedAndNothingIsSaved(String amount) throws Exception {
        Session s = register();
        String id = createPerson(s, "Validation", null);
        postAs(s, "/api/people/" + id + "/transactions",
                txBody("CREDIT", amount, "2025-10-22", "10:00", "Ravi", null))
                .andExpect(status().isBadRequest());
        getAs(s, "/api/people/" + id + "/transactions").andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void otherInvalidTransactionInputIsRejected() throws Exception {
        Session s = register();
        String id = createPerson(s, "Validation", null);
        String url = "/api/people/" + id + "/transactions";

        postAs(s, url, txBody("CREDIT", "10", "2025-10-22", "10:00", "", null)).andExpect(status().isBadRequest());
        postAs(s, url, txBody("CREDIT", "10", "2025-10-22", "10:00", "   ", null)).andExpect(status().isBadRequest());
        postAs(s, url, txBody("TRANSFER", "10", "2025-10-22", "10:00", "Ravi", null)).andExpect(status().isBadRequest());
        postAs(s, url, txBody("CREDIT", "10", "22-10-2025", "10:00", "Ravi", null)).andExpect(status().isBadRequest());

        Map<String, Object> noDate = txBody("CREDIT", "10", "2025-10-22", "10:00", "Ravi", null);
        noDate.remove("transactionDate");
        postAs(s, url, noDate).andExpect(status().isBadRequest());

        postAs(s, url, txBody("CREDIT", "10", LocalDate.now().plusDays(10).toString(), "10:00", "Ravi", null))
                .andExpect(status().isUnprocessableEntity());

        getAs(s, url).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void amountsAreStoredPositiveAndPreciseToTwoDecimals() throws Exception {
        Session s = register();
        String id = createPerson(s, "Precision", null);
        addTx(s, id, "CREDIT", "0.10", "2025-10-22", "10:00", "A", null);
        addTx(s, id, "CREDIT", "0.20", "2025-10-22", "11:00", "A", null);
        getAs(s, "/api/people/" + id + "/summary").andExpect(jsonPath("$.currentBalance").value("0.30"));
        addTx(s, id, "DEBIT", "1234567.89", "2025-10-22", "12:00", "B", null);
        getAs(s, "/api/people/" + id + "/summary")
                .andExpect(jsonPath("$.totalDebits").value("1234567.89"))
                .andExpect(jsonPath("$.currentBalance").value("-1234567.59"));
    }

    // ------------------------------------------------------------ search / filter / sort / paging

    @Test
    void filtersSearchSortingAndPagingWorkAndRunningBalanceIgnoresTheFilter() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "Filters");
        String base = "/api/people/" + id + "/transactions";

        // type filter: running balance still reflects the complete ledger, not the filtered subset
        getAs(s, base + "?type=DEBIT")
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].runningBalance").value("5000.00"))
                .andExpect(jsonPath("$.items[1].runningBalance").value("3500.00"));
        getAs(s, base + "?type=CREDIT").andExpect(jsonPath("$.totalItems").value(2));

        getAs(s, base + "?counterparty=rav").andExpect(jsonPath("$.totalItems").value(2));
        getAs(s, base + "?description=REPAYMENT").andExpect(jsonPath("$.totalItems").value(1));
        getAs(s, base + "?search=suresh").andExpect(jsonPath("$.totalItems").value(1));
        getAs(s, base + "?search=nobody").andExpect(jsonPath("$.totalItems").value(0));

        getAs(s, base + "?from=2025-10-23&to=2025-10-23").andExpect(jsonPath("$.totalItems").value(2));
        getAs(s, base + "?from=2025-10-22&to=2025-10-22").andExpect(jsonPath("$.totalItems").value(2));
        getAs(s, base + "?from=2025-10-24").andExpect(jsonPath("$.totalItems").value(0));

        getAs(s, base + "?sortBy=amount&direction=desc").andExpect(jsonPath("$.items[0].amount").value("5000.00"));
        getAs(s, base + "?sortBy=amount&direction=asc").andExpect(jsonPath("$.items[0].amount").value("500.00"));
        getAs(s, base + "?sortBy=date&direction=asc").andExpect(jsonPath("$.items[0].amount").value("5000.00"));

        getAs(s, base + "?size=2&page=1")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(1));

        getAs(s, base + "?sortBy=colour").andExpect(status().isUnprocessableEntity());
        getAs(s, base + "?from=2025-10-24&to=2025-10-01").andExpect(status().isUnprocessableEntity());
        getAs(s, base + "?type=SIDEWAYS").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ reversal

    @Test
    void reversalKeepsTheOriginalLinksBothEntriesAndRestoresTheBalance() throws Exception {
        Session s = register();
        String id = createPerson(s, "Reversal", null);
        String originalId = addTx(s, id, "CREDIT", "1000", "2025-10-22", "10:00", "Ravi", "Loan repayment")
                .get("id").asText();

        MvcResult result = postAs(s, "/api/transactions/" + originalId + "/reverse", Map.of("reason", "Entered by mistake"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.amount").value("1000.00"))
                .andExpect(jsonPath("$.signedAmount").value("-1000.00"))
                .andExpect(jsonPath("$.entryKind").value("REVERSAL"))
                .andExpect(jsonPath("$.reversalOfTransactionId").value(originalId))
                .andExpect(jsonPath("$.reason").value("Entered by mistake"))
                .andExpect(jsonPath("$.runningBalance").value("0.00"))
                .andReturn();
        String reversalId = body(result).get("id").asText();

        getAs(s, "/api/people/" + id + "/summary")
                .andExpect(jsonPath("$.currentBalance").value("0.00"))
                .andExpect(jsonPath("$.totalCredits").value("1000.00"))
                .andExpect(jsonPath("$.totalDebits").value("1000.00"))
                .andExpect(jsonPath("$.transactionCount").value(2));
        getAs(s, "/api/transactions/" + originalId)
                .andExpect(jsonPath("$.amount").value("1000.00"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.reversedByTransactionId").value(reversalId));

        postAs(s, "/api/transactions/" + originalId + "/reverse", Map.of("reason", "again"))
                .andExpect(status().isConflict());
        postAs(s, "/api/transactions/" + reversalId + "/reverse", Map.of("reason", "undo the undo"))
                .andExpect(status().isUnprocessableEntity());
        postAs(s, "/api/transactions/" + originalId + "/reverse", Map.of("reason", " "))
                .andExpect(status().isBadRequest());

        List<AuditEvent> events = auditEvents.findByOwnerIdAndEntityIdOrderByCreatedAtAsc(s.userId(), UUID.fromString(originalId));
        assertTrue(events.stream().anyMatch(e -> e.getAction().equals("REVERSE")
                && "Entered by mistake".equals(e.getReason()) && e.getActor().equals(s.email())));
    }

    @Test
    void reversingAnEarlierEntryRecalculatesLaterRunningBalances() throws Exception {
        Session s = register();
        String id = createPerson(s, "Recalc", null);
        String first = addTx(s, id, "CREDIT", "1000", "2025-10-22", "10:00", "Ravi", null).get("id").asText();
        addTx(s, id, "DEBIT", "300", "2025-10-23", "10:00", "Suresh", null);

        postAs(s, "/api/transactions/" + first + "/reverse", Map.of("reason", "wrong ledger")).andExpect(status().isCreated());

        getAs(s, "/api/people/" + id + "/summary").andExpect(jsonPath("$.currentBalance").value("-300.00"));
        getAs(s, "/api/people/" + id + "/transactions?sortBy=date&direction=asc")
                .andExpect(jsonPath("$.items[0].runningBalance").value("1000.00"))
                .andExpect(jsonPath("$.items[1].runningBalance").value("700.00"))
                .andExpect(jsonPath("$.items[2].runningBalance").value("-300.00"));
    }

    // ------------------------------------------------------------ correction

    @Test
    void correctionPreservesTheOriginalAndPostsReversalPlusReplacementAtomically() throws Exception {
        Session s = register();
        String id = createPerson(s, "Correction", null);
        String originalId = addTx(s, id, "CREDIT", "1000", "2025-10-22", "10:00", "Ravi", "Typo: extra zero")
                .get("id").asText();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "Typo: extra zero");
        request.put("replacement", txBody("CREDIT", "100.00", "2025-10-22", "10:30", "Ravi", "Loan repayment"));

        MvcResult result = postAs(s, "/api/transactions/" + originalId + "/corrections", request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversal.entryKind").value("REVERSAL"))
                .andExpect(jsonPath("$.reversal.reversalOfTransactionId").value(originalId))
                .andExpect(jsonPath("$.correction.entryKind").value("CORRECTION"))
                .andExpect(jsonPath("$.correction.correctionReferenceId").value(originalId))
                .andExpect(jsonPath("$.correction.amount").value("100.00"))
                .andReturn();
        String correctionId = body(result).get("correction").get("id").asText();

        getAs(s, "/api/people/" + id + "/summary")
                .andExpect(jsonPath("$.currentBalance").value("100.00"))
                .andExpect(jsonPath("$.totalCredits").value("1100.00"))
                .andExpect(jsonPath("$.totalDebits").value("1000.00"))
                .andExpect(jsonPath("$.transactionCount").value(3));

        getAs(s, "/api/transactions/" + originalId)
                .andExpect(jsonPath("$.amount").value("1000.00"))
                .andExpect(jsonPath("$.reversedByTransactionId").exists())
                .andExpect(jsonPath("$.correctedByTransactionId").value(correctionId));

        postAs(s, "/api/transactions/" + originalId + "/corrections", request).andExpect(status().isConflict());

        List<AuditEvent> originalEvents = auditEvents.findByOwnerIdAndEntityIdOrderByCreatedAtAsc(s.userId(), UUID.fromString(originalId));
        assertTrue(originalEvents.stream().anyMatch(e -> e.getAction().equals("REVERSE")));
        List<AuditEvent> correctionEvents = auditEvents.findByOwnerIdAndEntityIdOrderByCreatedAtAsc(s.userId(), UUID.fromString(correctionId));
        assertEquals(1, correctionEvents.size());
        assertEquals("CORRECT", correctionEvents.get(0).getAction());
        assertEquals("Typo: extra zero", correctionEvents.get(0).getReason());
    }

    @Test
    void invalidReplacementInACorrectionLeavesTheLedgerUntouched() throws Exception {
        Session s = register();
        String id = createPerson(s, "Bad correction", null);
        String originalId = addTx(s, id, "CREDIT", "1000", "2025-10-22", "10:00", "Ravi", null).get("id").asText();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "oops");
        request.put("replacement", txBody("CREDIT", "-5", "2025-10-22", "10:30", "Ravi", null));
        postAs(s, "/api/transactions/" + originalId + "/corrections", request).andExpect(status().isBadRequest());

        getAs(s, "/api/people/" + id + "/summary")
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.currentBalance").value("1000.00"));
    }

    // ------------------------------------------------------------ descriptive edit

    @Test
    void editingDescriptiveFieldsNeverChangesAmountTypeOrBalance() throws Exception {
        Session s = register();
        String id = createPerson(s, "Edit", null);
        String txId = addTx(s, id, "CREDIT", "1000", "2025-10-22", "10:00", "Ravi", "old").get("id").asText();

        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("description", "Corrected description");
        patchBody.put("counterpartyName", "Ravi K");
        patchBody.put("notes", "checked against the receipt");
        patchBody.put("amount", "1");          // ignored: not an editable field
        patchBody.put("type", "DEBIT");        // ignored: not an editable field
        mvc.perform(as(patch("/api/transactions/" + txId + "/description"), s)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Corrected description"))
                .andExpect(jsonPath("$.counterpartyName").value("Ravi K"))
                .andExpect(jsonPath("$.amount").value("1000.00"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.runningBalance").value("1000.00"));
        getAs(s, "/api/people/" + id + "/summary").andExpect(jsonPath("$.currentBalance").value("1000.00"));

        mvc.perform(as(patch("/api/transactions/" + txId + "/description"), s)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"counterpartyName\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity());

        List<AuditEvent> events = auditEvents.findByOwnerIdAndEntityIdOrderByCreatedAtAsc(s.userId(), UUID.fromString(txId));
        AuditEvent edit = events.stream().filter(e -> e.getAction().equals("UPDATE_DETAILS")).findFirst().orElseThrow();
        assertTrue(edit.getPreviousValues().contains("old"));
        assertTrue(edit.getNewValues().contains("Corrected description"));
    }

    // ------------------------------------------------------------ archive / overview / idempotency

    @Test
    void archivingHidesTheLedgerBlocksNewEntriesAndKeepsHistory() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "To archive");

        mvc.perform(as(patch("/api/people/" + id + "/archive"), s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        getAs(s, "/api/people").andExpect(jsonPath("$[?(@.id=='" + id + "')]").isEmpty());
        getAs(s, "/api/people?status=archived").andExpect(jsonPath("$[?(@.id=='" + id + "')]").isNotEmpty());
        postAs(s, "/api/people/" + id + "/transactions", txBody("CREDIT", "1", "2025-10-24", "10:00", "X", null))
                .andExpect(status().isConflict());
        getAs(s, "/api/people/" + id + "/transactions").andExpect(jsonPath("$.totalItems").value(4));
        getAs(s, "/api/people/" + id + "/summary").andExpect(jsonPath("$.currentBalance").value("5000.00"));

        mvc.perform(as(patch("/api/people/" + id + "/restore"), s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        addTx(s, id, "CREDIT", "1", "2025-10-24", "10:00", "X", null);
        getAs(s, "/api/people/" + id + "/summary").andExpect(jsonPath("$.currentBalance").value("5001.00"));
    }

    @Test
    void overviewAggregatesActiveLedgersOnly() throws Exception {
        Session s = register();
        String a = createPerson(s, "A", "50");
        String b = createPerson(s, "B", null);
        addTx(s, a, "CREDIT", "100", "2025-10-22", "10:00", "X", null);
        addTx(s, b, "CREDIT", "200", "2025-10-22", "10:00", "X", null);
        addTx(s, b, "DEBIT", "20", "2025-10-22", "11:00", "X", null);

        getAs(s, "/api/people/overview")
                .andExpect(jsonPath("$.activeLedgers").value(2))
                .andExpect(jsonPath("$.totalCredits").value("300.00"))
                .andExpect(jsonPath("$.totalDebits").value("20.00"))
                .andExpect(jsonPath("$.netBalance").value("330.00"));

        mvc.perform(as(patch("/api/people/" + b + "/archive"), s)).andExpect(status().isOk());
        getAs(s, "/api/people/overview")
                .andExpect(jsonPath("$.activeLedgers").value(1))
                .andExpect(jsonPath("$.totalCredits").value("100.00"))
                .andExpect(jsonPath("$.totalDebits").value("0.00"))
                .andExpect(jsonPath("$.netBalance").value("150.00"));
    }

    @Test
    void peopleListCarriesServerCalculatedTotalsAndLastTransaction() throws Exception {
        Session s = register();
        createSpecExampleLedger(s, "Listed");
        getAs(s, "/api/people")
                .andExpect(jsonPath("$[0].name").value("Listed"))
                .andExpect(jsonPath("$[0].totalCredits").value("7000.00"))
                .andExpect(jsonPath("$[0].totalDebits").value("2000.00"))
                .andExpect(jsonPath("$[0].currentBalance").value("5000.00"))
                .andExpect(jsonPath("$[0].transactionCount").value(4))
                .andExpect(jsonPath("$[0].lastTransactionAt").exists());
    }

    @Test
    void aRepeatedRequestWithTheSameIdempotencyKeyPostsOnlyOnce() throws Exception {
        Session s = register();
        String id = createPerson(s, "Idempotent", null);
        String url = "/api/people/" + id + "/transactions";
        String payload = json.writeValueAsString(txBody("CREDIT", "100", "2025-10-22", "10:00", "Ravi", null));

        String first = body(mvc.perform(as(post(url), s).header("Idempotency-Key", "idem-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated()).andReturn())
                .get("id").asText();
        String second = body(mvc.perform(as(post(url), s).header("Idempotency-Key", "idem-key-0001")
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated()).andReturn())
                .get("id").asText();

        assertEquals(first, second);
        getAs(s, "/api/people/" + id + "/summary")
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.currentBalance").value("100.00"));

        mvc.perform(as(post(url), s).header("Idempotency-Key", "not valid!")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------ error shape

    @Test
    void errorsAreSafeJsonWithoutStackTracesOrInternals() throws Exception {
        Session s = register();
        getAs(s, "/api/people/" + UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        getAs(s, "/api/people/not-a-uuid").andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/people"), s).contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(as(get("/api/does-not-exist"), s)).andExpect(status().isNotFound());
    }

    @Test
    void theBalanceMatchesWhatTheServerCalculatesNotWhatTheClientSends() throws Exception {
        Session s = register();
        String id = createPerson(s, "Untrusted", null);
        Map<String, Object> body = txBody("CREDIT", "100", "2025-10-22", "10:00", "Ravi", null);
        body.put("runningBalance", "999999.00");
        body.put("signedAmount", "-1.00");
        body.put("currentBalance", "123.00");
        JsonNode created = body(postAs(s, "/api/people/" + id + "/transactions", body)
                .andExpect(status().isCreated()).andReturn());
        assertEquals("100.00", created.get("runningBalance").asText());
        assertEquals("100.00", created.get("signedAmount").asText());
        getAs(s, "/api/people/" + id + "/summary").andExpect(jsonPath("$.currentBalance").value("100.00"));
    }
}
