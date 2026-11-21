import type { NavTab } from '@/types/domain';
import { applyAppearance, type Appearance } from './theme';

/**
 * Section identity.
 *
 * The product is built on ONE accent and ONE canvas. Rooms are not told apart by
 * colour — that made the app feel like seven different apps stitched together,
 * and it fought the user's own colour choice. Rooms are told apart by
 * composition: the shape of the screen, and the uppercase Latin voice that
 * announces it.
 *
 * This module therefore owns exactly two facts per section: its eyebrow, and the
 * `data-section` attribute used for structural CSS. Nothing else is per-section.
 */
export interface SectionIdentity {
  id: NavTab;
  /** Uppercase Latin eyebrow used above section titles. */
  eyebrow: string;
}

export const SECTION_IDENTITIES: Record<NavTab, SectionIdentity> = {
  // Persian labels. The 2.x app announced every screen with 8.5px uppercase
  // Latin (+0.26em tracking), which is the exact texture that makes a Persian
  // interface feel foreign and machine-made. The room still has a name; it is
  // now the name its readers use.
  dashboard: { id: 'dashboard', eyebrow: 'امروز' },
  focus: { id: 'focus', eyebrow: 'تمرکز' },
  tasks: { id: 'tasks', eyebrow: 'برنامه' },
  habits: { id: 'habits', eyebrow: 'آیین‌ها' },
  calendar: { id: 'calendar', eyebrow: 'تقویم' },
  analytics: { id: 'analytics', eyebrow: 'بینش' },
  settings: { id: 'settings', eyebrow: 'استودیو' },
};

export const SECTION_LIST: SectionIdentity[] = Object.values(SECTION_IDENTITIES);

export function sectionEyebrow(section: NavTab): string {
  return (SECTION_IDENTITIES[section] ?? SECTION_IDENTITIES.dashboard).eyebrow;
}

/**
 * Applies the user's appearance unchanged and marks which room we are standing
 * in. The accent, environment and motion level are never touched here — that is
 * the whole point: one product, one palette.
 */
export function applySectionAppearance(base: Appearance, section: NavTab): void {
  applyAppearance(base);

  if (typeof document === 'undefined') return;
  document.documentElement.dataset.section = section;
}
