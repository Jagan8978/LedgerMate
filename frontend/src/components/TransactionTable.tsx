import { formatDateTime } from '../lib/dates';
import type { Transaction } from '../lib/types';
import { AmountDisplay, BalanceDisplay } from './AmountDisplay';
import { Badge } from './ui';

interface Props {
  rows: Transaction[];
  timezone: string;
  /** Row actions (details / reverse / correct) are shown only when handlers are given. */
  onDetails?: (t: Transaction) => void;
  onReverse?: (t: Transaction) => void;
  onCorrect?: (t: Transaction) => void;
  /** false for archived ledgers: history stays visible but entries cannot be changed. */
  writable?: boolean;
}

export function TransactionTable({ rows, timezone, onDetails, onReverse, onCorrect, writable = true }: Props) {
  const showActions = Boolean(onDetails || onReverse || onCorrect);

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[720px] text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3 font-semibold">Date</th>
            <th scope="col" className="px-4 py-3 font-semibold">Type</th>
            <th scope="col" className="px-4 py-3 font-semibold">Particulars</th>
            <th scope="col" className="px-4 py-3 text-right font-semibold">Amount</th>
            <th scope="col" className="px-4 py-3 text-right font-semibold">Balance</th>
            {showActions && (
              <th scope="col" className="no-print px-4 py-3 text-right font-semibold">
                Actions
              </th>
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((t) => {
            const reversed = Boolean(t.reversedByTransactionId);
            const canChange = writable && t.entryKind !== 'REVERSAL' && !reversed;
            return (
              <tr key={t.id} className={reversed ? 'bg-slate-50/60 text-slate-500' : ''} data-testid="tx-row">
                <td className="whitespace-nowrap px-4 py-3 align-top text-slate-600">{formatDateTime(t.transactionAt, timezone)}</td>
                <td className="whitespace-nowrap px-4 py-3 align-top">
                  <span className={`text-xs font-semibold uppercase tracking-wide ${t.type === 'CREDIT' ? 'text-credit' : 'text-debit'}`}>
                    {t.type === 'CREDIT' ? 'Credit' : 'Debit'}
                  </span>
                </td>
                <td className="px-4 py-3 align-top">
                  <div className="font-medium text-slate-900">{t.counterpartyName}</div>
                  {t.description && <div className="text-slate-600">{t.description}</div>}
                  <div className="mt-1 flex flex-wrap gap-1">
                    {t.referenceNumber && <Badge tone="grey">Ref {t.referenceNumber}</Badge>}
                    {t.entryKind === 'REVERSAL' && <Badge tone="amber">Reversal</Badge>}
                    {t.entryKind === 'CORRECTION' && <Badge tone="blue">Correction</Badge>}
                    {reversed && <Badge tone="amber">Reversed</Badge>}
                  </div>
                  {t.reason && <div className="mt-1 text-xs text-slate-500">Reason: {t.reason}</div>}
                </td>
                <td className="px-4 py-3 text-right align-top">
                  <AmountDisplay type={t.type} amount={t.amount} />
                </td>
                <td className="px-4 py-3 text-right align-top">
                  <BalanceDisplay value={t.runningBalance} />
                </td>
                {showActions && (
                  <td className="no-print whitespace-nowrap px-4 py-3 text-right align-top">
                    <div className="flex justify-end gap-3">
                      {onDetails && (
                        <button type="button" className="btn-link" onClick={() => onDetails(t)}>
                          Details
                        </button>
                      )}
                      {onCorrect && canChange && (
                        <button type="button" className="btn-link" onClick={() => onCorrect(t)}>
                          Correct
                        </button>
                      )}
                      {onReverse && canChange && (
                        <button type="button" className="text-sm font-medium text-red-700 hover:underline" onClick={() => onReverse(t)}>
                          Reverse
                        </button>
                      )}
                    </div>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
