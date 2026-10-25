import { useCallback, useEffect, useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { formatDate } from '../lib/dates';
import { formatMagnitude } from '../lib/money';
import type { Overview, Person } from '../lib/types';
import { BalanceDisplay } from '../components/AmountDisplay';
import { PersonModal } from '../components/PersonModal';
import { EmptyState, ErrorBanner, Spinner } from '../components/ui';

function StatCard({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="card px-4 py-3">
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 text-xl">{children}</div>
    </div>
  );
}

export function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [people, setPeople] = useState<Person[]>([]);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [showArchived, setShowArchived] = useState(false);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [adding, setAdding] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [list, ov] = await Promise.all([api.listPeople(showArchived ? 'archived' : 'active'), api.overview()]);
      setPeople(list);
      setOverview(ov);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [showArchived]);

  useEffect(() => {
    setLoading(true);
    void load();
  }, [load]);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return q ? people.filter((p) => p.name.toLowerCase().includes(q) || (p.phone ?? '').toLowerCase().includes(q)) : people;
  }, [people, search]);

  return (
    <div>
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Your ledgers</h1>
          <p className="text-sm text-slate-500">One ledger per person. Open a ledger to add credits and debits.</p>
        </div>
        <button type="button" className="btn-primary hidden sm:inline-flex" onClick={() => setAdding(true)}>
          <Plus size={18} aria-hidden="true" /> Add Person
        </button>
      </div>

      <ErrorBanner error={error} />

      {overview && !showArchived && (
        <section aria-label="Aggregate totals" className="mb-6">
          <p className="mb-2 text-xs text-slate-500">
            Aggregate totals across your active ledgers. These are combined figures, not money belonging to any one person.
          </p>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard label="Active ledgers">
              <span className="font-semibold">{overview.activeLedgers}</span>
            </StatCard>
            <StatCard label="Aggregate credits">
              <span className="font-semibold text-credit">+{formatMagnitude(overview.totalCredits)}</span>
            </StatCard>
            <StatCard label="Aggregate debits">
              <span className="font-semibold text-debit">{'−'}{formatMagnitude(overview.totalDebits)}</span>
            </StatCard>
            <StatCard label="Net balance (all)">
              <BalanceDisplay value={overview.netBalance} />
            </StatCard>
          </div>
        </section>
      )}

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <input
          className="input max-w-xs"
          type="search"
          placeholder="Search people…"
          aria-label="Search people"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Show archived
        </label>
      </div>

      {loading ? (
        <Spinner />
      ) : visible.length === 0 ? (
        <div className="card">
          {people.length === 0 ? (
            <EmptyState title={showArchived ? 'No archived ledgers' : 'No ledgers yet'}>
              {!showArchived && (
                <>
                  Press the <strong>+</strong> button to create the first ledger, for example for someone you lend to or borrow from.
                </>
              )}
            </EmptyState>
          ) : (
            <EmptyState title="No one matches your search" />
          )}
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visible.map((p) => (
            <li key={p.id}>
              <Link
                to={`/people/${p.id}`}
                className="card block p-4 transition hover:border-brand hover:shadow-md focus:outline-none focus-visible:ring-2 focus-visible:ring-brand"
                data-testid="person-card"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="truncate text-base font-semibold text-slate-900">{p.name}</div>
                    {p.phone && <div className="truncate text-xs text-slate-500">{p.phone}</div>}
                  </div>
                  {p.archived && <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">Archived</span>}
                </div>
                <div className="mt-3 text-xs font-medium uppercase tracking-wide text-slate-500">Current balance</div>
                <div className="text-2xl">
                  <BalanceDisplay value={p.currentBalance} />
                </div>
                <div className="mt-2 flex justify-between text-sm">
                  <span className="font-semibold tabular-nums text-credit" title="Total credits">
                    +{formatMagnitude(p.totalCredits)}
                  </span>
                  <span className="font-semibold tabular-nums text-debit" title="Total debits">
                    {'−'}{formatMagnitude(p.totalDebits)}
                  </span>
                </div>
                <div className="mt-1 text-xs text-slate-500">
                  {p.transactionCount === 0
                    ? 'No transactions yet'
                    : `${p.transactionCount} transaction${p.transactionCount === 1 ? '' : 's'}${
                        p.lastTransactionAt ? ` · last ${formatDate(p.lastTransactionAt, user?.timezone)}` : ''
                      }`}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {/* Floating "+" button (always visible, thumb-reachable on phones) */}
      <button
        type="button"
        onClick={() => setAdding(true)}
        aria-label="Add a person (create a new ledger)"
        className="no-print fixed bottom-6 right-6 flex h-14 w-14 items-center justify-center rounded-full bg-brand text-white shadow-lg hover:bg-brand-light focus:outline-none focus-visible:ring-4 focus-visible:ring-brand/40"
      >
        <Plus size={28} aria-hidden="true" />
      </button>

      {adding && (
        <PersonModal
          onClose={() => setAdding(false)}
          onSaved={(p) => {
            setAdding(false);
            navigate(`/people/${p.id}`, { state: { notice: `Ledger created for ${p.name}. Add the first credit or debit below.` } });
          }}
        />
      )}
    </div>
  );
}
