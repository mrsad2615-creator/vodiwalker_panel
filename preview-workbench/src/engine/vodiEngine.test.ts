import { describe, expect, it } from 'vitest';
import {
  analyze,
  bandedDtwScore,
  compileHapticPlan,
  evaluatePower,
  fuseScores,
  ghostTrail,
  normalizeToUnitSpace,
  pathLength,
  predict,
  protractorScore,
  recognize,
  resample,
  turningAngle,
  buildTransitionTable,
} from './vodiEngine';
import type { PowerTelemetry, Stroke } from './vodiEngine';
import { sampleCaptureFor, TEMPLATES } from '../data/templates';

function stroke(points: Array<[number, number]>): Stroke {
  return {
    id: 0,
    points: points.map(([x, y], index) => ({ x, y, t: index * 8 })),
  };
}

describe('geometry', () => {
  it('measures path length and ignores pen-up jumps', () => {
    expect(pathLength([{ x: 0, y: 0, t: 0 }, { x: 3, y: 4, t: 8 }])).toBeCloseTo(5);
    expect(pathLength([])).toBe(0);
    expect(pathLength([{ x: 1, y: 1, t: 0 }])).toBe(0);
  });

  it('resamples to an exact count and preserves endpoints', () => {
    const source = Array.from({ length: 40 }, (_, index) => ({ x: index * 10, y: 0, t: index * 8 }));
    const sampled = resample(source, 24);
    expect(sampled).toHaveLength(24);
    expect(sampled[0]).toMatchObject({ x: 0, y: 0 });
    expect(sampled.at(-1)?.x).toBeCloseTo(source.at(-1)!.x, 0);
  });

  it('normalizes into the unit square without distortion', () => {
    const normalized = normalizeToUnitSpace([
      { x: 100, y: 300, t: 0 },
      { x: 500, y: 300, t: 8 },
    ]);
    const xs = normalized.map((point) => point.x);
    const ys = normalized.map((point) => point.y);
    expect(Math.min(...xs)).toBeCloseTo(0);
    expect(Math.max(...xs)).toBeCloseTo(1);
    expect(new Set(ys).size).toBe(1);
  });

  it('computes turning angle and kinematic descriptors', () => {
    const right = stroke([[0, 0], [50, 0], [100, 0]]);
    const corner = stroke([[0, 0], [50, 0], [50, 50]]);
    expect(turningAngle(right.points)).toBeCloseTo(0, 5);
    expect(turningAngle(corner.points)).toBeCloseTo(Math.PI / 2, 4);
    expect(analyze([right]).strokeCount).toBe(1);
    expect(analyze([right]).straightness).toBeCloseTo(1, 5);
    expect(analyze([]).sampleCount).toBe(0);
  });
});

describe('recognition', () => {
  it('recognizes the same shape as itself with near-perfect confidence', () => {
    const capture = sampleCaptureFor('preset_swipe_left', 0);
    const result = recognize(capture, TEMPLATES);
    expect(result.winner?.templateId).toBe('preset_swipe_left');
    expect(result.candidates[0]!.confidence).toBeGreaterThan(0.8);
    expect(result.latencyMs).toBeGreaterThanOrEqual(0);
  });

  it('rejects degenerate strokes', () => {
    const result = recognize([stroke([[10, 10], [11, 11]])], TEMPLATES);
    expect(result.winner).toBeNull();
    expect(result.rejectedReason).toBe('EMPTY_OR_DEGENERATE_STROKE');
  });

  it('honours the privacy shield before any matching work', () => {
    const result = recognize(sampleCaptureFor('preset_z', 0), TEMPLATES, true);
    expect(result.rejectedReason).toBe('PRIVACY_SHIELD_ACTIVE');
    expect(result.candidates).toHaveLength(0);
  });

  it('scores identical clouds higher than mirrored ones', () => {
    const base = sampleCaptureFor('preset_swipe_right', 0)[0]!.points;
    const mirrored = base.map((point) => ({ ...point, x: 720 - point.x }));
    const same = protractorScore(base, base);
    const opposite = protractorScore(mirrored, base);
    expect(same).toBeGreaterThan(opposite);
    expect(same).toBeCloseTo(1, 5);
  });

  it('keeps DTW and fused scores bounded in [0,1]', () => {
    const a = sampleCaptureFor('preset_z', 0)[0]!.points;
    const b = sampleCaptureFor('preset_loop', 0)[0]!.points;
    const dtw = bandedDtwScore(a, b);
    expect(dtw).toBeGreaterThanOrEqual(0);
    expect(dtw).toBeLessThanOrEqual(1);
    const fused = fuseScores(
      { protractor: 0.9, pointCloud: 0.8, bandedDtw: 0.7, directional: 0.6 },
      1,
      true,
    );
    expect(fused).toBeGreaterThan(0.6);
    expect(fused).toBeLessThanOrEqual(1);
  });
});

