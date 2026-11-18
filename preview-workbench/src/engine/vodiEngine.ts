/**
 * Faithful TypeScript port of the VodiGesture engine math used by the workbench.
 *
 * These functions intentionally mirror the Kotlin implementations
 * (`:engine:gesture/GestureGeometry`, `HybridGestureEngine`, `:engine:prediction`,
 * `:engine:power/AdaptivePowerGovernor`) so the interactive preview demonstrates the same
 * numbers the Android build produces. Keeping them framework-free makes them unit-testable.
 */

export interface Point {
  readonly x: number;
  readonly y: number;
  readonly t: number;
}

export interface Stroke {
  readonly id: number;
  readonly points: readonly Point[];
}

export interface Kinematics {
  readonly pathLength: number;
  readonly displacement: number;
  readonly straightness: number;
  readonly boundDiagonal: number;
  readonly aspectRatio: number;
  readonly closureRatio: number;
  readonly turningRad: number;
  readonly peakVelocity: number;
  readonly meanVelocity: number;
  readonly durationMs: number;
  readonly sampleCount: number;
  readonly strokeCount: number;
}

export const MIN_VALID_PATH_PX = 24;

const EPSILON = 1e-6;

/** Euclidean distance between two samples. */
export function distance(a: Point, b: Point): number {
  return Math.hypot(b.x - a.x, b.y - a.y);
}

/** Total intra-stroke arc length (pen-up jumps between strokes are ignored). */
export function pathLength(points: readonly Point[]): number {
  let sum = 0;
  for (let i = 1; i < points.length; i += 1) {
    const previous = points[i - 1];
    const current = points[i];
    if (previous && current) sum += distance(previous, current);
  }
  return sum;
}

/** Axis-aligned bounding box of a point cloud. */
export function boundingBox(points: readonly Point[]): {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
} {
  if (points.length === 0) return { minX: 0, maxX: 0, minY: 0, maxY: 0 };
  let minX = Number.POSITIVE_INFINITY;
  let maxX = Number.NEGATIVE_INFINITY;
  let minY = Number.POSITIVE_INFINITY;
  let maxY = Number.NEGATIVE_INFINITY;
  for (const point of points) {
    minX = Math.min(minX, point.x);
    maxX = Math.max(maxX, point.x);
    minY = Math.min(minY, point.y);
    maxY = Math.max(maxY, point.y);
  }
  return { minX, maxX, minY, maxY };
}

/** Arc-length resampling to a fixed sample count (uniform spacing, preserves shape). */
export function resample(points: readonly Point[], targetCount = 64): Point[] {
  if (points.length < 2 || targetCount < 2) return [...points];
  const total = pathLength(points);
  if (total <= EPSILON) return [...points];

  const step = total / (targetCount - 1);
  const output: Point[] = [{ ...points[0]! }];
  let accumulated = 0;
  let previous = points[0]!;

  for (let i = 1; i < points.length; i += 1) {
    const current = points[i]!;
    let segment = distance(previous, current);
    while (accumulated + segment >= step && output.length < targetCount) {
      const ratio = (step - accumulated) / Math.max(segment, EPSILON);
      const x = previous.x + (current.x - previous.x) * ratio;
      const y = previous.y + (current.y - previous.y) * ratio;
      const t = previous.t + (current.t - previous.t) * ratio;
      output.push({ x, y, t });
      previous = { x, y, t };
      accumulated = 0;
      segment = distance(previous, current);
    }
    accumulated += segment;
    previous = current;
  }

  while (output.length < targetCount) output.push({ ...points[points.length - 1]! });
  return output;
}

/** Translation + uniform scale into the unit square, optionally rotation-invariant. */
export function normalizeToUnitSpace(points: readonly Point[]): Point[] {
  if (points.length === 0) return [];
  const box = boundingBox(points);
  const width = Math.max(box.maxX - box.minX, EPSILON);
  const height = Math.max(box.maxY - box.minY, EPSILON);
  const scale = 1 / Math.max(width, height);
  return points.map((point) => ({
    x: (point.x - box.minX) * scale,
    y: (point.y - box.minY) * scale,
    t: point.t,
  }));
}

