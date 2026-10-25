import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import { ErrorBanner, Field } from '../components/ui';

function Shell({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-brand to-brand-light px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center text-white">
          <h1 className="text-3xl font-bold tracking-tight">LedgerMate</h1>
          <p className="mt-1 text-sm text-blue-100">Personal money audit: a separate credit and debit ledger for every person.</p>
        </div>
        <div className="card p-6">
          <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
          <p className="mb-5 mt-1 text-sm text-slate-500">{subtitle}</p>
          {children}
        </div>
      </div>
    </div>
  );
}

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Shell title="Log in" subtitle="Welcome back. Your ledgers are private to your account.">
      <form onSubmit={submit} className="space-y-4">
        <ErrorBanner error={error} />
        <Field label="Email" htmlFor="login-email">
          <input id="login-email" type="email" className="input" value={email} autoComplete="email" required autoFocus onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="login-password">
          <input id="login-password" type="password" className="input" value={password} autoComplete="current-password" required onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-600">
        New here?{' '}
        <Link to="/register" className="btn-link">
          Create an account
        </Link>
      </p>
    </Shell>
  );
}

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const found: Record<string, string> = {};
    if (password.length < 10) {
      found.password = 'Password must be at least 10 characters';
    }
    setFieldErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await register(email, password, displayName);
      navigate('/');
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.length > 0) {
        const map: Record<string, string> = {};
        err.fieldErrors.forEach((f) => (map[f.field] = f.message));
        setFieldErrors(map);
      }
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Shell title="Create your account" subtitle="Free and private. Use a password of at least 10 characters.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        <ErrorBanner error={error} />
        <Field label="Your name (optional)" htmlFor="reg-name" error={fieldErrors.displayName}>
          <input id="reg-name" className="input" value={displayName} maxLength={100} autoComplete="name" onChange={(e) => setDisplayName(e.target.value)} />
        </Field>
        <Field label="Email" htmlFor="reg-email" error={fieldErrors.email}>
          <input id="reg-email" type="email" className="input" value={email} autoComplete="email" required onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password" htmlFor="reg-password" error={fieldErrors.password}>
          <input id="reg-password" type="password" className="input" value={password} autoComplete="new-password" minLength={10} maxLength={72} required onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <button type="submit" className="btn-primary w-full" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-600">
        Already registered?{' '}
        <Link to="/login" className="btn-link">
          Log in
        </Link>
      </p>
    </Shell>
  );
}
