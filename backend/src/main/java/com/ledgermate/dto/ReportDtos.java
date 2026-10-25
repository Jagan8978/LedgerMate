package com.ledgermate.dto;

import com.ledgermate.dto.TransactionDtos.TransactionResponse;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    public record MonthlySummary(String month, String openingBalance, String totalCredits,
                                 String totalDebits, String closingBalance, long transactionCount) {}

    /**
     * Period figures. periodOpeningBalance = ledger opening balance + everything before "from".
     * closingBalance = periodOpeningBalance + totalCredits - totalDebits.
     */
    public record ReportSummaryResponse(
            LocalDate from, LocalDate to, String timezone, String periodOpeningBalance,
            String totalCredits, String totalDebits, String closingBalance, long transactionCount,
            String currentLedgerBalance, List<MonthlySummary> monthly) {}

    /** Statement rows may be narrowed by text/type filters; summary covers the date range only. */
    public record StatementResponse(
            ReportSummaryResponse summary, String filteredCredits, String filteredDebits,
            long filteredCount, List<TransactionResponse> transactions) {}
}
