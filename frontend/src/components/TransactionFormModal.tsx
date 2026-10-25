import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError, newIdempotencyKey } from '../lib/api';
import { currentTime, today } from '../lib/dates';
import { formatBalance, formatSigned, isValidAmountInput } from '../lib/money';
import type { Person, Transaction, TxType } from '../lib/types';
import { Modal } from './Modal';
import { ErrorBanner, Field } from './ui';

interface Props {
  person: Person;
  /** CREDIT / DEBIT opens a new-entry form with that type pre-selected. */
  initialType: TxType;
  /** When set, the form posts a CORRECTION of this entry instead of a plain new entry. */
  correcting?: Transaction;
  onClose: () => void;
  onSaved: () => void;
}

export function TransactionFormModal({ person, initialType, correcting, onClose, onSaved }: Props) {
  const isCorrection = Boolean(correcting);
  const [type, setType] = useState<TxType>(correcting?.type ?? initialType);
  const [amount, setAmount] = useState(correcting?.amount ?? '');
  const [date, setDate] = useState(today());
  const [time, setTime] = useState(currentTime());
  const [counterparty, setCounterparty] = useState(correcting?.counterpartyName ?? '');
  const [description, setDescription] = useState(correcting?.description ?? '');
  const [reference, setReference] = useState(correcting?.referenceNumber ?? '');
  const [notes, setNotes] = useState(correcting?.notes ?? '');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);
  // One key per opened form: a double click or a retry after a network blip cannot post twice.
  const idempotencyKey = useMemo(() => newIdempotencyKey(), []);

  const credit = type === 'CREDIT';
  const amountOk = isValidAmountInput(amount);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!amountOk) {
      found.amount = 'Enter an amount greater than zero, with at most 2 decimal places';
    }
    if (!date) {
      found.transactionDate = 'Date is required';
    } else if (date > today()) {
      found.transactionDate = 'Date cannot be in the future';
    }
    if (!counterparty.trim()) {
      found.counterpartyName = credit ? 'Enter who the money was received from' : 'Enter who the money was paid to';
    }
    if (isCorrection && !reason.trim()) {
      found.reason = 'A reason is required for a correction';
    }
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }

    const body = {
      type,
      amount: amount.trim(),
      transactionDate: date,
      transactionTime: time || undefined,
      counterpartyName: counterparty.trim(),
      description: description.trim() || undefined,
      referenceNumber: reference.trim() || undefined,
      notes: notes.trim() || undefined,
    };

    setSaving(true);
    setApiError(null);
    try {
      if (correcting) {
        await api.correctTransaction(correcting.id, reason.trim(), body);
      } else {
        await api.createTransaction(person.id, body, idempotencyKey);
      }
      onSaved();
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        const map: Record<string, string> = {};
        err.fieldErrors.forEach((f) => {
          map[f.field.replace(/^replacement\./, '')] = f.message;
        });
        setErrors(map);
      }
      setApiError(err);
    } finally {
      setSaving(false);
    }
  };

  const title = isCorrection ? 'Correct transaction' : credit ? 'Add credit (money received)' : 'Add debit (money paid)';

  return (
    <Modal title={title} onClose={onClose} accent={credit ? 'credit' : 'debit'} wide>
      <form onSubmit={submit} noValidate className="space-y-4">
        <ErrorBanner error={apiError} />

        {isCorrection && correcting && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            The original entry ({formatSigned(correcting.type, correcting.amount)}) stays in the ledger. LedgerMate will
            post a reversal of it plus the corrected entry below, so the full history remains auditable.
          </div>
        )}

        {isCorrection && (
          <fieldset className="flex gap-2" aria-label="Transaction type">
            {(['CREDIT', 'DEBIT'] as TxType[]).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setType(t)}
                aria-pressed={type === t}
                className={`flex-1 rounded-lg border px-3 py-2 text-sm font-semibold ${
                  type === t
                    ? t === 'CREDIT'
                      ? 'border-credit bg-credit text-white'
                      : 'border-debit bg-debit text-white'
                    : 'border-slate-300 bg-white text-slate-700'
                }`}
              >
                {t === 'CREDIT' ? 'Credit (+)' : 'Debit (−)'}
              </button>
            ))}
          </fieldset>
        )}

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Amount (₹) *" htmlFor="tx-amount" error={errors.amount}>
            <input
              id="tx-amount"
              className={`input text-lg font-semibold ${errors.amount ? 'input-error' : ''}`}
              inputMode="decimal"
              autoFocus
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              autoComplete="off"
            />
          </Field>
          <Field
            label={credit ? 'Received from *' : 'Paid to *'}
            htmlFor="tx-counterparty"
            error={errors.counterpartyName}
          >
            <input
              id="tx-counterparty"
              className={`input ${errors.counterpartyName ? 'input-error' : ''}`}
              value={counterparty}
              maxLength={120}
              onChange={(e) => setCounterparty(e.target.value)}
              placeholder={credit ? 'Who gave you the money' : 'Who received the money'}
            />
          </Field>
          <Field label="Date *" htmlFor="tx-date" error={errors.transactionDate}>
            <input
              id="tx-date"
              type="date"
              className={`input ${errors.transactionDate ? 'input-error' : ''}`}
              value={date}
              max={today()}
              onChange={(e) => setDate(e.target.value)}
            />
          </Field>
          <Field label="Time (optional)" htmlFor="tx-time" error={errors.transactionTime}>
            <input id="tx-time" type="time" className="input" value={time} onChange={(e) => setTime(e.target.value)} />
          </Field>
        </div>

        <Field label="Description (optional)" htmlFor="tx-desc" error={errors.description}>
          <input id="tx-desc" className="input" value={description} maxLength={500} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Reference number (optional)" htmlFor="tx-ref" error={errors.referenceNumber}>
            <input id="tx-ref" className="input" value={reference} maxLength={60} onChange={(e) => setReference(e.target.value)} />
          </Field>
          <Field label="Notes (optional)" htmlFor="tx-notes" error={errors.notes}>
            <input id="tx-notes" className="input" value={notes} maxLength={2000} onChange={(e) => setNotes(e.target.value)} />
          </Field>
        </div>

        {isCorrection && (
          <Field label="Reason for the correction *" htmlFor="tx-reason" error={errors.reason}>
            <input id="tx-reason" className={`input ${errors.reason ? 'input-error' : ''}`} value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
          </Field>
        )}

        {amountOk && (
          <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700" data-testid="tx-preview">
            This will post{' '}
            <strong className={credit ? 'text-credit' : 'text-debit'}>{formatSigned(type, amount.trim())}</strong>
            {isCorrection ? ' as the corrected entry.' : <> to {person.name}. Balance today: <strong>{formatBalance(person.currentBalance)}</strong>.</>}
          </p>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className={credit ? 'btn-credit' : 'btn-debit'} disabled={saving}>
            {saving ? 'Saving…' : isCorrection ? 'Post correction' : credit ? 'Save credit' : 'Save debit'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