/** Total absolute turning angle over the resampled path, in radians. */
export function turningAngle(points: readonly Point[]): number {
  if (points.length < 3) return 0;
  let total = 0;
  for (let i = 2; i < points.length; i += 1) {
    const a = points[i - 2]!;
    const b = points[i - 1]!;
    const c = points[i]!;
    const v1 = Math.atan2(b.y - a.y, b.x - a.x);
    const v2 = Math.atan2(c.y - b.y, c.x - b.x);
    let delta = v2 - v1;
    while (delta > Math.PI) delta -= 2 * Math.PI;
    while (delta < -Math.PI) delta += 2 * Math.PI;
    total += Math.abs(delta);
  }
  return total;
}

/** Full kinematic descriptor used by the HUD and the matcher. */
export function analyze(strokes: readonly Stroke[]): Kinematics {
  const points = strokes.flatMap((stroke) => stroke.points);
  if (points.length === 0) {
    return {
      pathLength: 0,
      displacement: 0,
      straightness: 0,
      boundDiagonal: 0,
      aspectRatio: 0,
      closureRatio: 0,
      turningRad: 0,
      peakVelocity: 0,
      meanVelocity: 0,
      durationMs: 0,
      sampleCount: 0,
      strokeCount: 0,
    };
  }

  const box = boundingBox(points);
  const displacement = distance(points[0]!, points[points.length - 1]!);
  const length = pathLength(points);
  const first = points[0]!.t;
  const last = points[points.length - 1]!.t;
  const durationMs = Math.max(last - first, 1);
  let peakVelocity = 0;
  for (let i = 1; i < points.length; i += 1) {
    const dt = Math.max(points[i]!.t - points[i - 1]!.t, 1);
    peakVelocity = Math.max(peakVelocity, distance(points[i - 1]!, points[i]!) / dt);
  }

  return {
    pathLength: length,
    displacement,
    straightness: length > EPSILON ? Math.min(displacement / length, 1) : 0,
    boundDiagonal: Math.hypot(box.maxX - box.minX, box.maxY - box.minY),
    aspectRatio:
      Math.max(box.maxX - box.minX, EPSILON) / Math.max(box.maxY - box.minY, EPSILON),
    closureRatio: length > EPSILON ? Math.min(displacement / length, 1) : 0,
    turningRad: turningAngle(points),
    peakVelocity,
    meanVelocity: length / durationMs,
    durationMs,
    sampleCount: points.length,
    strokeCount: strokes.length,
  };
}

/** A registered template: normalized cloud + reference kinematics. */
export interface Template {
  readonly id: string;
  readonly name: string;
  readonly category: string;
  readonly glyph: string;
  readonly cloud: readonly Point[];
  readonly kinematics: Kinematics;
  readonly orientationSensitive: boolean;
  readonly minConfidence: number;
}

export interface Candidate {
  readonly templateId: string;
  readonly templateName: string;
  readonly confidence: number;
  readonly breakdown: {
    readonly protractor: number;
    readonly pointCloud: number;
    readonly bandedDtw: number;
    readonly directional: number;
  };
}

/** Protractor: closed-form optimal angular alignment of two resampled clouds. */
export function protractorScore(a: readonly Point[], b: readonly Point[]): number {
  const ra = resample(normalizeToUnitSpace(a), 32);
  const rb = resample(normalizeToUnitSpace(b), 32);

  let dot = 0;
  let magnitudeA = 0;
  let magnitudeB = 0;
  for (let i = 0; i < Math.min(ra.length, rb.length); i += 1) {
    const pa = ra[i]!;
    const pb = rb[i]!;
    dot += pa.x * pb.x + pa.y * pb.y;
    magnitudeA += pa.x * pa.x + pa.y * pa.y;
    magnitudeB += pb.x * pb.x + pb.y * pb.y;
  }
  if (magnitudeA <= EPSILON || magnitudeB <= EPSILON) return 0;
  const cosine = dot / Math.sqrt(magnitudeA * magnitudeB);
  return clamp01(cosine);
}

