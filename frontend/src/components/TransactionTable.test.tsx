import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Transaction } from '../lib/types';
import { TransactionTable } from './TransactionTable';

function tx(over: Partial<Transaction>): Transaction {
  return {
    id: 'id-' + Math.random(),
    personId: 'p1',
    type: 'CREDIT',
    amount: '1000.00',
    signedAmount: '1000.00',
    transactionAt: '2025-10-22T04:30:00Z',
    counterpartyName: 'Ravi',
    description: 'Money received',
    referenceNumber: null,
    notes: null,
    runningBalance: '1000.00',
    entryKind: 'NORMAL',
    reversalOfTransactionId: null,
    correctionReferenceId: null,
    reversedByTransactionId: null,
    correctedByTransactionId: null,
    reason: null,
    createdAt: '2025-10-22T04:30:00Z',
    updatedAt: '2025-10-22T04:30:00Z',
    ...over,
  };
}

describe('TransactionTable', () => {
  it('shows credits green with + and debits red with the minus sign, plus running balances', () => {
    render(
      <TransactionTable
        timezone="Asia/Kolkata"
        rows={[
          tx({ id: 'a', type: 'CREDIT', amount: '5000.00', runningBalance: '5000.00' }),
          tx({ id: 'b', type: 'DEBIT', amount: '1500.00', runningBalance: '3500.00', counterpartyName: 'Suresh' }),
        ]}
      />,
    );
    const rows = screen.getAllByTestId('tx-row');
    expect(within(rows[0]).getByTestId('amount-credit')).toHaveTextContent('+₹5,000.00');
    expect(within(rows[1]).getByTestId('amount-debit')).toHaveTextContent('−₹1,500.00');
    expect(within(rows[1]).getByTestId('balance-positive')).toHaveTextContent('₹3,500.00');
  });

  it('marks reversed entries and hides change actions for them and for reversals', () => {
    const calls: string[] = [];
    render(
      <TransactionTable
        timezone="UTC"
        onReverse={() => calls.push('reverse')}
        onCorrect={() => calls.push('correct')}
        onDetails={() => calls.push('details')}
        rows={[
          tx({ id: 'orig', reversedByTransactionId: 'rev' }),
          tx({ id: 'rev', type: 'DEBIT', entryKind: 'REVERSAL', reversalOfTransactionId: 'orig', reason: 'Entered twice' }),
          tx({ id: 'live' }),
        ]}
      />,
    );
    expect(screen.getByText('Reversed')).toBeInTheDocument();
    expect(screen.getByText('Reversal')).toBeInTheDocument();
    expect(screen.getByText('Reason: Entered twice')).toBeInTheDocument();
    // only the untouched entry can still be reversed or corrected
    expect(screen.getAllByRole('button', { name: 'Reverse' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: 'Correct' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: 'Details' })).toHaveLength(3);
  });

  it('offers no change actions on an archived (read-only) ledger', () => {
    render(<TransactionTable timezone="UTC" writable={false} onReverse={() => {}} onCorrect={() => {}} onDetails={() => {}} rows={[tx({})]} />);
    expect(screen.queryByRole('button', { name: 'Reverse' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Correct' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Details' })).toBeInTheDocument();
  });
});
