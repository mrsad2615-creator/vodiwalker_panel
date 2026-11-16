import { useEffect, useState } from 'react';
import { toEnDigits } from '@/lib/format';

interface NumericInputProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  fallback?: number;
  className?: string;
  ariaLabel?: string;
}

/**
 * Android/Persian-friendly numeric input:
 * - Allows full Backspace clearing to empty string while editing
 * - Accepts Persian (۰-۹), Arabic (٠-٩), and English (0-9) digits seamlessly
 * - Clamps cleanly to [min, max] on blur instead of fighting the user mid-keystroke
 */
export function NumericInput({
  value,
  onChange,
  min = 0,
  max = 99999,
  fallback,
  className = '',
  ariaLabel,
}: NumericInputProps) {
  const [draft, setDraft] = useState(String(value));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) {
      setDraft(String(value));
    }
  }, [value, focused]);

  const handleChange = (raw: string) => {
    const normalized = toEnDigits(raw).replace(/[^0-9]/g, '');
    setDraft(normalized);
    if (normalized !== '') {
      const parsed = Number(normalized);
      if (Number.isFinite(parsed)) {
        onChange(Math.min(max, parsed));
      }
    }
  };

  const handleBlur = () => {
    setFocused(false);
    const parsed = draft === '' ? (fallback ?? min) : Number(draft);
    const clamped = Math.max(min, Math.min(max, Number.isFinite(parsed) ? parsed : min));
    setDraft(String(clamped));
    onChange(clamped);
  };

  return (
    <input
      type="text"
      inputMode="numeric"
      pattern="[0-9]*"
      dir="ltr"
      aria-label={ariaLabel}
      value={draft}
      onFocus={() => setFocused(true)}
      onBlur={handleBlur}
      onChange={(e) => handleChange(e.target.value)}
      className={className}
    />
  );
}