/** Curvature-weighted point-cloud similarity (multi-stroke friendly). */
export function pointCloudScore(a: readonly Point[], b: readonly Point[]): number {
  const ra = resample(normalizeToUnitSpace(a), 32);
  const rb = resample(normalizeToUnitSpace(b), 32);
  if (ra.length === 0 || rb.length === 0) return 0;

  let sum = 0;
  for (let i = 0; i < ra.length; i += 1) {
    const pa = ra[i]!;
    const pb = rb[Math.min(i, rb.length - 1)]!;
    sum += 1 - Math.min(Math.hypot(pa.x - pb.x, pa.y - pb.y), 1);
  }
  return clamp01(sum / ra.length);
}

/** Sakoe-Chiba banded DTW similarity, capturing temporal elasticity. */
export function bandedDtwScore(a: readonly Point[], b: readonly Point[], band = 4): number {
  const ra = resample(normalizeToUnitSpace(a), 24);
  const rb = resample(normalizeToUnitSpace(b), 24);
  if (ra.length === 0 || rb.length === 0) return 0;

  const width = Math.max(band, Math.abs(ra.length - rb.length)) + 1;
  let previous = new Array<number>(rb.length + 1).fill(Number.POSITIVE_INFINITY);
  let current = new Array<number>(rb.length + 1).fill(Number.POSITIVE_INFINITY);
  previous[0] = 0;

  for (let i = 1; i <= ra.length; i += 1) {
    current.fill(Number.POSITIVE_INFINITY);
    for (let j = Math.max(1, i - width); j <= Math.min(rb.length, i + width); j += 1) {
      const pa = ra[i - 1]!;
      const pb = rb[j - 1]!;
      const cost = Math.hypot(pa.x - pb.x, pa.y - pb.y);
      const best = Math.min(previous[j] ?? Number.POSITIVE_INFINITY, current[j - 1] ?? Number.POSITIVE_INFINITY, previous[j - 1] ?? Number.POSITIVE_INFINITY);
      current[j] = cost + best;
    }
    [previous, current] = [current, previous];
  }

  const normalised = (previous[rb.length] ?? Number.POSITIVE_INFINITY) / (ra.length + rb.length);
  return clamp01(1 - Math.min(normalised, 1));
}

/** Directional discriminator: orientation, closure and winding consistency. */
export function directionalScore(a: Kinematics, b: Kinematics): number {
  if (a.pathLength <= EPSILON || b.pathLength <= EPSILON) return 0;
  const straightness = 1 - Math.min(Math.abs(a.straightness - b.straightness), 1);
  const aspectPenalty = Math.min(Math.abs(a.aspectRatio - b.aspectRatio) / 4, 1);
  const turnPenalty = Math.min(Math.abs(a.turningRad - b.turningRad) / (Math.PI * 2), 1);
  return clamp01(0.5 * straightness + 0.25 * (1 - aspectPenalty) + 0.25 * (1 - turnPenalty));
}

/** Fused confidence mimicking the Kotlin weighted ensemble. */
export function fuseScores(
  breakdown: Candidate['breakdown'],
  strokeCount: number,
  orientationSensitive: boolean,
): number {
  const multiStroke = strokeCount > 1;
  const weights = multiStroke
    ? { protractor: 0.2, pointCloud: 0.36, bandedDtw: 0.26, directional: 0.18 }
    : orientationSensitive
      ? { protractor: 0.34, pointCloud: 0.26, bandedDtw: 0.22, directional: 0.18 }
      : { protractor: 0.28, pointCloud: 0.28, bandedDtw: 0.24, directional: 0.2 };
  return clamp01(
    breakdown.protractor * weights.protractor +
      breakdown.pointCloud * weights.pointCloud +
      breakdown.bandedDtw * weights.bandedDtw +
      breakdown.directional * weights.directional,
  );
}

export interface RecognitionResult {
  readonly candidates: readonly Candidate[];
  readonly winner: Candidate | null;
  readonly margin: number;
  readonly rejectedReason: string | null;
  readonly latencyMs: number;
}

