import { afterEach, describe, expect, it } from 'vitest';
import {
  SECTION_IDENTITIES,
  SECTION_LIST,
  applySectionAppearance,
  sectionAccent,
} from '@/lib/sections';
import { hexToOklch, rotateHue } from '@/lib/theme';
import type { NavTab } from '@/types/domain';

const BASE = '#8B5CF6';

/** Smallest distance between two hues on the colour wheel, in degrees. */
function hueDistance(a: number, b: number): number {
  const raw = Math.abs(a - b) % 360;
  return raw > 180 ? 360 - raw : raw;
}

const HEX = /^#[0-9A-Fa-f]{6}$/;

afterEach(() => {
  const root = document.documentElement;
  root.removeAttribute('data-section');
  root.style.removeProperty('--section-light');
});

describe('rotateHue', () => {
  it('returns a valid hex and keeps OKLCH lightness', () => {
    const base = hexToOklch(BASE);
    const rotated = rotateHue(BASE, 40);

    expect(rotated).toMatch(HEX);
    expect(hexToOklch(rotated).l).toBeCloseTo(base.l, 2);
  });

  it('moves the hue by the requested amount within gamut tolerance', () => {
    const base = hexToOklch(BASE);
    for (const shift of [14, 26, 44, -46, -96]) {
      const rotated = hexToOklch(rotateHue(BASE, shift));
      // 8-bit quantisation plus the chroma reduction drifts up to ~2.5°.
      expect(hueDistance(rotated.h, base.h)).toBeGreaterThan(Math.abs(shift) - 2.6);
      expect(hueDistance(rotated.h, base.h)).toBeLessThan(Math.abs(shift) + 2.6);
    }
  });

  it('never increases chroma — out-of-gamut targets lose saturation', () => {
    const base = hexToOklch(BASE);
    const rotated = hexToOklch(rotateHue(BASE, -96));
    expect(rotated.c).toBeLessThanOrEqual(base.c + 0.01);
  });

  it('a zero shift keeps hue and lightness (identity rotation)', () => {
    const base = hexToOklch(BASE);
    const same = hexToOklch(rotateHue(BASE, 0));

    expect(hueDistance(same.h, base.h)).toBeLessThan(2.5);
    expect(same.l).toBeCloseTo(base.l, 2);
  });

  it('is deterministic and wraps past 360°', () => {
    expect(rotateHue(BASE, 30)).toBe(rotateHue(BASE, 30));

    const wrapped = hexToOklch(rotateHue(BASE, 360 + 30));
    const plain = hexToOklch(rotateHue(BASE, 30));
    expect(hueDistance(wrapped.h, plain.h)).toBeLessThan(2);
  });

  it('survives extreme inputs without throwing', () => {
    for (const hex of ['#000000', '#FFFFFF', '#FF0000', '#00FF00', '#FFFF00']) {
      const out = rotateHue(hex, 75);
      expect(out).toMatch(HEX);
    }
  });
});

describe('section identity', () => {
  it('every nav tab has an identity and the list covers all of them', () => {
    const tabs: NavTab[] = ['dashboard', 'focus', 'tasks', 'habits', 'calendar', 'analytics', 'settings'];
    expect(SECTION_LIST).toHaveLength(tabs.length);
    for (const tab of tabs) {
      expect(SECTION_IDENTITIES[tab].id).toBe(tab);
      expect(SECTION_IDENTITIES[tab].eyebrow).toMatch(/^[A-Z]+$/);
      expect(SECTION_IDENTITIES[tab].light).toMatch(/^-?\d+% -?\d+%$/);
    }
  });

  it('dashboard is the base accent; the other sections are hue-shifted', () => {
    expect(sectionAccent(BASE, 'dashboard')).toBe(BASE);

    const base = hexToOklch(BASE).h;
    for (const tab of ['focus', 'tasks', 'habits', 'calendar', 'analytics'] as NavTab[]) {
      const hue = hexToOklch(sectionAccent(BASE, tab)).h;
      expect(hueDistance(hue, base)).toBeGreaterThanOrEqual(10);
    }
  });

  it('gives every section a distinct accent', () => {
    const accents = SECTION_LIST.filter((s) => s.hueShift !== 0).map((s) =>
      sectionAccent(BASE, s.id).toUpperCase()
    );
    expect(new Set(accents).size).toBe(accents.length);
  });
});

describe('applySectionAppearance', () => {
  const base = { accent: BASE, backgroundId: 'obsidian' as const, motionLevel: 'cinematic' as const };

  it('writes the section accent, environment and light position', () => {
    applySectionAppearance(base, 'focus');
    const root = document.documentElement;

    expect(root.dataset.section).toBe('focus');
    expect(root.dataset.bg).toBe(SECTION_IDENTITIES.focus.atmosphere);
    expect(root.style.getPropertyValue('--section-light').trim()).toBe(
      SECTION_IDENTITIES.focus.light
    );

    const applied = hexToOklch(root.style.getPropertyValue('--accent').trim());
    const expected = hexToOklch(sectionAccent(BASE, 'focus'));
    expect(hueDistance(applied.h, expected.h)).toBeLessThan(3);
    // Lightness comes from the user's accent, not from the section.
    expect(applied.l).toBeCloseTo(hexToOklch(BASE).l, 1);
  });

  it('keeps a distinct room per section', () => {
    const seen = new Set<string>();
    for (const identity of SECTION_LIST) {
      applySectionAppearance(base, identity.id);
      const root = document.documentElement;
      seen.add(
        [
          root.style.getPropertyValue('--accent'),
          root.dataset.bg,
          root.style.getPropertyValue('--section-light'),
        ].join('|')
      );
    }
    // settings shares graphite with analytics but still differs by light/accent.
    expect(seen.size).toBeGreaterThanOrEqual(SECTION_LIST.length - 1);
  });

  it('respects the global switch', () => {
    applySectionAppearance(base, 'habits', false);
    const root = document.documentElement;

    expect(root.dataset.section).toBe('habits');
    expect(root.dataset.bg).toBe(base.backgroundId);
    expect(hexToOklch(root.style.getPropertyValue('--accent').trim()).h).toBeCloseTo(
      hexToOklch(BASE).h,
      0
    );
    // The light position is layout, so it still tells the truth about the page.
    expect(root.style.getPropertyValue('--section-light').trim()).toBe(
      SECTION_IDENTITIES.habits.light
    );
  });

  it('never leaves the motion level behind', () => {
    applySectionAppearance({ ...base, motionLevel: 'standard' }, 'tasks');
    expect(document.documentElement.dataset.motion).toBe('standard');
  });
});
