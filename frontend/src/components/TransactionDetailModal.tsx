import { useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../lib/api';
import { formatDateTime } from '../lib/dates';
import type { Transaction } from '../lib/types';
import { AmountDisplay, BalanceDisplay } from './AmountDisplay';
import { Modal } from './Modal';
import { ErrorBanner, Field } from './ui';

interface Props {
  transaction: Transaction;
  timezone: string;
  /** Descriptive fields can be edited unless the ledger is archived. */
  canEdit: boolean;
  onClose: () => void;
  onSaved: () => void;
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right font-medium text-slate-900">{children}</dd>
    </div>
  );
}

export function TransactionDetailModal({ transaction: t, timezone, canEdit, onClose, onSaved }: Props) {
  const [counterparty, setCounterparty] = useState(t.counterpartyName);
  const [description, setDescription] = useState(t.description ?? '');
  const [reference, setReference] = useState(t.referenceNumber ?? '');
  const [notes, setNotes] = useState(t.notes ?? '');
  const [reason, setReason] = useState('');
  const [apiError, setApiError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);
  const [counterpartyError, setCounterpartyError] = useState<string | undefined>();

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!counterparty.trim()) {
      setCounterpartyError('Counterparty name cannot be empty');
      return;
    }
    setCounterpartyError(undefined);
    setSaving(true);
    setApiError(null);
    try {
      await api.updateDescription(t.id, {
        counterpartyName: counterparty.trim(),
        description: description.trim(),
        referenceNumber: reference.trim(),
        notes: notes.trim(),
        reason: reason.trim() || undefined,
      });
      onSaved();
    } catch (err) {
      setApiError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title="Transaction details" onClose={onClose} wide accent={t.type === 'CREDIT' ? 'credit' : 'debit'}>
      <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200 px-4">
        <Row label="Amount">
          <AmountDisplay type={t.type} amount={t.amount} />
        </Row>
        <Row label="Date and time">{formatDateTime(t.transactionAt, timezone)}</Row>
        <Row label="Balance after this entry">
          <BalanceDisplay value={t.runningBalance} />
        </Row>
        <Row label="Entry type">
          {t.entryKind === 'NORMAL' ? 'Normal' : t.entryKind === 'REVERSAL' ? 'Reversal' : 'Correction'}
        </Row>
        {t.reason && <Row label="Reason">{t.reason}</Row>}
        {t.reversedByTransactionId && <Row label="Status">Reversed (an offsetting entry exists)</Row>}
        {t.correctedByTransactionId && <Row label="Replaced by">A corrected entry (see history)</Row>}
        {t.reversalOfTransactionId && <Row label="Reverses">An earlier entry (see history)</Row>}
        {t.correctionReferenceId && <Row label="Corrects">An earlier entry (see history)</Row>}
      </dl>

      <p className="mt-3 text-xs text-slate-500">
        Amount, type and date can never be edited. To change them, reverse or correct the entry so the history stays auditable.
      </p>

      <form onSubmit={submit} noValidate className="mt-4 space-y-4">
        <ErrorBanner error={apiError} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Counterparty" htmlFor="det-counterparty" error={counterpartyError}>
            <input id="det-counterparty" className="input" value={counterparty} maxLength={120} disabled={!canEdit} onChange={(e) => setCounterparty(e.target.value)} />
          </Field>
          <Field label="Reference number" htmlFor="det-ref">
            <input id="det-ref" className="input" value={reference} maxLength={60} disabled={!canEdit} onChange={(e) => setReference(e.target.value)} />
          </Field>
        </div>
        <Field label="Description" htmlFor="det-desc">
          <input id="det-desc" className="input" value={description} maxLength={500} disabled={!canEdit} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <Field label="Notes" htmlFor="det-notes">
          <textarea id="det-notes" className="input" rows={3} value={notes} maxLength={2000} disabled={!canEdit} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        {canEdit && (
          <Field label="Reason for this edit (optional, kept in the audit trail)" htmlFor="det-reason">
            <input id="det-reason" className="input" value={reason} maxLength={500} onChange={(e) => setReason(e.target.value)} />
          </Field>
        )}
        <div className="flex justify-end gap-3">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
          {canEdit && (
            <button type="submit" className="btn-primary" disabled={saving}>
              {saving ? 'Saving…' : 'Save details'}
            </button>
          )}
        </div>
      </form>
    </Modal>
  );
}
