// Money display helpers. All amounts are decimal strings from the server and are formatted with
// string maths only (no Number / floating point), using Indian digit grouping: 1,00,000.00.

export const MINUS = '−'; // true minus sign, not a hyphen
export const RUPEE = '₹';

export type Tone = 'positive' | 'negative' | 'zero';

interface Parts {
  negative: boolean;
  whole: string;
  fraction: string;
}

function parse(value: string): Parts {
  const m = /^(-)?(\d+)(?:\.(\d+))?$/.exec(value.trim());
  if (!m) {
    throw new Error(`Not a valid decimal amount: "${value}"`);
  }
  const whole = m[2].replace(/^0+(?=\d)/, '');
  const fraction = (m[3] ?? '').padEnd(2, '0').slice(0, 2);
  const isZero = /^0*$/.test(whole) && /^0*$/.test(fraction);
  return { negative: m[1] === '-' && !isZero, whole, fraction };
}

/** 1234567 -> "12,34,567" (last three digits, then groups of two). */
export function groupIndian(whole: string): string {
  if (whole.length <= 3) {
    return whole;
  }
  const lastThree = whole.slice(-3);
  let rest = whole.slice(0, -3);
  const groups: string[] = [];
  while (rest.length > 2) {
    groups.unshift(rest.slice(-2));
    rest = rest.slice(0, -2);
  }
  if (rest.length > 0) {
    groups.unshift(rest);
  }
  return `${groups.join(',')},${lastThree}`;
}

/** "1000" -> "₹1,000.00" (no sign; the magnitude only). */
export function formatMagnitude(value: string): string {
  const p = parse(value);
  return `${RUPEE}${groupIndian(p.whole)}.${p.fraction}`;
}

/** Credit: "+₹1,000.00". Debit: "−₹1,000.00". */
export function formatSigned(type: 'CREDIT' | 'DEBIT', amount: string): string {
  return `${type === 'CREDIT' ? '+' : MINUS}${formatMagnitude(amount)}`;
}

/** Balances: positive "₹5,000.00", negative "−₹300.00", zero "₹0.00". */
export function formatBalance(value: string): string {
  const p = parse(value);
  return `${p.negative ? MINUS : ''}${RUPEE}${groupIndian(p.whole)}.${p.fraction}`;
}

export function balanceTone(value: string): Tone {
  const p = parse(value);
  if (/^0*$/.test(p.whole) && /^0*$/.test(p.fraction)) {
    return 'zero';
  }
  return p.negative ? 'negative' : 'positive';
}

export const TONE_CLASS: Record<Tone, string> = {
  positive: 'text-credit',
  negative: 'text-debit',
  zero: 'text-slate-600',
};

/** Client-side check mirroring the server: up to 15 whole digits, up to 2 decimals, > 0. */
export function isValidAmountInput(input: string): boolean {
  const s = input.trim();
  if (!/^\d{1,15}(\.\d{1,2})?$/.test(s)) {
    return false;
  }
  return /[1-9]/.test(s);
}

/** Opening balance input: optional minus, up to 15 whole digits, up to 2 decimals (0 allowed). */
export function isValidOpeningBalanceInput(input: string): boolean {
  const s = input.trim();
  return s === '' || /^-?\d{1,15}(\.\d{1,2})?$/.test(s);
}
