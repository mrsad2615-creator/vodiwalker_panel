import type { ReactNode } from 'react';
import { toEnDigits } from '@/lib/format';

interface NumericProps {
  value: string | number;
  variant?: 'display' | 'geo';
  className?: string;
  suffix?: ReactNode;
  prefix?: ReactNode;
}

/**
 * Signature KAIROS numeral renderer:
 * Always renders crisp Western/English 0-9 tabular numerals using
 * Barlow Condensed (display) or Space Grotesk (geo), properly isolated in RTL Persian layouts.
 */
export function Numeric({
  value,
  variant = 'geo',
  className = '',
  suffix,
  prefix,
}: NumericProps) {
  const formatted = toEnDigits(value);
  return (
    <span
      dir="ltr"
      className={`${variant === 'display' ? 'num-display' : 'num-geo'} inline-flex items-baseline ${className}`}
    >
      {prefix}
      <span>{formatted}</span>
      {suffix}
    </span>
  );
}
