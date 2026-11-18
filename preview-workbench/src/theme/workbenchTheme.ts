import type { M3ColorScheme } from './m3Tokens';
import {
  M3_DARK_HIGH_CONTRAST_SCHEME,
  M3_DARK_SCHEME,
  M3_LIGHT_HIGH_CONTRAST_SCHEME,
  M3_LIGHT_SCHEME,
} from './m3Tokens';
import type { CSSProperties } from 'react';

/** The four audited M3 schemes exposed by the workbench switcher. */
export type SchemeId = 'dark' | 'light' | 'dark-hc' | 'light-hc';

export interface SchemeOption {
  readonly id: SchemeId;
  readonly label: string;
  readonly description: string;
  readonly scheme: M3ColorScheme;
}

export const SCHEME_OPTIONS: readonly SchemeOption[] = [
  {
    id: 'dark',
    label: 'Obsidian Cinema',
    description: 'Primary brand surface, 15/15 WCAG AA pairs',
    scheme: M3_DARK_SCHEME,
  },
  {
    id: 'light',
    label: 'Daylight Studio',
    description: 'Light default, 15/15 WCAG AA pairs',
    scheme: M3_LIGHT_SCHEME,
  },
  {
    id: 'dark-hc',
    label: 'Obsidian AAA',
    description: 'High contrast, min 11.07:1',
    scheme: M3_DARK_HIGH_CONTRAST_SCHEME,
  },
  {
    id: 'light-hc',
    label: 'Daylight AAA',
    description: 'High contrast, min 8.97:1',
    scheme: M3_LIGHT_HIGH_CONTRAST_SCHEME,
  },
];

/** Applies an M3 scheme to the document as CSS custom properties (source of truth for the UI). */
export function applyScheme(root: HTMLElement, scheme: M3ColorScheme, mode: 'dark' | 'light'): void {
  const variables: Record<keyof M3ColorScheme, string> = scheme;
  for (const [role, value] of Object.entries(variables)) {
    root.style.setProperty(`--md-sys-color-${kebab(role)}`, value);
  }
  // Extended (non-M3) semantic status roles used by telemetry chips.
  root.style.setProperty('--vodi-status-active', mode === 'dark' ? '#7CF2B0' : '#0F6B3A');
  root.style.setProperty('--vodi-status-warn', mode === 'dark' ? '#FFD27A' : '#7A4F00');
  root.style.setProperty('--vodi-status-error', scheme.error);
  root.style.setProperty('--vodi-glass-tint', mode === 'dark' ? '255, 255, 255' : '18, 18, 24');
  root.style.setProperty('--vodi-glass-strength', mode === 'dark' ? '0.055' : '0.62');
  root.dataset.schemeMode = mode;
}

/** M3 motion tokens (mirrors `VodiMotion` in `:core:designsystem`). */
export const MOTION = {
  spatialFast: { type: 'spring', stiffness: 900, damping: 42, mass: 0.7 },
  spatialExpressive: { type: 'spring', stiffness: 420, damping: 34, mass: 0.9 },
  effects: { type: 'spring', stiffness: 520, damping: 40, mass: 0.8 },
  emphasizedDecelerate: [0.05, 0.7, 0.1, 1] as const,
  emphasizedAccelerate: [0.3, 0, 0.8, 0.15] as const,
  standard: [0.2, 0, 0, 1] as const,
  durationShort: 0.18,
  durationMedium: 0.32,
  durationLong: 0.5,
} as const;

/** Glassmorphic surface recipe shared by every panel. */
export function glassStyle(strength = 1): CSSProperties {
  return {
    background: `rgba(var(--vodi-glass-tint), calc(var(--vodi-glass-strength) * ${strength}))`,
    backdropFilter: 'blur(22px) saturate(160%)',
    WebkitBackdropFilter: 'blur(22px) saturate(160%)',
    border: '1px solid color-mix(in oklab, var(--md-sys-color-outline-variant) 60%, transparent)',
    borderRadius: '28px',
    boxShadow:
      '0 24px 60px -24px rgba(0,0,0,0.55), inset 0 1px 0 color-mix(in oklab, var(--md-sys-color-on-surface) 12%, transparent)',
  };
}

function kebab(value: string): string {
  return value.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
}