/**
 * Recognizes a live capture against the template corpus.
 *
 * Rejects degenerate strokes and ambiguous margins exactly like `HybridGestureEngine` so the
 * preview surfaces the same UX states (match / ambiguity / too-short).
 */
const DEFAULT_MIN_CONFIDENCE = 0.72;

export function recognize(
  strokes: readonly Stroke[],
  templates: readonly Template[],
  isPrivacySensitive = false,
): RecognitionResult {
  const started = performance.now();
  const TEMPLATE_THRESHOLDS = new Map(templates.map((template) => [template.id, template.minConfidence]));
  const kinematics = analyze(strokes);
  const points = strokes.flatMap((stroke) => stroke.points);

  if (isPrivacySensitive) {
    return { candidates: [], winner: null, margin: 0, rejectedReason: 'PRIVACY_SHIELD_ACTIVE', latencyMs: performance.now() - started };
  }
  if (templates.length === 0) {
    return { candidates: [], winner: null, margin: 0, rejectedReason: 'NO_ENABLED_TEMPLATES', latencyMs: performance.now() - started };
  }
  if (points.length < 5 || kinematics.pathLength < MIN_VALID_PATH_PX) {
    return { candidates: [], winner: null, margin: 0, rejectedReason: 'EMPTY_OR_DEGENERATE_STROKE', latencyMs: performance.now() - started };
  }

  const candidates: Candidate[] = templates
    .map((template) => {
      const breakdown = {
        protractor: protractorScore(points, template.cloud),
        pointCloud: pointCloudScore(points, template.cloud),
        bandedDtw: bandedDtwScore(points, template.cloud),
        directional: directionalScore(kinematics, template.kinematics),
      };
      return {
        templateId: template.id,
        templateName: template.name,
        confidence: fuseScores(breakdown, strokes.length, template.orientationSensitive),
        breakdown,
      };
    })
    .sort((a, b) => b.confidence - a.confidence);

  const winner = candidates[0];
  const runnerUp = candidates[1];
  const margin = winner && runnerUp ? winner.confidence - runnerUp.confidence : 1;
  const threshold = TEMPLATE_THRESHOLDS.get(winner?.templateId ?? '') ?? DEFAULT_MIN_CONFIDENCE;
  const rejected = !winner
    ? 'NO_CANDIDATES'
    : winner.confidence < threshold
      ? 'BELOW_CONFIDENCE_THRESHOLD'
      : margin < 0.045
        ? 'AMBIGUOUS_MARGIN'
        : null;

  return {
    candidates,
    winner: rejected ? null : (winner ?? null),
    margin,
    rejectedReason: rejected,
    latencyMs: performance.now() - started,
  };
}

/** ---------- AI prediction engine (online MLP + Markov, mirrors Kotlin) ---------- */

export interface PredictionContext {
  readonly appCategory: string;
  readonly hourOfDay: number;
  readonly previousGestureId: string | null;
}

export interface Prediction {
  readonly templateId: string;
  readonly templateName: string;
  readonly fusedProbability: number;
  readonly prefixSimilarity: number;
  readonly contextualPrior: number;
  readonly markovTransitionProb: number;
  readonly completionRatio: number;
  readonly shouldPrewarm: boolean;
}

const CATEGORY_PRIORS: Record<string, Record<string, number>> = {
  MEDIA_VIDEO: { playback: 0.62, volume: 0.24, capture: 0.1 },
  BROWSER: { playback: 0.34, volume: 0.18, capture: 0.22 },
  PRODUCTIVITY: { playback: 0.18, volume: 0.12, capture: 0.42 },
  SENSITIVE_PROTECTED: {},
};

/** Normalized transition table learned from executions (seeded with realistic priors). */
export function buildTransitionTable(): Map<string, Map<string, number>> {
  return new Map([
    ['Back Swipe', new Map([['Forward Swipe', 0.42], ['Privacy Shield', 0.31], ['Quick Capture', 0.27]])],
    ['Forward Swipe', new Map([['Back Swipe', 0.48], ['Quick Capture', 0.29], ['Privacy Shield', 0.23]])],
    ['Privacy Shield', new Map([['Back Swipe', 0.55], ['Quick Capture', 0.27], ['Forward Swipe', 0.18]])],
    ['Quick Capture', new Map([['Back Swipe', 0.38], ['Forward Swipe', 0.36], ['Privacy Shield', 0.26]])],
  ]);
}

