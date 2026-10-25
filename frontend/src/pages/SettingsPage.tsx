import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { ErrorBanner, Field } from '../components/ui';

const FALLBACK_ZONES = [
  'Asia/Kolkata', 'Asia/Dubai', 'Asia/Singapore', 'Asia/Tokyo', 'Europe/London', 'Europe/Berlin',
  'America/New_York', 'America/Chicago', 'America/Los_Angeles', 'Australia/Sydney', 'UTC',
];

function timeZones(current: string): string[] {
  const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
  let zones: string[] = [];
  try {
    zones = intl.supportedValuesOf?.('timeZone') ?? [];
  } catch {
    zones = [];
  }
  const list = zones.length > 0 ? zones : FALLBACK_ZONES;
  return list.includes(current) ? list : [current, ...list];
}

export function SettingsPage() {
  const { user, setUser } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [timezone, setTimezone] = useState(user?.timezone ?? 'Asia/Kolkata');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);
  const zones = useMemo(() => timeZones(user?.timezone ?? 'Asia/Kolkata'), [user?.timezone]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      setUser(await api.updateProfile({ displayName: displayName.trim(), timezone }));
      setSaved(true);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto max-w-xl">
      <Link to="/" className="btn-link">
        ← All ledgers
      </Link>
      <h1 className="mt-3 text-2xl font-bold text-slate-900">Settings</h1>
      <p className="mb-5 text-sm text-slate-500">Signed in as {user?.email}</p>

      <form onSubmit={submit} className="card space-y-4 p-5" noValidate>
        <ErrorBanner error={error} />
        {saved && (
          <div role="status" className="rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">
            Settings saved.
          </div>
        )}
        <Field label="Your name" htmlFor="set-name">
          <input id="set-name" className="input" value={displayName} maxLength={100} onChange={(e) => setDisplayName(e.target.value)} />
        </Field>
        <Field
          label="Timezone"
          htmlFor="set-tz"
          hint="Dates and times, date filters, reports and CSV exports use this timezone. Stored timestamps are always UTC."
        >
          <select id="set-tz" className="input" value={timezone} onChange={(e) => setTimezone(e.target.value)}>
            {zones.map((z) => (
              <option key={z} value={z}>
                {z}
              </option>
            ))}
          </select>
        </Field>
        <div className="flex justify-end">
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Save settings'}
          </button>
        </div>
      </form>
    </div>
  );
}
