package com.ledgermate.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ledgermate.report.CsvExporter.Line;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExporterTest {

    @Test
    void headerAndRowsUseTypeAmountSignedAmountAndRunningBalance() {
        String csv = CsvExporter.build("Asia/Kolkata", List.of(
                new Line("2026-10-22 10:00:00", "CREDIT", "5000.00", "5000.00", "Ravi", "Money received",
                        null, "5000.00", "NORMAL", null, null),
                new Line("2026-10-22 11:00:00", "DEBIT", "1500.00", "-1500.00", "Suresh", "Money paid",
                        "REF-1", "3500.00", "NORMAL", null, null)));
        String[] lines = csv.split("\r\n");
        assertEquals("Date (Asia/Kolkata),Type,Amount,Signed Amount,Counterparty,Description,Reference,"
                + "Running Balance,Entry Type,Related Entry,Reason", lines[0]);
        assertEquals("2026-10-22 10:00:00,CREDIT,5000.00,5000.00,Ravi,Money received,,5000.00,NORMAL,,", lines[1]);
        assertEquals("2026-10-22 11:00:00,DEBIT,1500.00,-1500.00,Suresh,Money paid,REF-1,3500.00,NORMAL,,", lines[2]);
    }

    @Test
    void formulaCharactersAreNeutralisedInTextCells() {
        assertEquals("'=SUM(A1:A9)", CsvExporter.text("=SUM(A1:A9)"));
        assertEquals("'+1234", CsvExporter.text("+1234"));
        assertEquals("'-cmd", CsvExporter.text("-cmd"));
        assertEquals("'@handle", CsvExporter.text("@handle"));
        assertEquals("plain text", CsvExporter.text("plain text"));
        assertEquals("", CsvExporter.text(null));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"a,b\"", CsvExporter.text("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.text("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvExporter.text("line1\nline2"));
        // injection AND special characters together
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"", CsvExporter.text("=HYPERLINK(\"http://x\",\"y\")"));
    }

    @Test
    void numericColumnsAreNotPrefixedSoNegativeAmountsStayNumbers() {
        String csv = CsvExporter.build("UTC", List.of(
                new Line("d", "DEBIT", "500.00", "-500.00", "X", null, null, "-500.00", "NORMAL", null, null)));
        assertTrue(csv.contains(",500.00,-500.00,"));
        assertTrue(csv.contains(",-500.00,NORMAL"));
    }
}
