// Mirrors the backend DTOs. Money values are decimal STRINGS ("5000.00") produced by the server
// (BigDecimal). They are never converted to JavaScript numbers, so no precision is ever lost.

export type TxType = 'CREDIT' | 'DEBIT';
export type EntryKind = 'NORMAL' | 'REVERSAL' | 'CORRECTION';

export interface User {
  id: string;
  email: string;
  displayName: string | null;
  timezone: string;
}

export interface Person {
  id: string;
  name: string;
  phone: string | null;
  description: string | null;
  notes: string | null;
  openingBalance: string;
  totalCredits: string;
  totalDebits: string;
  currentBalance: string;
  transactionCount: number;
  lastTransactionAt: string | null;
  archived: boolean;
  archivedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PersonInput {
  name: string;
  phone?: string;
  description?: string;
  openingBalance?: string;
  notes?: string;
}

export interface Overview {
  activeLedgers: number;
  totalCredits: string;
  totalDebits: string;
  netBalance: string;
}

export interface Transaction {
  id: string;
  personId: string;
  type: TxType;
  amount: string;
  signedAmount: string;
  transactionAt: string;
  counterpartyName: string;
  description: string | null;
  referenceNumber: string | null;
  notes: string | null;
  runningBalance: string;
  entryKind: EntryKind;
  reversalOfTransactionId: string | null;
  correctionReferenceId: string | null;
  reversedByTransactionId: string | null;
  correctedByTransactionId: string | null;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TransactionInput {
  type: TxType;
  amount: string;
  transactionDate: string;
  transactionTime?: string;
  counterpartyName: string;
  description?: string;
  referenceNumber?: string;
  notes?: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface MonthlySummary {
  month: string;
  openingBalance: string;
  totalCredits: string;
  totalDebits: string;
  closingBalance: string;
  transactionCount: number;
}

export interface ReportSummary {
  from: string | null;
  to: string | null;
  timezone: string;
  periodOpeningBalance: string;
  totalCredits: string;
  totalDebits: string;
  closingBalance: string;
  transactionCount: number;
  currentLedgerBalance: string;
  monthly: MonthlySummary[];
}

export interface Statement {
  summary: ReportSummary;
  filteredCredits: string;
  filteredDebits: string;
  filteredCount: number;
  transactions: Transaction[];
}

export interface TxFilters {
  type?: TxType | '';
  from?: string;
  to?: string;
  search?: string;
  sortBy?: 'date' | 'amount';
  direction?: 'asc' | 'desc';
}
