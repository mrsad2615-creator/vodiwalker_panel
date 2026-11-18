import { motion } from 'framer-motion';
import type { PowerBudget, PowerTelemetry } from '../engine/vodiEngine';
import { MOTION } from '../theme/workbenchTheme';
import styles from './PowerGovernor.module.css';

interface PowerGovernorProps {
  readonly telemetry: PowerTelemetry;
  readonly budget: PowerBudget;
  readonly onChange: (patch: Partial<PowerTelemetry>) => void;
}

const MODE_COPY: Record<PowerBudget['mode'], string> = {
  PERFORMANCE: '120 Hz sampling, full ensemble, prefix prediction armed.',
  BALANCED: '90 Hz sampling with full recognition accuracy.',
  ECO: '60 Hz sampling, fast-path recognizer, haptics attenuated.',
  ULTRA_SAVER: '30 Hz sampling, overlay suspended, no prediction.',
  PRIVACY_SHIELD_SUSPENDED: 'Automation suspended over a sensitive window.',
  DOZE_SUSPENDED: 'Screen off / Doze: engines parked.',
};

const BARS: ReadonlyArray<{ key: keyof PowerBudget; label: string }> = [
  { key: 'touchSampleRateHz', label: 'Sample rate (Hz)' },
  { key: 'accessibilityTimeoutMs', label: 'Event timeout (ms)' },
];

/**
 * Battery-optimization control surface. Toggling system conditions shows the deterministic
 * governor re-resolving the execution budget in real time.
 */
export function PowerGovernor({ telemetry, budget, onChange }: PowerGovernorProps) {
  return (
    <section className={styles.panel} aria-labelledby="power-heading">
      <header className={styles.header}>
        <h2 id="power-heading">Adaptive power governor</h2>
        <motion.span
          key={budget.mode}
          className={styles.mode}
          initial={{ opacity: 0, scale: 0.92 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={MOTION.spatialFast}
          data-testid="power-mode"
        >
          {budget.mode.replaceAll('_', ' ')}
        </motion.span>
      </header>
      <p className={styles.reason}>{budget.reason} · {MODE_COPY[budget.mode]}</p>

      <div className={styles.grid}>
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={telemetry.isPrivacySensitive}
            onChange={(event) => onChange({ isPrivacySensitive: event.target.checked })}
          />
          Privacy-sensitive window
        </label>
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={telemetry.isDeviceIdle}
            onChange={(event) => onChange({ isDeviceIdle: event.target.checked })}
          />
          Doze / idle
        </label>
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={telemetry.isPowerSaveMode}
            onChange={(event) => onChange({ isPowerSaveMode: event.target.checked })}
          />
          System battery saver
        </label>
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={telemetry.isCharging}
            onChange={(event) => onChange({ isCharging: event.target.checked })}
          />
          Charging
        </label>
      </div>

      <label className={styles.sliderRow}>
        <span>Battery</span>
        <input
          type="range"
          min={0}
          max={100}
          value={telemetry.batteryPercent}
          onChange={(event) => onChange({ batteryPercent: Number(event.target.value) })}
          aria-label="Battery percentage"
        />
        <output className="mono-num">{telemetry.batteryPercent}%</output>
      </label>

      <label className={styles.sliderRow}>
        <span>Thermal</span>
        <select
          value={telemetry.thermalSeverity}
          onChange={(event) => onChange({ thermalSeverity: event.target.value as PowerTelemetry['thermalSeverity'] })}
          aria-label="Thermal severity"
        >
          {(['NONE', 'LIGHT', 'MODERATE', 'SEVERE', 'CRITICAL', 'EMERGENCY'] as const).map((level) => (
            <option key={level} value={level}>
              {level}
            </option>
          ))}
        </select>
      </label>

      <div className={styles.bars}>
        {BARS.map(({ key, label }) => {
          const value = Number(budget[key]);
          const maximum = key === 'touchSampleRateHz' ? 120 : 600;
          return (
            <div key={key} className={styles.bar}>
              <span>{label}</span>
              <div className={styles.track}>
                <motion.div
                  className={styles.fill}
                  initial={false}
                  animate={{ width: `${Math.min((value / maximum) * 100, 100)}%` }}
                  transition={MOTION.spatialExpressive}
                />
              </div>
              <strong className="mono-num">{value}</strong>
            </div>
          );
        })}
      </div>

      <ul className={styles.flags}>
        <li data-active={budget.fullEnsemble}>Full ensemble</li>
        <li data-active={budget.prefixPrediction}>Prefix prediction</li>
        <li data-active={budget.overlayRendering}>Overlay rendering</li>
        <li data-active={budget.listenContentChanges}>Content events</li>
        <li>
          <span className="mono-num">{budget.estimatedBatteryPercentPerHour.toFixed(2)}</span> %/h
        </li>
      </ul>
    </section>
  );
}
