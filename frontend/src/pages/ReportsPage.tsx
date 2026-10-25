import { useCallback, useEffect, useState } from 'react';
import { Download, Printer } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { monthRange, formatMonth, yearRange } from '../lib/dates';
import { MINUS, formatBalance, formatMagnitude } from '../lib/money';
import type { Person, Statement, TxFilters, TxType } from '../lib/types';
import { BalanceDisplay } from '../components/AmountDisplay';
import { TransactionTable } from '../components/TransactionTable';
import { EmptyState, ErrorBanner, Spinner } from '../components/ui';

function Stat({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="card px-4 py-3">
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-1 text-lg font-semibold tabular-nums">{children}</div>
    </div>
  );
}

export function ReportsPage() {
  const { personId = '' } = useParams();
  const { user } = useAuth();
  const timezone = user?.timezone ?? 'Asia/Kolkata';

  const [person, setPerson] = useState<Person | null>(null);
  const [statement, setStatement] = useState<Statement | null>(null);
  const [filters, setFilters] = useState<TxFilters>({ type: '', direction: 'asc' });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, s] = await Promise.all([api.getPerson(personId), api.reportStatement(personId, filters)]);
      setPerson(p);
      setStatement(s);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [personId, filters]);

  useEffect(() => {
    void load();
  }, [load]);

  const preset = (range: { from?: string; to?: string }) => setFilters((f) => ({ ...f, from: range.from, to: range.to }));
  const s = statement?.summary;

  return (
    <div>
      <div className="no-print mb-4 flex items-center justify-between">
        <Link to={`/people/${personId}`} className="btn-link">
          ← Back to ledger
        </Link>
        <button type="button" className="btn-secondary" onClick={() => window.print()}>
          <Printer size={16} aria-hidden="true" /> Print statement
        </button>
      </div>

      <h1 className="text-2xl font-bold text-slate-900">{person ? `${person.name}: reports` : 'Reports'}</h1>
      <p className="mb-4 text-sm text-slate-500">
        Period summary, monthly breakdown and statement. Every figure is calculated on the server in {s?.timezone ?? timezone}.
      </p>

      <ErrorBanner error={error} />

      <section className="no-print card mb-6 p-4" aria-label="Report filters">
        <div className="mb-3 flex flex-wrap gap-2">
          <button type="button" className="btn-secondary" onClick={() => preset(monthRange(0))}>This month</button>
          <button type="button" className="btn-secondary" onClick={() => preset(monthRange(-1))}>Last month</button>
          <button type="button" className="btn-secondary" onClick={() => preset(yearRange())}>This year</button>
          <button type="button" className="btn-secondary" onClick={() => preset({})}>All time</button>
        </div>
        <div className="grid gap-3 sm:grid-cols-4">
          <div>
            <label htmlFor="rep-from" className="label">From</label>
            <input id="rep-from" type="date" className="input" value={filters.from ?? ''} onChange={(e) => setFilters((f) => ({ ...f, from: e.target.value || undefined }))} />
          </div>
          <div>
            <label htmlFor="rep-to" className="label">To</label>
            <input id="rep-to" type="date" className="input" value={filters.to ?? ''} onChange={(e) => setFilters((f) => ({ ...f, to: e.target.value || undefined }))} />
          </div>
          <div>
            <label htmlFor="rep-type" className="label">Type</label>
            <select id="rep-type" className="input" value={filters.type ?? ''} onChange={(e) => setFilters((f) => ({ ...f, type: e.target.value as TxType | '' }))}>
              <option value="">Credits and debits</option>
              <option value="CREDIT">Credits only</option>
              <option value="DEBIT">Debits only</option>
            </select>
          </div>
          <div>
            <label htmlFor="rep-search" className="label">Search</label>
            <input id="rep-search" className="input" placeholder="Name, description…" value={filters.search ?? ''} onChange={(e) => setFilters((f) => ({ ...f, search: e.target.value }))} />
          </div>
        </div>
        <div className="mt-3 flex justify-end">
          <a className="btn-primary" href={api.exportUrl(personId, filters)} download>
            <Download size={16} aria-hidden="true" /> Export CSV
          </a>
        </div>
      </section>

      {loading && !statement ? (
        <Spinner />
      ) : s ? (
        <>
          <p className="mb-3 text-sm font-medium text-slate-700" data-testid="report-period">
            Reporting period:{' '}
            {s.from || s.to ? `${s.from ?? 'beginning'} to ${s.to ?? 'today'}` : 'All time'} ({s.timezone})
          </p>
          <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat label="Opening balance (period)"><BalanceDisplay value={s.periodOpeningBalance} /></Stat>
            <Stat label="Total credits"><span className="text-credit">+{formatMagnitude(s.totalCredits)}</span></Stat>
            <Stat label="Total debits"><span className="text-debit">{'−'}{formatMagnitude(s.totalDebits)}</span></Stat>
            <Stat label="Closing balance"><BalanceDisplay value={s.closingBalance} /></Stat>
          </div>
          <section className="card mb-6 px-4 py-3 text-sm" aria-label="Balance reconciliation" data-testid="reconciliation">
            <h2 className="mb-1 font-semibold text-slate-900">Balance reconciliation</h2>
            <p className="tabular-nums text-slate-700">
              {formatBalance(s.periodOpeningBalance)} (opening) + {formatMagnitude(s.totalCredits)} (credits) {MINUS}{' '}
              {formatMagnitude(s.totalDebits)} (debits) = <strong>{formatBalance(s.closingBalance)}</strong> (closing), from{' '}
              {s.transactionCount} entr{s.transactionCount === 1 ? 'y' : 'ies'}.
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Current ledger balance (all time): <BalanceDisplay value={s.currentLedgerBalance} />. The closing balance equals it when
              the period runs up to today.
            </p>
          </section>

          <section className="card mb-6 overflow-hidden" aria-label="Monthly summary">
            <h2 className="border-b border-slate-100 px-4 py-3 font-semibold text-slate-900">Monthly summary</h2>
            {s.monthly.length === 0 ? (
              <EmptyState title="No activity in this period" />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-sm">
                  <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                    <tr>
                      <th scope="col" className="px-4 py-2 text-left">Month</th>
                      <th scope="col" className="px-4 py-2 text-right">Opening</th>
                      <th scope="col" className="px-4 py-2 text-right">Credits</th>
                      <th scope="col" className="px-4 py-2 text-right">Debits</th>
                      <th scope="col" className="px-4 py-2 text-right">Closing</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {s.monthly.map((m) => (
                      <tr key={m.month}>
                        <td className="px-4 py-2 font-medium">{formatMonth(m.month)}</td>
                        <td className="px-4 py-2 text-right"><BalanceDisplay value={m.openingBalance} /></td>
                        <td className="px-4 py-2 text-right font-semibold tabular-nums text-credit">+{formatMagnitude(m.totalCredits)}</td>
                        <td className="px-4 py-2 text-right font-semibold tabular-nums text-debit">{'−'}{formatMagnitude(m.totalDebits)}</td>
                        <td className="px-4 py-2 text-right"><BalanceDisplay value={m.closingBalance} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="card overflow-hidden" aria-label="Statement">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
              <h2 className="font-semibold text-slate-900">Statement ({statement?.filteredCount ?? 0})</h2>
              {statement && statement.filteredCount > 0 && (
                <span className="text-sm text-slate-600">
                  <span className="font-semibold text-credit">+{formatMagnitude(statement.filteredCredits)}</span>
                  {'  '}
                  <span className="font-semibold text-debit">{'−'}{formatMagnitude(statement.filteredDebits)}</span>
                </span>
              )}
            </div>
            {statement && statement.transactions.length > 0 ? (
              <TransactionTable rows={statement.transactions} timezone={timezone} />
            ) : (
              <EmptyState title="No transactions match these filters" />
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
