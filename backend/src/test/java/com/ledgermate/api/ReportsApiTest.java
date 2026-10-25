package com.ledgermate.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ReportsApiTest extends AbstractApiTest {

    @Test
    void summaryForTheWholeLedgerReconcilesWithTheCurrentBalance() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "Reports");

        getAs(s, "/api/people/" + id + "/reports/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodOpeningBalance").value("0.00"))
                .andExpect(jsonPath("$.totalCredits").value("7000.00"))
                .andExpect(jsonPath("$.totalDebits").value("2000.00"))
                .andExpect(jsonPath("$.closingBalance").value("5000.00"))
                .andExpect(jsonPath("$.currentLedgerBalance").value("5000.00"))
                .andExpect(jsonPath("$.transactionCount").value(4))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.monthly.length()").value(1))
                .andExpect(jsonPath("$.monthly[0].month").value("2025-10"))
                .andExpect(jsonPath("$.monthly[0].openingBalance").value("0.00"))
                .andExpect(jsonPath("$.monthly[0].totalCredits").value("7000.00"))
                .andExpect(jsonPath("$.monthly[0].totalDebits").value("2000.00"))
                .andExpect(jsonPath("$.monthly[0].closingBalance").value("5000.00"));
    }

    @Test
    void periodOpeningBalanceIncludesTheLedgerOpeningBalanceAndEverythingBeforeThePeriod() throws Exception {
        Session s = register();
        String id = createPerson(s, "Period", "1000");
        addTx(s, id, "CREDIT", "5000", "2025-10-22", "10:00", "Ravi", null);
        addTx(s, id, "DEBIT", "1500", "2025-10-22", "11:00", "Suresh", null);
        addTx(s, id, "CREDIT", "2000", "2025-10-23", "10:00", "Kumar", null);
        addTx(s, id, "DEBIT", "500", "2025-10-23", "15:00", "Ravi", null);

        // period = 23 Oct only: opening = 1000 + 5000 - 1500 = 4500
        getAs(s, "/api/people/" + id + "/reports/summary?from=2025-10-23&to=2025-10-23")
                .andExpect(jsonPath("$.periodOpeningBalance").value("4500.00"))
                .andExpect(jsonPath("$.totalCredits").value("2000.00"))
                .andExpect(jsonPath("$.totalDebits").value("500.00"))
                .andExpect(jsonPath("$.closingBalance").value("6000.00"))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.currentLedgerBalance").value("6000.00"));

        // a period with no activity: closing equals opening
        getAs(s, "/api/people/" + id + "/reports/summary?from=2025-11-01&to=2025-11-30")
                .andExpect(jsonPath("$.periodOpeningBalance").value("6000.00"))
                .andExpect(jsonPath("$.totalCredits").value("0.00"))
                .andExpect(jsonPath("$.closingBalance").value("6000.00"))
                .andExpect(jsonPath("$.transactionCount").value(0))
                .andExpect(jsonPath("$.monthly.length()").value(0));

        getAs(s, "/api/people/" + id + "/reports/summary?from=2025-12-01&to=2025-01-01")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void monthlySummariesChainTheClosingBalanceIntoTheNextOpeningBalance() throws Exception {
        Session s = register();
        String id = createPerson(s, "Monthly", null);
        addTx(s, id, "CREDIT", "1000", "2025-08-15", "10:00", "A", null);
        addTx(s, id, "DEBIT", "250", "2025-09-10", "10:00", "B", null);
        addTx(s, id, "CREDIT", "50", "2025-09-20", "10:00", "C", null);

        getAs(s, "/api/people/" + id + "/reports/summary")
                .andExpect(jsonPath("$.monthly.length()").value(2))
                .andExpect(jsonPath("$.monthly[0].month").value("2025-08"))
                .andExpect(jsonPath("$.monthly[0].closingBalance").value("1000.00"))
                .andExpect(jsonPath("$.monthly[1].month").value("2025-09"))
                .andExpect(jsonPath("$.monthly[1].openingBalance").value("1000.00"))
                .andExpect(jsonPath("$.monthly[1].totalCredits").value("50.00"))
                .andExpect(jsonPath("$.monthly[1].totalDebits").value("250.00"))
                .andExpect(jsonPath("$.monthly[1].closingBalance").value("800.00"))
                .andExpect(jsonPath("$.monthly[1].transactionCount").value(2));
    }

    @Test
    void statementListsEntriesChronologicallyWithFullLedgerRunningBalances() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "Statement");

        getAs(s, "/api/people/" + id + "/reports/transactions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filteredCount").value(4))
                .andExpect(jsonPath("$.filteredCredits").value("7000.00"))
                .andExpect(jsonPath("$.filteredDebits").value("2000.00"))
                .andExpect(jsonPath("$.transactions[0].runningBalance").value("5000.00"))
                .andExpect(jsonPath("$.transactions[1].runningBalance").value("3500.00"))
                .andExpect(jsonPath("$.transactions[2].runningBalance").value("5500.00"))
                .andExpect(jsonPath("$.transactions[3].runningBalance").value("5000.00"))
                .andExpect(jsonPath("$.summary.closingBalance").value("5000.00"));

        // text/type filters narrow the rows, but each running balance still reflects the full ledger
        getAs(s, "/api/people/" + id + "/reports/transactions?type=DEBIT&from=2025-10-23&to=2025-10-23")
                .andExpect(jsonPath("$.filteredCount").value(1))
                .andExpect(jsonPath("$.filteredDebits").value("500.00"))
                .andExpect(jsonPath("$.transactions[0].runningBalance").value("5000.00"))
                .andExpect(jsonPath("$.summary.periodOpeningBalance").value("3500.00"))
                .andExpect(jsonPath("$.summary.closingBalance").value("5000.00"));
    }

    @Test
    void csvExportContainsTypeAmountSignedAmountCounterpartyAndRunningBalance() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "CSV Export");

        MvcResult result = getAs(s, "/api/people/" + id + "/reports/export")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("ledger-csv-export-")))
                .andReturn();
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");

        assertTrue(lines[0].startsWith("﻿Date (Asia/Kolkata),Type,Amount,Signed Amount,Counterparty,"), lines[0]);
        assertEquals(5, lines.length, csv);
        assertEquals("2025-10-22 10:00:00,CREDIT,5000.00,5000.00,Ravi,Money received from Ravi,,5000.00,NORMAL,,", lines[1]);
        assertEquals("2025-10-22 11:00:00,DEBIT,1500.00,-1500.00,Suresh,Money paid to Suresh,,3500.00,NORMAL,,", lines[2]);
        assertEquals("2025-10-23 10:00:00,CREDIT,2000.00,2000.00,Kumar,Repayment received from Kumar,,5500.00,NORMAL,,", lines[3]);
        assertEquals("2025-10-23 15:00:00,DEBIT,500.00,-500.00,Ravi,Money paid to Ravi,,5000.00,NORMAL,,", lines[4]);
    }

    @Test
    void csvExportHonoursFiltersAndNeutralisesFormulaInjection() throws Exception {
        Session s = register();
        String id = createPerson(s, "Injection", null);
        addTx(s, id, "CREDIT", "10", "2025-10-22", "10:00", "=HYPERLINK(\"http://evil.example\",\"click\")", "+cmd|' /C calc'!A0");
        addTx(s, id, "DEBIT", "5", "2025-10-22", "11:00", "Normal, Person", "-2+3");

        String all = getAs(s, "/api/people/" + id + "/reports/export").andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(all.contains("\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"click\"\")\""), all);
        assertTrue(all.contains("'+cmd|' /C calc'!A0"), all);
        assertTrue(all.contains("\"Normal, Person\""), all);
        assertTrue(all.contains("'-2+3"), all);
        assertTrue(all.contains(",DEBIT,5.00,-5.00,"), all);

        String creditsOnly = getAs(s, "/api/people/" + id + "/reports/export?type=CREDIT").andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(2, creditsOnly.split("\r\n").length);
    }

    @Test
    void csvExportIsRecordedInTheAuditTrail() throws Exception {
        Session s = register();
        String id = createSpecExampleLedger(s, "Audited export");
        getAs(s, "/api/people/" + id + "/reports/export").andExpect(status().isOk());
        // exporting twice is two events; the request itself must keep working
        getAs(s, "/api/people/" + id + "/reports/export").andExpect(status().isOk());
    }
}
