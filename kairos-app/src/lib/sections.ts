import type { NavTab } from '@/types/domain';
import { applyAppearance, rotateHue, type BackgroundId } from './theme';

/**
 * Section identity.
 *
 * The master plan asks every section to feel like its own place. Doing that by
 * hard-coding seven palettes would fight the user's accent choice, so each
 * section is defined as a *relationship* to the accent: a hue offset in OKLCH,
 * its own environment, and its own light position. Pick any accent and the
 * family still holds together; each room still reads as a different room.
 */
export interface SectionIdentity {
  id: NavTab;
  /** Latin eyebrow used above section titles. */
  eyebrow: string;
  /** OKLCH hue rotation applied to the user's accent. */
  hueShift: number;
  /** Environment used while this section is active. */
  atmosphere: BackgroundId;
  /** Where the atmospheric light sits (CSS position). */
  light: string;
}

export const SECTION_IDENTITIES: Record<NavTab, SectionIdentity> = {
  dashboard: {
    id: 'dashboard',
    eyebrow: 'TODAY',
    hueShift: 0,
    atmosphere: 'nebula',
    light: '50% -8%',
  },
  focus: {
    id: 'focus',
    eyebrow: 'FOCUS',
    hueShift: 14,
    atmosphere: 'obsidian',
    light: '50% 34%',
  },
  tasks: {
    id: 'tasks',
    eyebrow: 'PLANNER',
    hueShift: 26,
    atmosphere: 'alpine',
    light: '22% -6%',
  },
  habits: {
    id: 'habits',
    eyebrow: 'RITUALS',
    hueShift: -46,
    atmosphere: 'ember',
    light: '50% 104%',
  },
  calendar: {
    id: 'calendar',
    eyebrow: 'CALENDAR',
    hueShift: 44,
    atmosphere: 'aurora',
    light: '78% -4%',
  },
  analytics: {
    id: 'analytics',
    eyebrow: 'INSIGHT',
    hueShift: -96,
    atmosphere: 'graphite',
    light: '50% -10%',
  },
  settings: {
    id: 'settings',
    eyebrow: 'STUDIO',
    hueShift: 0,
    atmosphere: 'graphite',
    light: '50% -10%',
  },
};

export const SECTION_LIST: SectionIdentity[] = Object.values(SECTION_IDENTITIES);

/** The accent this section should paint with, derived from the user's accent. */
export function sectionAccent(baseAccent: string, section: NavTab): string {
  const identity = SECTION_IDENTITIES[section];
  return identity.hueShift === 0 ? baseAccent : rotateHue(baseAccent, identity.hueShift);
}

/**
 * Applies a section's identity on top of the user's appearance.
 *
 * The user's accent and environment remain the source of truth for *the app*;
 * this only derives the room they are standing in. The boot cache is never
 * written here, so a cold start always begins from the base appearance.
 */
export function applySectionAppearance(
  base: { accent: string; backgroundId: BackgroundId; motionLevel: 'standard' | 'cinematic' | 'ultra' },
  section: NavTab,
  identityEnabled = true
): void {
  const identity = SECTION_IDENTITIES[section] ?? SECTION_IDENTITIES.dashboard;
  const accent = identityEnabled ? sectionAccent(base.accent, section) : base.accent;
  applyAppearance({
    accent,
    backgroundId: identityEnabled ? identity.atmosphere : base.backgroundId,
    motionLevel: base.motionLevel,
  });

  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  root.dataset.section = section;
  root.style.setProperty('--section-light', identity.light);
}