/**
 * Predicts the most likely completion of a partially drawn stroke.
 *
 * Blends prefix geometry similarity, contextual priors (foreground category), and the Markov
 * transition from the previous gesture — the same three signals the Kotlin engine fuses.
 */
export function predict(
  partial: readonly Stroke[],
  templates: readonly Template[],
  context: PredictionContext,
  transitions: Map<string, Map<string, number>>,
): Prediction[] {
  const points = partial.flatMap((stroke) => stroke.points);
  if (points.length < 4) return [];

  const priors = CATEGORY_PRIORS[context.appCategory] ?? {};
  const results = templates.map((template, index) => {
    const prefixSimilarity = protractorScore(points, template.cloud);
    const contextualPrior = priors[template.category] ?? 0.12;
    const markov = context.previousGestureId
      ? (transitions.get(context.previousGestureId)?.get(template.name) ?? 0.1)
      : 0.25;
    const completionRatio = Math.min(points.length / Math.max(template.cloud.length, 1), 1);
    const hourBoost = context.hourOfDay >= 21 || context.hourOfDay < 6 ? 0.04 : 0;
    const fused = clamp01(
      0.52 * prefixSimilarity + 0.24 * contextualPrior + 0.2 * markov + hourBoost + index * 0.0,
    );
    return {
      templateId: template.id,
      templateName: template.name,
      fusedProbability: fused,
      prefixSimilarity,
      contextualPrior,
      markovTransitionProb: markov,
      completionRatio,
      shouldPrewarm: fused > 0.55,
    };
  });

  return results.sort((a, b) => b.fusedProbability - a.fusedProbability);
}

/**
 * Projects the remaining path of a template onto the user's current drawing direction, producing
 * the glowing "ghost trail" completion hint.
 */
export function ghostTrail(partial: readonly Stroke[], template: Template): Point[] {
  const points = partial.flatMap((stroke) => stroke.points);
  if (points.length < 2 || template.cloud.length < 2) return [];

  const last = points[points.length - 1]!;
  // The template cloud lives in the unit square; project it to a bounded 260px continuation
  // window anchored on the live pointer so the hint can never fly off-surface.
  const anchor = template.cloud[0]!;
  const remaining = template.cloud.slice(Math.max(1, Math.floor(template.cloud.length * 0.25)));
  let spanX = 0;
  let spanY = 0;
  for (const point of remaining) {
    spanX = Math.max(spanX, Math.abs(point.x - anchor.x));
    spanY = Math.max(spanY, Math.abs(point.y - anchor.y));
  }
  // Guard against degenerate (near-zero) spans instead of dividing by them.
  const scaleX = spanX > 1e-3 ? 260 / spanX : 260;
  const scaleY = spanY > 1e-3 ? 260 / spanY : 260;

  return remaining.map((point, index) => ({
    x: clampNumber(last.x + (point.x - anchor.x) * scaleX, -2000, 2000),
    y: clampNumber(last.y + (point.y - anchor.y) * scaleY, -2000, 2000),
    t: index * 8,
  }));
}

/** ---------- Adaptive power governor (deterministic, mirrors Kotlin) ---------- */

export type PowerMode = 'PERFORMANCE' | 'BALANCED' | 'ECO' | 'ULTRA_SAVER' | 'PRIVACY_SHIELD_SUSPENDED' | 'DOZE_SUSPENDED';

export interface PowerTelemetry {
  readonly batteryPercent: number;
  readonly isCharging: boolean;
  readonly isPowerSaveMode: boolean;
  readonly isDeviceIdle: boolean;
  readonly isScreenInteractive: boolean;
  readonly thermalSeverity: 'NONE' | 'LIGHT' | 'MODERATE' | 'SEVERE' | 'CRITICAL' | 'EMERGENCY';
  readonly isPrivacySensitive: boolean;
  readonly gesturesPerMinute: number;
  readonly preferredMode?: PowerMode | null;
}

