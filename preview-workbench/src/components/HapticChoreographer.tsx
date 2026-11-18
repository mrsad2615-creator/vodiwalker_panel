import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import type { HapticPlan } from '../engine/vodiEngine';
import { compileHapticPlan, HAPTIC_PRESET_IDS } from '../engine/vodiEngine';
import { MOTION } from '../theme/workbenchTheme';
import styles from './HapticChoreographer.module.css';

interface HapticChoreographerProps {
  readonly powerScale: number;
  readonly apiLevel: number;
}

const PRESET_LABELS: Record<string, string> = {
  edge_detent: 'Edge Detent',
  stroke_tick: 'Velocity Tick',
  ai_lock_in: 'AI Lock-in',
  confirm_crisp: 'Confirm Crisp',
  confirm_cinematic: 'Cinematic Chord',
  reject_double_thud: 'Rejection',
  privacy_shield: 'Privacy Shield',
  record_sample_saved: 'Calibration',
};

/**
 * Haptic choreography laboratory: shows exactly which `VibrationEffect` tier the current hardware
 * and power budget resolve to, and replays the primitive timeline with motion.
 */
export function HapticChoreographer({ powerScale, apiLevel }: HapticChoreographerProps) {
  const [presetId, setPresetId] = useState('confirm_cinematic');
  const [intensity, setIntensity] = useState(0.85);
  const [playhead, setPlayhead] = useState<number | null>(null);

  const plan: HapticPlan = useMemo(
    () =>
      compileHapticPlan({
        presetId,
        userIntensity: intensity,
        powerScale,
        apiLevel,
        supportsComposition: true,
        supportsAmplitude: true,
      }),
    [apiLevel, intensity, powerScale, presetId],
  );

  const totalMs = plan.steps.reduce((sum, step) => sum + step.delayMs + 60, 0);

  return (
    <section className={styles.panel} aria-labelledby="haptics-heading">
      <header className={styles.header}>
        <h2 id="haptics-heading">Haptic choreography</h2>
        <span className={styles.tier} data-testid="haptic-tier">
          {plan.tier}
        </span>
      </header>

      <div className={styles.presets} role="group" aria-label="Haptic presets">
        {HAPTIC_PRESET_IDS.map((id) => (
          <button
            key={id}
            type="button"
            aria-pressed={presetId === id}
            className={presetId === id ? `${styles.preset} ${styles.presetActive}` : styles.preset}
            onClick={() => setPresetId(id)}
          >
            {PRESET_LABELS[id] ?? id}
          </button>
        ))}
      </div>

      <label className={styles.sliderRow}>
        <span>User intensity</span>
        <input
          type="range"
          min={0}
          max={1}
          step={0.01}
          value={intensity}
          onChange={(event) => setIntensity(Number(event.target.value))}
          aria-label="User haptic intensity"
        />
        <output className="mono-num">{(intensity * 100).toFixed(0)}%</output>
      </label>

      <div className={styles.timeline} data-testid="haptic-timeline">
        {plan.steps.length === 0 && <p className={styles.empty}>Suppressed by the power budget.</p>}
        {plan.steps.map((step, index) => {
          const amplitude = Math.max(0.12, Math.min(step.scale * plan.scale, 1));
          return (
            <motion.div
              key={`${step.primitive}-${index}`}
              className={styles.step}
              style={{ height: `${28 + amplitude * 92}px` }}
              initial={{ scaleY: 0.2, opacity: 0 }}
              animate={{
                scaleY: playhead === index ? [1, 1.14, 1] : 1,
                opacity: 1,
                boxShadow:
                  playhead === index
                    ? '0 0 32px color-mix(in oklab, var(--md-sys-color-primary) 70%, transparent)'
                    : '0 0 0 transparent',
              }}
              transition={playhead === index ? { duration: 0.42, ease: 'easeOut' } : MOTION.effects}
              onAnimationStart={() => undefined}
            >
              <span className={styles.primitive}>{step.primitive.replaceAll('_', ' ').toLowerCase()}</span>
              <span className={styles.scale}>{(step.scale * plan.scale).toFixed(2)}</span>
            </motion.div>
          );
        })}
      </div>

      <div className={styles.controls}>
        <button
          type="button"
          className={styles.play}
          onClick={() => {
            plan.steps.forEach((_, index) => {
              const delay = plan.steps
                .slice(0, index)
                .reduce((sum, step) => sum + step.delayMs, 0);
              window.setTimeout(() => setPlayhead(index), delay);
            });
            window.setTimeout(() => setPlayhead(null), Math.max(totalMs, 240));
          }}
          disabled={plan.steps.length === 0}
        >
          ▶ Replay pattern
        </button>
        <span className={styles.duration}>
          <span className="mono-num">{plan.steps.length}</span> primitives ·{' '}
          <span className="mono-num">{totalMs}</span> ms · scale{' '}
          <span className="mono-num">{plan.scale.toFixed(2)}</span>
        </span>
      </div>
    </section>
  );
}
