import { describe, expect, it } from 'vitest';
import {
  balanceTone,
  formatBalance,
  formatMagnitude,
  formatSigned,
  groupIndian,
  isValidAmountInput,
  isValidOpeningBalanceInput,
} from './money';

describe('formatSigned', () => {
  it('shows credits with a plus sign', () => {
    expect(formatSigned('CREDIT', '1000.00')).toBe('+₹1,000.00');
    expect(formatSigned('CREDIT', '1000')).toBe('+₹1,000.00');
  });

  it('shows debits with a real minus sign', () => {
    expect(formatSigned('DEBIT', '1000.00')).toBe('−₹1,000.00');
    expect(formatSigned('DEBIT', '1000.00')).not.toContain('-');
  });
});

describe('formatBalance', () => {
  it('formats positive, negative and zero balances', () => {
    expect(formatBalance('5000.00')).toBe('₹5,000.00');
    expect(formatBalance('-300.5')).toBe('−₹300.50');
    expect(formatBalance('0.00')).toBe('₹0.00');
    expect(formatBalance('-0.00')).toBe('₹0.00');
  });

  it('groups digits the Indian way', () => {
    expect(formatBalance('100000')).toBe('₹1,00,000.00');
    expect(formatBalance('12345678.9')).toBe('₹1,23,45,678.90');
    expect(groupIndian('999')).toBe('999');
    expect(groupIndian('1000')).toBe('1,000');
  });

  it('keeps full precision for huge values (no floating point)', () => {
    expect(formatMagnitude('999999999999999.99')).toBe('₹99,99,99,99,99,99,999.99');
  });

  it('rejects anything that is not a decimal string', () => {
    expect(() => formatBalance('abc')).toThrow();
    expect(() => formatBalance('1e5')).toThrow();
  });
});

describe('balanceTone', () => {
  it('maps the sign to a tone', () => {
    expect(balanceTone('0.01')).toBe('positive');
    expect(balanceTone('-0.01')).toBe('negative');
    expect(balanceTone('0.00')).toBe('zero');
    expect(balanceTone('-0.00')).toBe('zero');
  });
});

describe('input validation', () => {
  it('accepts positive amounts with up to two decimals', () => {
    ['1', '0.01', '100.5', '1234567.89', '999999999999999.99'].forEach((v) => expect(isValidAmountInput(v)).toBe(true));
  });

  it('rejects zero, negatives, extra decimals and non-numbers', () => {
    ['', '0', '0.00', '-5', '1.234', 'abc', '1,000', '1e3', '1000000000000000'].forEach((v) =>
      expect(isValidAmountInput(v)).toBe(false),
    );
  });

  it('accepts optional signed opening balances', () => {
    ['', '0', '-250.50', '1000'].forEach((v) => expect(isValidOpeningBalanceInput(v)).toBe(true));
    ['abc', '1.234', '--5'].forEach((v) => expect(isValidOpeningBalanceInput(v)).toBe(false));
  });
});
