import { describe, expect, it } from 'vitest';
import {
  BREATH_PATTERNS,
  breathStateAt,
  patternDuration,
  PHASE_LABEL,
} from '@/lib/audio/breathing';

const box = BREATH_PATTERNS.find((p) => p.id === 'box')!;
const slow = BREATH_PATTERNS.find((p) => p.id === '478')!;

describe('breathing pacer', () => {
  it('knows the length of each pattern', () => {
    expect(patternDuration(box)).toBe(16);
    expect(patternDuration(slow)).toBe(19);
    expect(patternDuration(BREATH_PATTERNS[2])).toBe(10);
  });

  it('walks the box pattern step by step', () => {
    expect(breathStateAt(box, 0).phase).toBe('inhale');
    expect(breathStateAt(box, 4_000).phase).toBe('hold');
    expect(breathStateAt(box, 8_000).phase).toBe('exhale');
    expect(breathStateAt(box, 12_000).phase).toBe('rest');
    expect(breathStateAt(box, 15_999).progress).toBeGreaterThan(0.9);
  });

  it('counts cycles and rolls over cleanly', () => {
    expect(breathStateAt(box, 16_000).cycles).toBe(1);
    expect(breathStateAt(box, 16_000).phase).toBe('inhale');
    expect(breathStateAt(box, 32_500).cycles).toBe(2);
  });

  it('reports whole seconds left in the step', () => {
    expect(breathStateAt(box, 0).secondsLeft).toBe(4);
    expect(breathStateAt(box, 3_400).secondsLeft).toBe(1);
  });

  it('survives nonsense input without leaving the pattern', () => {
    expect(breathStateAt(box, Number.NaN).phase).toBe('inhale');
    expect(breathStateAt(box, -5000).phase).toBe('inhale');
  });

  it('has a Persian label for every phase', () => {
    for (const phase of ['inhale', 'hold', 'exhale', 'rest'] as const) {
      expect(PHASE_LABEL[phase]).toMatch(/[\u0600-\u06FF]/);
    }
  });
});
