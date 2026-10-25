import { useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '../lib/api';
import { formatSigned } from '../lib/money';
import type { Transaction } from '../lib/types';
import { Modal } from './Modal';
import { ErrorBanner, Field } from './ui';

interface Props {
  transaction: Transaction;
  onClose: () => void;
  onDone: () => void;
}

export function ReverseModal({ transaction, onClose, onDone }: Props) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | undefined>();
  const [apiError, setApiError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!reason.trim()) {
      setError('A reason is required');
      return;
    }
    setError(undefined);
    setSaving(true);
    setApiError(null);
    try {
      await api.reverseTransaction(transaction.id, reason.trim());
      onDone();
    } catch (err) {
      setApiError(err);
    } finally {
      setSaving(false);
    }
  };

  const opposite = transaction.type === 'CREDIT' ? 'DEBIT' : 'CREDIT';

  return (
    <Modal title="Reverse transaction" onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <ErrorBanner error={apiError} />
        <p className="text-sm text-slate-700">
          You are reversing{' '}
          <strong className={transaction.type === 'CREDIT' ? 'text-credit' : 'text-debit'}>
            {formatSigned(transaction.type, transaction.amount)}
          </strong>{' '}
          ({transaction.counterpartyName}). The original is <em>not</em> deleted: an offsetting{' '}
          <strong className={opposite === 'CREDIT' ? 'text-credit' : 'text-debit'}>{formatSigned(opposite, transaction.amount)}</strong>{' '}
          entry is added and linked to it.
        </p>
        <Field label="Reason *" htmlFor="rev-reason" error={error}>
          <input
            id="rev-reason"
            className={`input ${error ? 'input-error' : ''}`}
            value={reason}
            maxLength={500}
            autoFocus
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. Entered twice by mistake"
          />
        </Field>
        <div className="flex justify-end gap-3">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Reversing…' : 'Reverse transaction'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
