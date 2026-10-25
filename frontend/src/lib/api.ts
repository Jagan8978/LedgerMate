import type {
  Overview,
  Page,
  Person,
  PersonInput,
  Statement,
  ReportSummary,
  Transaction,
  TransactionInput,
  TxFilters,
  User,
} from './types';

export interface FieldIssue {
  field: string;
  message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: FieldIssue[];

  constructor(status: number, message: string, fieldErrors: FieldIssue[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }

  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((f) => f.field === field)?.message;
  }
}

let onUnauthorized: (() => void) | null = null;

/** The auth provider registers a callback that sends the user back to the login page. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : null;
}

function query(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && String(v).trim() !== '') {
      sp.set(k, String(v));
    }
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  extraHeaders: Record<string, string> = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...extraHeaders };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) {
      headers['X-XSRF-TOKEN'] = csrf;
    }
  }

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check that the backend is running and try again.');
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    const err = (data ?? {}) as { message?: string; fieldErrors?: FieldIssue[] };
    if (response.status === 401 && !path.startsWith('/api/auth/login')) {
      onUnauthorized?.();
    }
    throw new ApiError(
      response.status,
      err.message ?? `Request failed (${response.status}).`,
      err.fieldErrors ?? [],
    );
  }
  return data as T;
}

function txParams(f: TxFilters): Record<string, string | undefined> {
  return {
    type: f.type || undefined,
    from: f.from,
    to: f.to,
    search: f.search,
    sortBy: f.sortBy,
    direction: f.direction,
  };
}

export const api = {
  // ---- auth
  register: (email: string, password: string, displayName: string) =>
    request<{ user: User }>('POST', '/api/auth/register', { email, password, displayName }),
  login: (email: string, password: string) =>
    request<{ user: User }>('POST', '/api/auth/login', { email, password }),
  logout: () => request<void>('POST', '/api/auth/logout'),
  me: () => request<User>('GET', '/api/auth/me'),
  updateProfile: (body: { displayName?: string; timezone?: string }) =>
    request<User>('PATCH', '/api/auth/me', body),

  // ---- people
  listPeople: (status: 'active' | 'archived' = 'active') =>
    request<Person[]>('GET', `/api/people${query({ status })}`),
  overview: () => request<Overview>('GET', '/api/people/overview'),
  getPerson: (id: string) => request<Person>('GET', `/api/people/${id}`),
  createPerson: (body: PersonInput) => request<Person>('POST', '/api/people', body),
  updatePerson: (id: string, body: PersonInput) => request<Person>('PUT', `/api/people/${id}`, body),
  archivePerson: (id: string) => request<Person>('PATCH', `/api/people/${id}/archive`),
  restorePerson: (id: string) => request<Person>('PATCH', `/api/people/${id}/restore`),
  duplicateCheck: (name: string) =>
    request<{ duplicate: boolean; matches: number }>('GET', `/api/people/duplicate-check${query({ name })}`),

  // ---- transactions
  listTransactions: (personId: string, filters: TxFilters, page: number, size: number) =>
    request<Page<Transaction>>(
      'GET',
      `/api/people/${personId}/transactions${query({ ...txParams(filters), page, size })}`,
    ),
  createTransaction: (personId: string, body: TransactionInput, idempotencyKey: string) =>
    request<Transaction>('POST', `/api/people/${personId}/transactions`, body, {
      'Idempotency-Key': idempotencyKey,
    }),
  updateDescription: (
    id: string,
    body: { description?: string; notes?: string; counterpartyName?: string; referenceNumber?: string; reason?: string },
  ) => request<Transaction>('PATCH', `/api/transactions/${id}/description`, body),
  reverseTransaction: (id: string, reason: string) =>
    request<Transaction>('POST', `/api/transactions/${id}/reverse`, { reason }),
  correctTransaction: (id: string, reason: string, replacement: TransactionInput) =>
    request<{ reversal: Transaction; correction: Transaction }>('POST', `/api/transactions/${id}/corrections`, {
      reason,
      replacement,
    }),

  // ---- reports
  reportSummary: (personId: string, from?: string, to?: string) =>
    request<ReportSummary>('GET', `/api/people/${personId}/reports/summary${query({ from, to })}`),
  reportStatement: (personId: string, filters: TxFilters) =>
    request<Statement>(
      'GET',
      `/api/people/${personId}/reports/transactions${query({ ...txParams(filters), direction: filters.direction ?? 'asc' })}`,
    ),
  exportUrl: (personId: string, filters: TxFilters) =>
    `/api/people/${personId}/reports/export${query({ ...txParams(filters), direction: filters.direction ?? 'asc' })}`,
};

/** A fresh key per form session lets the server ignore an accidental double submit. */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `k${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}
