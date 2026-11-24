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
  dashboard: { id: 'dashboard', eyebrow: 'TODAY' },
  focus: { id: 'focus', eyebrow: 'FOCUS' },
  tasks: { id: 'tasks', eyebrow: 'PLANNER' },
  habits: { id: 'habits', eyebrow: 'RITUALS' },
  projects: { id: 'projects', eyebrow: 'PROJECTS' },
  calendar: { id: 'calendar', eyebrow: 'CALENDAR' },
  analytics: { id: 'analytics', eyebrow: 'INSIGHT' },
  settings: { id: 'settings', eyebrow: 'STUDIO' },
  motivation: { id: 'motivation', eyebrow: 'MOMENTUM' },
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
