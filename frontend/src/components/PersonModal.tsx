import { useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError } from '../lib/api';
import { isValidOpeningBalanceInput } from '../lib/money';
import type { Person } from '../lib/types';
import { Modal } from './Modal';
import { ErrorBanner, Field } from './ui';

interface Props {
  /** When given, the dialog edits this person; otherwise it creates a new ledger. */
  person?: Person;
  onClose: () => void;
  onSaved: (person: Person) => void;
}

export function PersonModal({ person, onClose, onSaved }: Props) {
  const editing = Boolean(person);
  const [name, setName] = useState(person?.name ?? '');
  const [phone, setPhone] = useState(person?.phone ?? '');
  const [description, setDescription] = useState(person?.description ?? '');
  const [openingBalance, setOpeningBalance] = useState(person?.openingBalance ?? '0.00');
  const [notes, setNotes] = useState(person?.notes ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);
  const [duplicate, setDuplicate] = useState(false);

  const checkDuplicate = async () => {
    const trimmed = name.trim();
    if (!trimmed || (editing && trimmed.toLowerCase() === person?.name.toLowerCase())) {
      setDuplicate(false);
      return;
    }
    try {
      const res = await api.duplicateCheck(trimmed);
      setDuplicate(res.duplicate);
    } catch {
      setDuplicate(false);
    }
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (!name.trim()) {
      found.name = 'Name is required';
    } else if (name.trim().length > 120) {
      found.name = 'Name must be at most 120 characters';
    }
    if (!isValidOpeningBalanceInput(openingBalance)) {
      found.openingBalance = 'Enter a number with at most 2 decimal places (for example 0 or -250.50)';
    }
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }

    setSaving(true);
    setApiError(null);
    try {
      const body = {
        name: name.trim(),
        phone: phone.trim(),
        description: description.trim(),
        openingBalance: openingBalance.trim() === '' ? '0' : openingBalance.trim(),
        notes: notes.trim(),
      };
      const saved = person ? await api.updatePerson(person.id, body) : await api.createPerson(body);
      onSaved(saved);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        const map: Record<string, string> = {};
        err.fieldErrors.forEach((f) => (map[f.field] = f.message));
        setErrors(map);
      }
      setApiError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit person' : 'Add a person'} onClose={onClose}>
      <form onSubmit={submit} noValidate className="space-y-4">
        <ErrorBanner error={apiError} />
        <Field label="Name *" htmlFor="person-name" error={errors.name}>
          <input
            id="person-name"
            className={`input ${errors.name ? 'input-error' : ''}`}
            value={name}
            maxLength={120}
            autoFocus
            onChange={(e) => setName(e.target.value)}
            onBlur={checkDuplicate}
            placeholder="e.g. Ravi Kumar"
          />
          {duplicate && (
            <p className="mt-1 text-xs text-amber-700" role="status">
              You already have a ledger with this name. You can still create it (for example for two different people).
            </p>
          )}
        </Field>
        <Field label="Phone or reference (optional)" htmlFor="person-phone" error={errors.phone}>
          <input id="person-phone" className="input" value={phone} maxLength={30} onChange={(e) => setPhone(e.target.value)} />
        </Field>
        <Field label="Description (optional)" htmlFor="person-desc" error={errors.description}>
          <input id="person-desc" className="input" value={description} maxLength={255} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <Field
          label="Opening balance (optional)"
          htmlFor="person-opening"
          error={errors.openingBalance}
          hint="Money already owed/held before you start. Positive = in your favour, negative = against."
        >
          <input
            id="person-opening"
            className={`input ${errors.openingBalance ? 'input-error' : ''}`}
            inputMode="decimal"
            value={openingBalance}
            onChange={(e) => setOpeningBalance(e.target.value)}
          />
        </Field>
        <Field label="Notes (optional)" htmlFor="person-notes" error={errors.notes}>
          <textarea id="person-notes" className="input" rows={3} maxLength={2000} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
        <div className="flex justify-end gap-3 pt-2">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Create ledger'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
