import { describe, expect, it } from 'vitest';
import { fillNoise, lowToHighRatio, peakOf } from '@/lib/audio/noise';

const SIZE = 8192;
const buffer = () => new Float32Array(SIZE);

describe('noise generators', () => {
  it('fills every sample and stays inside full scale', () => {
    for (const color of ['white', 'pink', 'brown'] as const) {
      const out = fillNoise(buffer(), color, 5);
      expect(out.every((v) => Number.isFinite(v))).toBe(true);
      expect(peakOf(out)).toBeLessThanOrEqual(0.95);
      expect(peakOf(out)).toBeGreaterThan(0.2);
    }
  });

  it('is deterministic per seed (profiles and tests reproduce)', () => {
    const a = fillNoise(buffer(), 'brown', 42);
    const b = fillNoise(buffer(), 'brown', 42);
    expect(Array.from(a.slice(0, 64))).toEqual(Array.from(b.slice(0, 64)));
  });

  it('brown is darker than pink, and pink darker than white', () => {
    const brown = lowToHighRatio(fillNoise(buffer(), 'brown', 3));
    const pink = lowToHighRatio(fillNoise(buffer(), 'pink', 3));
    const white = lowToHighRatio(fillNoise(buffer(), 'white', 3));

    expect(brown).toBeGreaterThan(pink);
    expect(pink).toBeGreaterThan(white);
  });
});
