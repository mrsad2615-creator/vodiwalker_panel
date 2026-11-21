import { beforeEach, describe, expect, it } from 'vitest';
import {
  ACCENT_PRESETS,
  BACKGROUND_PRESETS,
  DEFAULT_ACCENT,
  DEFAULT_APPEARANCE,
  applyAppearance,
  cacheAppearance,
  contrastRatio,
  deriveAccent,
  isHexColor,
  normalizeAppearance,
  normalizeHex,
  oklchToHex,
  hexToOklch,
  readCachedAppearance,
} from '@/lib/theme';

const INK = '#0A0A0C';

describe('KAIROS theme engine', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('style');
    delete document.documentElement.dataset.bg;
    delete document.documentElement.dataset.motion;
  });

  it('normalises hex input instead of trusting it', () => {
    expect(normalizeHex('#abc')).toBe('#AABBCC');
    expect(normalizeHex('6d4aff')).toBe('#6D4AFF');
    expect(normalizeHex('  #6D4AFF ')).toBe('#6D4AFF');
    expect(normalizeHex('not-a-colour')).toBeNull();
    expect(isHexColor('#12')).toBe(false);
    expect(isHexColor('#6D4AFF')).toBe(true);
  });

  it('keeps the brand derivation identical to the shipped palette', () => {
    const derived = deriveAccent(DEFAULT_ACCENT);
    expect(derived.accent).toBe(DEFAULT_ACCENT);
    // Foreground must stay readable as text on obsidian (AA, normal text).
    expect(contrastRatio(derived.fg, INK)).toBeGreaterThanOrEqual(4.5);
    // The label on the accent fill is chosen by contrast, not by brand vanity:
    // brass takes ink, violet would take white. Either way it must be AA.
    expect(['#FFFFFF', INK]).toContain(derived.on);
    expect(contrastRatio(derived.on, derived.accent)).toBeGreaterThanOrEqual(4.5);
  });

  it('flips the label colour on a light accent instead of shipping unreadable white', () => {
    const gold = deriveAccent('#FFD166');
    expect(gold.on).toBe(INK);
    expect(contrastRatio(gold.on, gold.accent)).toBeGreaterThanOrEqual(4.5);
    expect(contrastRatio(gold.fg, INK)).toBeGreaterThanOrEqual(4.5);
  });

  it('derives readable foregrounds for every preset', () => {
    for (const preset of ACCENT_PRESETS) {
      const derived = deriveAccent(preset.hex);
      expect(contrastRatio(derived.fg, INK)).toBeGreaterThanOrEqual(4.5);
      expect(contrastRatio(derived.on, derived.accent)).toBeGreaterThanOrEqual(4.5);
      // hue is preserved: the derived foreground stays in the same colour family
      const baseHue = hexToOklch(preset.hex).h;
      const fgHue = hexToOklch(derived.fg).h;
      const delta = Math.min(Math.abs(baseHue - fgHue), 360 - Math.abs(baseHue - fgHue));
      expect(delta).toBeLessThan(6);
    }
  });

  it('round-trips through OKLCH without drifting', () => {
    for (const hex of ['#6D4AFF', '#0FB5A6', '#E0505B', '#D08A3E']) {
      const back = oklchToHex(hexToOklch(hex));
      const a = hexToOklch(hex);
      const b = hexToOklch(back);
      expect(Math.abs(a.l - b.l)).toBeLessThan(0.02);
      expect(Math.abs(a.c - b.c)).toBeLessThan(0.02);
    }
  });

  it('writes tokens and data attributes on :root', () => {
    const derived = applyAppearance({ accent: '#0FB5A6', backgroundId: 'aurora', motionLevel: 'ultra' });
    const root = document.documentElement;

    expect(root.style.getPropertyValue('--accent')).toBe('#0FB5A6');
    expect(root.style.getPropertyValue('--accent-fg')).toBe(derived.fg);
    expect(root.style.getPropertyValue('--accent-on')).toBe(derived.on);
    expect(root.style.getPropertyValue('--accent-soft')).toContain('color-mix');
    expect(root.dataset.bg).toBe('aurora');
    expect(root.dataset.motion).toBe('ultra');
  });

  it('falls back to the brand defaults for garbage input', () => {
    expect(normalizeAppearance({ accent: 'nope', backgroundId: 'x' as never, motionLevel: 'x' as never })).toEqual(
      DEFAULT_APPEARANCE
    );
    expect(DEFAULT_APPEARANCE.backgroundId).toBe('obsidian');
    expect(DEFAULT_APPEARANCE.motionLevel).toBe('cinematic');
    expect(DEFAULT_APPEARANCE.accent).toBe(DEFAULT_ACCENT);
  });

  it('caches the appearance for a flash-free next boot', () => {
    cacheAppearance({ accent: '#D2548F', backgroundId: 'ember', motionLevel: 'standard' });
    expect(readCachedAppearance()).toEqual({
      accent: '#D2548F',
      backgroundId: 'ember',
      motionLevel: 'standard',
    });
  });

  it('ships a complete, unique background library', () => {
    const ids = BACKGROUND_PRESETS.map((b) => b.id);
    expect(ids).toHaveLength(6);
    expect(new Set(ids).size).toBe(6);
    expect(ids).toContain('obsidian');
    expect(new Set(ACCENT_PRESETS.map((p) => p.hex)).size).toBe(ACCENT_PRESETS.length);
  });
});
