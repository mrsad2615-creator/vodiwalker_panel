import type { Kinematics, Point, Stroke, Template } from '../engine/vodiEngine';
import { analyze, normalizeToUnitSpace, resample } from '../engine/vodiEngine';

/** Builds a template from raw normalized points, computing its reference kinematics once. */
export function makeTemplate(
  id: string,
  name: string,
  category: string,
  glyph: string,
  raw: readonly Point[],
  options: { orientationSensitive?: boolean; minConfidence?: number } = {},
): Template {
  const cloud = resample(normalizeToUnitSpace(raw), 64);
  const kinematics: Kinematics = analyze([
    { id: 0, points: raw } satisfies Stroke,
  ]);
  return {
    id,
    name,
    category,
    glyph,
    cloud,
    kinematics,
    orientationSensitive: options.orientationSensitive ?? true,
    minConfidence: options.minConfidence ?? 0.72,
  };
}

/** Samples a line with a realistic 8ms pointer cadence and slight tremor. */
export function line(
  from: readonly [number, number],
  to: readonly [number, number],
  samples = 28,
): Point[] {
  const points: Point[] = [];
  for (let i = 0; i < samples; i += 1) {
    const t = i / (samples - 1);
    const tremor = Math.sin(i * 1.7) * 1.4;
    points.push({
      x: from[0] + (to[0] - from[0]) * t + tremor,
      y: from[1] + (to[1] - from[1]) * t + tremor * 0.6,
      t: i * 8,
    });
  }
  return points;
}

/** Samples a circular arc (used for the loop/rotation templates). */
export function arc(
  center: readonly [number, number],
  radius: number,
  fromRad: number,
  toRad: number,
  samples = 40,
): Point[] {
  const points: Point[] = [];
  for (let i = 0; i < samples; i += 1) {
    const t = i / (samples - 1);
    const angle = fromRad + (toRad - fromRad) * t;
    points.push({
      x: center[0] + Math.cos(angle) * radius,
      y: center[1] + Math.sin(angle) * radius,
      t: i * 8,
    });
  }
  return points;
}

/** The four seeded starter gestures shown in the workbench. */
export const TEMPLATES: readonly Template[] = [
  makeTemplate('preset_swipe_left', 'Back Swipe', 'NAVIGATION', '←', line([620, 200], [120, 210], 30)),
  makeTemplate('preset_swipe_right', 'Forward Swipe', 'NAVIGATION', '→', line([120, 200], [620, 190], 30)),
  makeTemplate('preset_z', 'Quick Capture', 'APP_LAUNCHER', '⚡', [
    ...line([140, 120], [600, 120], 16),
    ...line([600, 120], [140, 320], 20),
    ...line([140, 320], [600, 320], 16),
  ], { minConfidence: 0.7 }),
  makeTemplate('preset_loop', 'Privacy Shield', 'SYSTEM_CONTROL', '◍', arc([360, 220], 150, Math.PI * 0.15, Math.PI * 1.85, 52), {
    orientationSensitive: false,
    minConfidence: 0.66,
  }),
];

/** Debug "showcase" capture mimicking a real Z drawn slightly off. */
export function sampleCaptureFor(templateId: string, jitter = 6): Stroke[] {
  const template = TEMPLATES.find((item) => item.id === templateId) ?? TEMPLATES[0]!;
  const points = template.cloud.map((point, index) => ({
    x: point.x * 520 + 90 + Math.sin(index) * jitter,
    y: point.y * 420 + 60 + Math.cos(index) * jitter,
    t: index * 8,
  }));
  return [{ id: 0, points }];
}
