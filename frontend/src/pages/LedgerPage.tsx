import { useCallback, useEffect, useState } from 'react';
import { Download, Minus, Plus } from 'lucide-react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { formatMagnitude } from '../lib/money';
import type { Page, Person, Transaction, TxFilters, TxType } from '../lib/types';
import { BalanceDisplay } from '../components/AmountDisplay';
import { PersonModal } from '../components/PersonModal';
import { ReverseModal } from '../components/ReverseModal';
import { TransactionDetailModal } from '../components/TransactionDetailModal';
import { TransactionFormModal } from '../components/TransactionFormModal';
import { TransactionTable } from '../components/TransactionTable';
import { EmptyState, ErrorBanner, Spinner } from '../components/ui';

const PAGE_SIZE = 15;

export function LedgerPage() {
  const { personId = '' } = useParams();
  const { user } = useAuth();
  const timezone = user?.timezone ?? 'Asia/Kolkata';

  const [person, setPerson] = useState<Person | null>(null);
  const [page, setPage] = useState<Page<Transaction> | null>(null);
  const [pageIndex, setPageIndex] = useState(0);
  const [filters, setFilters] = useState<TxFilters>({ type: '', sortBy: 'date', direction: 'desc' });
  const [searchText, setSearchText] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const location = useLocation();
  const [notice, setNotice] = useState<string | null>(
    (location.state as { notice?: string } | null)?.notice ?? null,
  );
  const [form, setForm] = useState<{ type: TxType; correcting?: Transaction } | null>(null);
  const [reversing, setReversing] = useState<Transaction | null>(null);
  const [details, setDetails] = useState<Transaction | null>(null);
  const [editingPerson, setEditingPerson] = useState(false);

  // debounce the free-text search so every keystroke is not a request
  useEffect(() => {
    const handle = setTimeout(() => {
      setFilters((f) => (f.search === searchText ? f : { ...f, search: searchText }));
      setPageIndex(0);
    }, 300);
    return () => clearTimeout(handle);
  }, [searchText]);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [p, txs] = await Promise.all([
        api.getPerson(personId),
        api.listTransactions(personId, filters, pageIndex, PAGE_SIZE),
      ]);
      setPerson(p);
      setPage(txs);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [personId, filters, pageIndex]);

  useEffect(() => {
    void load();
  }, [load]);

  const updateFilter = (patch: Partial<TxFilters>) => {
    setFilters((f) => ({ ...f, ...patch }));
    setPageIndex(0);
  };

  const clearFilters = () => {
    setSearchText('');
    setFilters({ type: '', sortBy: 'date', direction: 'desc' });
    setPageIndex(0);
  };

  const toggleArchive = async () => {
    if (!person) return;
    const message = person.archived
      ? `Restore ${person.name}'s ledger to your dashboard?`
      : `Archive ${person.name}'s ledger? It will be hidden from the dashboard and locked against new entries. History is kept and you can restore it any time.`;
    if (!window.confirm(message)) return;
    try {
      setPerson(person.archived ? await api.restorePerson(person.id) : await api.archivePerson(person.id));
    } catch (err) {
      setError(err);
    }
  };

  const refreshAfterChange = (message: string) => {
    setForm(null);
    setReversing(null);
    setDetails(null);
    setNotice(message);
    setLoading(true);
    // The balance and history always come back from the server; nothing is updated optimistically.
    void load();
  };

  if (loading && !person) {
    return <Spinner />;
  }
  if (!person) {
    return (
      <div>
        <ErrorBanner error={error} />
        <Link to="/" className="btn-link">
          ← Back to dashboard
        </Link>
      </div>
    );
  }

  const filtersActive = Boolean(filters.type || filters.from || filters.to || filters.search);
  const exportFilters = { ...filters, direction: 'asc' as const };

  return (
    <div>
      <div className="no-print mb-4">
        <Link to="/" className="btn-link">
          ← All ledgers
        </Link>
      </div>

      <ErrorBanner error={error} />
      {notice && (
        <div role="status" className="mb-4 flex items-start justify-between gap-3 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800">
          <span>{notice}</span>
          <button type="button" className="font-medium underline" onClick={() => setNotice(null)}>
            Dismiss
          </button>
        </div>
      )}

      {/* Balance header */}
      <section className="card mb-6 p-5 sm:p-6" aria-label="Ledger balance">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h1 className="truncate text-2xl font-bold text-slate-900">{person.name}</h1>
            <p className="text-sm text-slate-500">
              {[person.phone, person.description].filter(Boolean).join(' · ') || 'No details added'}
              {person.archived && <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">Archived</span>}
            </p>
          </div>
          <div className="no-print flex flex-wrap gap-3 text-sm">
            <button type="button" className="btn-link" onClick={() => setEditingPerson(true)}>
              Edit
            </button>
            <Link to={`/people/${person.id}/reports`} className="btn-link">
              Reports
            </Link>
            <button type="button" className="btn-link" onClick={toggleArchive}>
              {person.archived ? 'Restore' : 'Archive'}
            </button>
          </div>
        </div>

        <div className="mt-5 text-center">
          <div className="text-sm font-semibold uppercase tracking-widest text-slate-500">Total balance</div>
          <div className="mt-1 text-5xl sm:text-6xl" data-testid="current-balance">
            <BalanceDisplay value={person.currentBalance} />
          </div>
          <div className="mt-1 text-xs text-slate-500">Opening balance + total credits − total debits</div>
        </div>

        <dl className="mt-5 grid grid-cols-2 gap-3 border-t border-slate-100 pt-4 text-center text-sm sm:grid-cols-4">
          <div>
            <dt className="text-slate-500">Opening balance</dt>
            <dd className="mt-0.5 font-semibold tabular-nums text-slate-700">
              <BalanceDisplay value={person.openingBalance} />
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Total credits</dt>
            <dd className="mt-0.5 font-semibold tabular-nums text-credit">+{formatMagnitude(person.totalCredits)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Total debits</dt>
            <dd className="mt-0.5 font-semibold tabular-nums text-debit">{'−'}{formatMagnitude(person.totalDebits)}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Transactions</dt>
            <dd className="mt-0.5 font-semibold tabular-nums text-slate-700" data-testid="tx-count">{person.transactionCount}</dd>
          </div>
        </dl>

        {/* Credit and Debit side by side */}
        <div className="no-print mt-5 grid grid-cols-2 gap-3">
          <button type="button" className="btn-credit py-4 text-base" disabled={person.archived} onClick={() => setForm({ type: 'CREDIT' })}>
            <Plus size={20} aria-hidden="true" /> Add Credit
          </button>
          <button type="button" className="btn-debit py-4 text-base" disabled={person.archived} onClick={() => setForm({ type: 'DEBIT' })}>
            <Minus size={20} aria-hidden="true" /> Add Debit
          </button>
        </div>
        {person.archived && <p className="mt-2 text-center text-xs text-slate-500">This ledger is archived. Restore it to add entries.</p>}
      </section>

      {/* Filters */}
      <section className="no-print mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-6" aria-label="Filter transactions">
        <input
          className="input lg:col-span-2"
          type="search"
          placeholder="Search name, description, reference…"
          aria-label="Search transactions"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        <select className="input" aria-label="Filter by type" value={filters.type ?? ''} onChange={(e) => updateFilter({ type: e.target.value as TxType | '' })}>
          <option value="">All types</option>
          <option value="CREDIT">Credits only</option>
          <option value="DEBIT">Debits only</option>
        </select>
        <input className="input" type="date" aria-label="From date" value={filters.from ?? ''} onChange={(e) => updateFilter({ from: e.target.value || undefined })} />
        <input className="input" type="date" aria-label="To date" value={filters.to ?? ''} onChange={(e) => updateFilter({ to: e.target.value || undefined })} />
        <select
          className="input"
          aria-label="Sort order"
          value={`${filters.sortBy}-${filters.direction}`}
          onChange={(e) => {
            const [sortBy, direction] = e.target.value.split('-') as ['date' | 'amount', 'asc' | 'desc'];
            updateFilter({ sortBy, direction });
          }}
        >
          <option value="date-desc">Newest first</option>
          <option value="date-asc">Oldest first</option>
          <option value="amount-desc">Largest amount</option>
          <option value="amount-asc">Smallest amount</option>
        </select>
      </section>

      <section className="card overflow-hidden" aria-label="Transaction history">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
          <h2 className="font-semibold text-slate-900">
            Transactions {page && <span className="font-normal text-slate-500">({page.totalItems})</span>}
          </h2>
          <div className="no-print flex gap-3 text-sm">
            {filtersActive && (
              <button type="button" className="btn-link" onClick={clearFilters}>
                Clear filters
              </button>
            )}
            <a className="btn-link inline-flex items-center gap-1" href={api.exportUrl(person.id, exportFilters)} download>
              <Download size={14} aria-hidden="true" /> Export CSV
            </a>
          </div>
        </div>

        {loading && !page ? (
          <Spinner />
        ) : page && page.items.length > 0 ? (
          <>
            <TransactionTable
              rows={page.items}
              timezone={timezone}
              writable={!person.archived}
              onDetails={setDetails}
              onReverse={setReversing}
              onCorrect={(t) => setForm({ type: t.type, correcting: t })}
            />
            {filtersActive && (
              <p className="border-t border-slate-100 px-4 py-2 text-xs text-slate-500">
                Balances shown are the ledger balance after each entry, not the total of the filtered rows.
              </p>
            )}
            {page.totalPages > 1 && (
              <div className="no-print flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm">
                <button type="button" className="btn-secondary" disabled={pageIndex === 0} onClick={() => setPageIndex((i) => Math.max(0, i - 1))}>
                  Previous
                </button>
                <span className="text-slate-600">
                  Page {page.page + 1} of {page.totalPages}
                </span>
                <button type="button" className="btn-secondary" disabled={pageIndex + 1 >= page.totalPages} onClick={() => setPageIndex((i) => i + 1)}>
                  Next
                </button>
              </div>
            )}
          </>
        ) : (
          <EmptyState title={filtersActive ? 'No transactions match your filters' : 'No transactions yet'}>
            {!filtersActive && 'Use the Credit and Debit buttons above to record the first one.'}
          </EmptyState>
        )}
      </section>

      {form && (
        <TransactionFormModal
          person={person}
          initialType={form.type}
          correcting={form.correcting}
          onClose={() => setForm(null)}
          onSaved={() =>
            refreshAfterChange(
              form.correcting
                ? 'Correction posted. The original entry is preserved, with a linked reversal and the corrected entry.'
                : `${form.type === 'CREDIT' ? 'Credit' : 'Debit'} saved. The balance below comes from the server.`,
            )
          }
        />
      )}
      {reversing && (
        <ReverseModal
          transaction={reversing}
          onClose={() => setReversing(null)}
          onDone={() => refreshAfterChange('Transaction reversed. The original is kept and linked to its reversal.')}
        />
      )}
      {details && (
        <TransactionDetailModal
          transaction={details}
          timezone={timezone}
          canEdit={!person.archived}
          onClose={() => setDetails(null)}
          onSaved={() => refreshAfterChange('Transaction details updated. The change is recorded in the audit trail.')}
        />
      )}
      {editingPerson && (
        <PersonModal
          person={person}
          onClose={() => setEditingPerson(false)}
          onSaved={(p) => {
            setPerson(p);
            setEditingPerson(false);
            setNotice('Person details saved.');
            void load();
          }}
        />
      )}
    </div>
  );
}
