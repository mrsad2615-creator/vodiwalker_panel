import { afterEach, describe, expect, it } from 'vitest';
import {
  SECTION_IDENTITIES,
  SECTION_LIST,
  applySectionAppearance,
  sectionEyebrow,
} from '@/lib/sections';
import { hexToOklch } from '@/lib/theme';
import type { NavTab } from '@/types/domain';

const BASE = { accent: '#8B5CF6', backgroundId: 'obsidian' as const, motionLevel: 'cinematic' as const };

afterEach(() => {
  const root = document.documentElement;
  root.removeAttribute('data-section');
});

describe('section identity', () => {
  it('covers every nav tab with a unique Latin eyebrow', () => {
    const tabs: NavTab[] = [
      'dashboard',
      'focus',
      'tasks',
      'habits',
      'calendar',
      'analytics',
      'settings',
    ];
    expect(SECTION_LIST).toHaveLength(tabs.length);

    const eyebrows = tabs.map((tab) => {
      expect(SECTION_IDENTITIES[tab].id).toBe(tab);
      expect(sectionEyebrow(tab)).toMatch(/^[A-Z]+$/);
      return SECTION_IDENTITIES[tab].eyebrow;
    });
    expect(new Set(eyebrows).size).toBe(tabs.length);
  });

  it('never introduces a second palette — one accent for the whole product', () => {
    for (const identity of SECTION_LIST) {
      applySectionAppearance(BASE, identity.id);
      const root = document.documentElement;

      expect(root.dataset.section).toBe(identity.id);
      expect(hexToOklch(root.style.getPropertyValue('--accent').trim()).h).toBeCloseTo(
        hexToOklch(BASE.accent).h,
        1
      );
      expect(root.dataset.bg).toBe(BASE.backgroundId);
      expect(root.dataset.motion).toBe(BASE.motionLevel);
    }
  });

  it('keeps the user environment and motion level intact', () => {
    const aurora = { accent: '#22C55E', backgroundId: 'aurora' as const, motionLevel: 'standard' as const };
    applySectionAppearance(aurora, 'analytics');
    const root = document.documentElement;

    expect(root.dataset.bg).toBe('aurora');
    expect(root.dataset.motion).toBe('standard');
    expect(hexToOklch(root.style.getPropertyValue('--accent').trim()).h).toBeCloseTo(
      hexToOklch(aurora.accent).h,
      1
    );
  });

  it('is idempotent — switching rooms twice changes nothing', () => {
    applySectionAppearance(BASE, 'habits');
    const first = document.documentElement.style.getPropertyValue('--accent');
    applySectionAppearance(BASE, 'habits');
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe(first);
  });
});
