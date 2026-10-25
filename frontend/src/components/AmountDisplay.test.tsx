import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AmountDisplay, BalanceDisplay } from './AmountDisplay';

describe('AmountDisplay', () => {
  it('renders a credit as green with a + sign', () => {
    render(<AmountDisplay type="CREDIT" amount="1000.00" />);
    const el = screen.getByTestId('amount-credit');
    expect(el).toHaveTextContent('+₹1,000.00');
    expect(el.className).toContain('text-credit');
    expect(el).toHaveAttribute('aria-label', 'Credit of ₹1,000.00');
  });

  it('renders a debit as red with a minus sign', () => {
    render(<AmountDisplay type="DEBIT" amount="1000.00" />);
    const el = screen.getByTestId('amount-debit');
    expect(el).toHaveTextContent('−₹1,000.00');
    expect(el.className).toContain('text-debit');
  });
});

describe('BalanceDisplay', () => {
  it('colours positive green, negative red and zero neutral', () => {
    const { rerender } = render(<BalanceDisplay value="5000.00" />);
    expect(screen.getByTestId('balance-positive').className).toContain('text-credit');

    rerender(<BalanceDisplay value="-300.00" />);
    const negative = screen.getByTestId('balance-negative');
    expect(negative.className).toContain('text-debit');
    expect(negative).toHaveTextContent('−₹300.00');

    rerender(<BalanceDisplay value="0.00" />);
    const zero = screen.getByTestId('balance-zero');
    expect(zero.className).not.toContain('text-credit');
    expect(zero.className).not.toContain('text-debit');
  });
});