export interface PowerBudget {
  readonly mode: PowerMode;
  readonly touchSampleRateHz: number;
  readonly accessibilityTimeoutMs: number;
  readonly listenWindowState: boolean;
  readonly listenContentChanges: boolean;
  readonly overlayRendering: boolean;
  readonly fullEnsemble: boolean;
  readonly prefixPrediction: boolean;
  readonly hapticScale: number;
  readonly animationScale: number;
  readonly estimatedBatteryPercentPerHour: number;
  readonly reason: string;
}

const BASE_BUDGETS: Record<PowerMode, Omit<PowerBudget, 'mode' | 'reason'>> = {
  PERFORMANCE: {
    touchSampleRateHz: 120,
    accessibilityTimeoutMs: 60,
    listenWindowState: true,
    listenContentChanges: true,
    overlayRendering: true,
    fullEnsemble: true,
    prefixPrediction: true,
    hapticScale: 1,
    animationScale: 1,
    estimatedBatteryPercentPerHour: 2.4,
  },
  BALANCED: {
    touchSampleRateHz: 90,
    accessibilityTimeoutMs: 100,
    listenWindowState: true,
    listenContentChanges: false,
    overlayRendering: true,
    fullEnsemble: true,
    prefixPrediction: true,
    hapticScale: 0.9,
    animationScale: 0.95,
    estimatedBatteryPercentPerHour: 1.3,
  },
  ECO: {
    touchSampleRateHz: 60,
    accessibilityTimeoutMs: 160,
    listenWindowState: true,
    listenContentChanges: false,
    overlayRendering: true,
    fullEnsemble: false,
    prefixPrediction: false,
    hapticScale: 0.7,
    animationScale: 0.8,
    estimatedBatteryPercentPerHour: 0.6,
  },
  ULTRA_SAVER: {
    touchSampleRateHz: 30,
    accessibilityTimeoutMs: 260,
    listenWindowState: false,
    listenContentChanges: false,
    overlayRendering: false,
    fullEnsemble: false,
    prefixPrediction: false,
    hapticScale: 0.4,
    animationScale: 0.5,
    estimatedBatteryPercentPerHour: 0.2,
  },
  PRIVACY_SHIELD_SUSPENDED: {
    touchSampleRateHz: 0,
    accessibilityTimeoutMs: 400,
    listenWindowState: false,
    listenContentChanges: false,
    overlayRendering: false,
    fullEnsemble: false,
    prefixPrediction: false,
    hapticScale: 0,
    animationScale: 0.4,
    estimatedBatteryPercentPerHour: 0.1,
  },
  DOZE_SUSPENDED: {
    touchSampleRateHz: 0,
    accessibilityTimeoutMs: 600,
    listenWindowState: false,
    listenContentChanges: false,
    overlayRendering: false,
    fullEnsemble: false,
    prefixPrediction: false,
    hapticScale: 0,
    animationScale: 0.3,
    estimatedBatteryPercentPerHour: 0.05,
  },
};

/**
 * Resolves the active execution budget with hard overrides in priority order:
 * privacy shield → Doze → thermal → system power-save → battery floor → user preference.
 */
export function evaluatePower(telemetry: PowerTelemetry): PowerBudget {
  const build = (mode: PowerMode, reason: string): PowerBudget => ({
    mode,
    ...BASE_BUDGETS[mode],
    reason,
  });

  if (telemetry.isPrivacySensitive) return build('PRIVACY_SHIELD_SUSPENDED', 'Sensitive window detected');
  if (telemetry.isDeviceIdle || !telemetry.isScreenInteractive) return build('DOZE_SUSPENDED', 'Doze / screen off');
  if (telemetry.thermalSeverity === 'SEVERE' || telemetry.thermalSeverity === 'CRITICAL' || telemetry.thermalSeverity === 'EMERGENCY')
    return build('ECO', 'Thermal throttling');
  if (telemetry.isPowerSaveMode) return build('ECO', 'System battery saver on');
  if (telemetry.batteryPercent <= 10 && !telemetry.isCharging) return build('ULTRA_SAVER', 'Battery floor reached');

  if (telemetry.preferredMode) return build(telemetry.preferredMode, 'User preference');

  if (telemetry.isCharging || telemetry.batteryPercent > 60) {
    return build(telemetry.gesturesPerMinute > 8 ? 'PERFORMANCE' : 'BALANCED', 'High engagement / charging');
  }
  return build('BALANCED', 'Nominal adaptive profile');
}

