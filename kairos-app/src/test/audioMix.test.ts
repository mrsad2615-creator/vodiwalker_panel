import { describe, expect, it } from 'vitest';
import {
  activeLayerCount,
  formatSleepRemaining,
  LAYERS,
  MAX_LAYERS,
  normalizeMix,
  SLEEP_TIMER_PRESETS,
  SOUND_PROFILES,
} from '@/lib/audio/layers';

describe('sound mix', () => {
  it('ships eleven real layers and four profiles', () => {
    expect(LAYERS).toHaveLength(11);
    expect(SOUND_PROFILES).toHaveLength(4);
    expect(SLEEP_TIMER_PRESETS).toContain(0);
  });

  it('clamps levels into 0..1 and drops near-silent layers', () => {
    const mix = normalizeMix({ rain: 2, ocean: -3, wind: 0.005, brown: 0.4 });
    expect(mix).toEqual({ rain: 1, brown: 0.4 });
  });

  it('keeps only the loudest layers so the mix never turns to mud', () => {
    const messy = { rain: 0.1, ocean: 0.2, wind: 0.3, fire: 0.4, forest: 0.5, cafe: 0.6 };
    const mix = normalizeMix(messy);
    expect(Object.keys(mix)).toHaveLength(MAX_LAYERS);
    expect(mix).not.toHaveProperty('rain');
    expect(mix).not.toHaveProperty('ocean');
    expect(mix.cafe).toBe(0.6);
  });

  it('ignores unknown layer ids from older builds', () => {
    // @ts-expect-error deliberately invalid key
    expect(normalizeMix({ vinyl: 0.7, rain: 0.5 })).toEqual({ rain: 0.5 });
  });

  it('every profile is a valid, non-empty mix', () => {
    for (const profile of SOUND_PROFILES) {
      const mix = normalizeMix(profile.mix);
      expect(activeLayerCount(mix)).toBeGreaterThan(0);
      for (const level of Object.values(mix)) expect(level).toBeGreaterThan(0);
    }
  });

  it('formats the sleep countdown in Latin digits', () => {
    expect(formatSleepRemaining(0)).toBe('0:00');
    expect(formatSleepRemaining(65)).toBe('1:05');
    expect(formatSleepRemaining(3600)).toBe('60:00');
    expect(formatSleepRemaining(-10)).toBe('0:00');
  });
});