describe('prediction', () => {
  it('produces ranked hypotheses and pre-warm flags', () => {
    const partial = sampleCaptureFor('preset_z', 0);
    const predictions = predict(
      partial,
      TEMPLATES,
      { appCategory: 'PRODUCTIVITY', hourOfDay: 10, previousGestureId: 'Back Swipe' },
      buildTransitionTable(),
    );
    expect(predictions.length).toBe(TEMPLATES.length);
    expect(predictions[0]!.fusedProbability).toBeGreaterThanOrEqual(
      predictions.at(-1)!.fusedProbability,
    );
    expect(predictions.every((p) => p.fusedProbability >= 0 && p.fusedProbability <= 1)).toBe(true);
  });

  it('returns no hypotheses for near-empty input and none for sensitive contexts', () => {
    expect(predict(partialStroke(2), TEMPLATES, context(), buildTransitionTable())).toEqual([]);
  });

  it('projects a ghost trail that continues from the live pointer', () => {
    const partial = sampleCaptureFor('preset_loop', 0);
    const trail = ghostTrail(partial, TEMPLATES[3]!);
    expect(trail.length).toBeGreaterThan(4);
    const last = partial[0]!.points.at(-1)!;
    expect(Number.isFinite(trail[0]!.x)).toBe(true);
    expect(Math.hypot(trail[0]!.x - last.x, trail[0]!.y - last.y)).toBeLessThan(600);
  });
});

describe('power governor', () => {
  const base: PowerTelemetry = {
    batteryPercent: 80,
    isCharging: false,
    isPowerSaveMode: false,
    isDeviceIdle: false,
    isScreenInteractive: true,
    thermalSeverity: 'NONE',
    isPrivacySensitive: false,
    gesturesPerMinute: 4,
    preferredMode: null,
  };

  it('applies overrides in strict priority order', () => {
    expect(evaluatePower({ ...base, isPrivacySensitive: true }).mode).toBe('PRIVACY_SHIELD_SUSPENDED');
    expect(evaluatePower({ ...base, isPrivacySensitive: true, isDeviceIdle: true }).mode).toBe(
      'PRIVACY_SHIELD_SUSPENDED',
    );
    expect(evaluatePower({ ...base, isDeviceIdle: true }).mode).toBe('DOZE_SUSPENDED');
    expect(evaluatePower({ ...base, thermalSeverity: 'CRITICAL' }).mode).toBe('ECO');
    expect(evaluatePower({ ...base, isPowerSaveMode: true }).mode).toBe('ECO');
    expect(evaluatePower({ ...base, batteryPercent: 5 }).mode).toBe('ULTRA_SAVER');
  });

  it('respects a pinned preference and scales the budget accordingly', () => {
    const budget = evaluatePower({ ...base, preferredMode: 'PERFORMANCE' });
    expect(budget.mode).toBe('PERFORMANCE');
    expect(budget.touchSampleRateHz).toBe(120);
    expect(budget.fullEnsemble).toBe(true);
    const eco = evaluatePower({ ...base, preferredMode: 'ECO' });
    expect(eco.touchSampleRateHz).toBeLessThan(budget.touchSampleRateHz);
    expect(eco.estimatedBatteryPercentPerHour).toBeLessThan(budget.estimatedBatteryPercentPerHour);
  });

  it('never emits out-of-range sample rates', () => {
    for (const telemetry of [base, { ...base, isDeviceIdle: true }, { ...base, batteryPercent: 1 }]) {
      const budget = evaluatePower(telemetry);
      expect(budget.touchSampleRateHz).toBeGreaterThanOrEqual(0);
      expect(budget.touchSampleRateHz).toBeLessThanOrEqual(120);
      expect(budget.hapticScale).toBeGreaterThanOrEqual(0);
      expect(budget.hapticScale).toBeLessThanOrEqual(1);
    }
  });
});

describe('haptic choreographer', () => {
  it('prefers composition on API 31+, falls back to waveform then predefined', () => {
    const composition = compileHapticPlan({
      presetId: 'confirm_cinematic',
      userIntensity: 1,
      powerScale: 1,
      apiLevel: 34,
      supportsComposition: true,
      supportsAmplitude: true,
    });
    expect(composition.tier).toBe('COMPOSITION');
    expect(composition.steps.length).toBeGreaterThan(1);

    const waveform = compileHapticPlan({
      presetId: 'confirm_cinematic',
      userIntensity: 1,
      powerScale: 1,
      apiLevel: 29,
      supportsComposition: false,
      supportsAmplitude: true,
    });
    expect(waveform.tier).toBe('WAVEFORM');

    const predefined = compileHapticPlan({
      presetId: 'confirm_cinematic',
      userIntensity: 1,
      powerScale: 1,
      apiLevel: 29,
      supportsComposition: false,
      supportsAmplitude: false,
    });
    expect(predefined.tier).toBe('PREDEFINED');
    expect(predefined.steps).toHaveLength(1);
  });

  it('suppresses output when the power budget zeroes the gain', () => {
    const plan = compileHapticPlan({
      presetId: 'ai_lock_in',
      userIntensity: 0.9,
      powerScale: 0,
      apiLevel: 34,
      supportsComposition: true,
      supportsAmplitude: true,
    });
    expect(plan.tier).toBe('SUPPRESSED');
    expect(plan.steps).toHaveLength(0);
  });

  it('scales amplitude by user intensity × power budget without exceeding 1', () => {
    const plan = compileHapticPlan({
      presetId: 'confirm_crisp',
      userIntensity: 0.8,
      powerScale: 0.5,
      apiLevel: 34,
      supportsComposition: true,
      supportsAmplitude: true,
    });
    expect(plan.scale).toBeCloseTo(0.4, 5);
    expect(plan.scale).toBeLessThanOrEqual(1);
  });
});

function partialStroke(count: number): Stroke[] {
  return [stroke(Array.from({ length: count }, (_, index) => [index * 12, index * 4] as [number, number]))];
}

function context() {
  return { appCategory: 'MEDIA_VIDEO', hourOfDay: 12, previousGestureId: null };
}