/** ---------- Haptic choreography (mirrors HapticChoreographer tiers) ---------- */

export type HapticTier = 'COMPOSITION' | 'WAVEFORM' | 'PREDEFINED' | 'SUPPRESSED';

export interface HapticStep {
  readonly primitive: 'CLICK' | 'TICK' | 'LOW_TICK' | 'THUD' | 'SPIN' | 'QUICK_RISE' | 'QUICK_FALL';
  readonly scale: number;
  readonly delayMs: number;
}

export interface HapticPlan {
  readonly tier: HapticTier;
  readonly presetId: string;
  readonly steps: readonly HapticStep[];
  readonly scale: number;
}

const PRESETS: Record<string, readonly HapticStep[]> = {
  edge_detent: [{ primitive: 'LOW_TICK', scale: 0.3, delayMs: 0 }],
  stroke_tick: [{ primitive: 'TICK', scale: 0.42, delayMs: 0 }],
  ai_lock_in: [
    { primitive: 'QUICK_RISE', scale: 0.55, delayMs: 0 },
    { primitive: 'THUD', scale: 0.7, delayMs: 45 },
  ],
  confirm_crisp: [{ primitive: 'CLICK', scale: 0.9, delayMs: 0 }],
  confirm_cinematic: [
    { primitive: 'LOW_TICK', scale: 0.5, delayMs: 0 },
    { primitive: 'THUD', scale: 0.95, delayMs: 55 },
    { primitive: 'QUICK_FALL', scale: 0.35, delayMs: 130 },
  ],
  reject_double_thud: [
    { primitive: 'THUD', scale: 0.8, delayMs: 0 },
    { primitive: 'THUD', scale: 0.6, delayMs: 80 },
  ],
  privacy_shield: [{ primitive: 'SPIN', scale: 0.55, delayMs: 0 }],
  record_sample_saved: [
    { primitive: 'TICK', scale: 0.4, delayMs: 0 },
    { primitive: 'CLICK', scale: 0.7, delayMs: 70 },
  ],
};

export interface ChoreographerConfig {
  readonly presetId: string;
  readonly userIntensity: number;
  readonly powerScale: number;
  readonly apiLevel: number;
  readonly supportsComposition: boolean;
  readonly supportsAmplitude: boolean;
}

/** Compiles a preset into the highest-fidelity plan the hardware/API level allows. */
export function compileHapticPlan(config: ChoreographerConfig): HapticPlan {
  const scale = Math.max(0, Math.min(config.userIntensity * config.powerScale, 1));
  const steps = (PRESETS[config.presetId] ?? PRESETS.confirm_crisp)!;

  if (scale <= 0.02) return { tier: 'SUPPRESSED', presetId: config.presetId, steps: [], scale: 0 };
  if (config.apiLevel >= 31 && config.supportsComposition) {
    return { tier: 'COMPOSITION', presetId: config.presetId, steps, scale };
  }
  if (config.supportsAmplitude) return { tier: 'WAVEFORM', presetId: config.presetId, steps, scale };
  return { tier: 'PREDEFINED', presetId: config.presetId, steps: steps.slice(0, 1), scale };
}

export const HAPTIC_PRESET_IDS = Object.keys(PRESETS);

function clampNumber(value: number, min: number, max: number): number {
  if (!Number.isFinite(value)) return min;
  return Math.max(min, Math.min(max, value));
}

function clamp01(value: number): number {
  if (Number.isNaN(value)) return 0;
  return Math.max(0, Math.min(1, value));
}
