import { TONE_CLASS, balanceTone, formatBalance, formatMagnitude, formatSigned } from '../lib/money';
import type { TxType } from '../lib/types';

/**
 * A transaction amount. Credit: green with a "+" sign. Debit: red with a "−" sign.
 * The sign is part of the text, so the meaning never depends on colour alone.
 */
export function AmountDisplay({ type, amount, className = '' }: { type: TxType; amount: string; className?: string }) {
  const tone = type === 'CREDIT' ? 'text-credit' : 'text-debit';
  const label = `${type === 'CREDIT' ? 'Credit' : 'Debit'} of ${formatMagnitude(amount)}`;
  return (
    <span
      className={`whitespace-nowrap font-semibold tabular-nums ${tone} ${className}`}
      data-testid={type === 'CREDIT' ? 'amount-credit' : 'amount-debit'}
      aria-label={label}
    >
      {formatSigned(type, amount)}
    </span>
  );
}

/** A balance: positive green, negative red (with "−"), zero neutral. */
export function BalanceDisplay({ value, className = '' }: { value: string; className?: string }) {
  const tone = balanceTone(value);
  return (
    <span
      className={`whitespace-nowrap font-semibold tabular-nums ${TONE_CLASS[tone]} ${className}`}
      data-testid={`balance-${tone}`}
    >
      {formatBalance(value)}
    </span>
  );
}
