import { useCallback, useEffect, useMemo, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { GestureCanvas } from './components/GestureCanvas';
import { HapticChoreographer } from './components/HapticChoreographer';
import { PowerGovernor } from './components/PowerGovernor';
import { PermissionFlow } from './components/PermissionFlow';
import type { PermissionState } from './components/PermissionFlow';
import { EngineMatrix } from './components/EngineMatrix';
import type { EngineStatus } from './components/EngineMatrix';
import { TEMPLATES } from './data/templates';
import { useGestureCanvas } from './hooks/useGestureCanvas';
import type { PowerTelemetry } from './engine/vodiEngine';
import { evaluatePower } from './engine/vodiEngine';
import { applyScheme, MOTION, SCHEME_OPTIONS } from './theme/workbenchTheme';
import type { SchemeId } from './theme/workbenchTheme';
import styles from './App.module.css';

const TABS = ['Studio', 'Haptics', 'Power', 'Privacy'] as const;
type Tab = (typeof TABS)[number];

/** Root workbench: scheme switcher, adaptive navigation, and the four interactive modules. */
export default function App() {
  const [schemeId, setSchemeId] = useState<SchemeId>('dark');
  const [tab, setTab] = useState<Tab>('Studio');
  const [appCategory, setAppCategory] = useState('MEDIA_VIDEO');
  const [lastMatch, setLastMatch] = useState<string | null>(null);

  const [telemetry, setTelemetry] = useState<PowerTelemetry>({
    batteryPercent: 74,
    isCharging: false,
    isPowerSaveMode: false,
    isDeviceIdle: false,
    isScreenInteractive: true,
    thermalSeverity: 'NONE',
    isPrivacySensitive: false,
    gesturesPerMinute: 9,
    preferredMode: null,
  });

  const [permissions, setPermissions] = useState<PermissionState>({
    disclosureAccepted: false,
    accessibilityEnabled: false,
    notificationsGranted: false,
    restrictedSettingsLikely: true,
  });

  const budget = useMemo(() => evaluatePower(telemetry), [telemetry]);
  const hourOfDay = useMemo(() => new Date().getHours(), []);

  const schemeOption = SCHEME_OPTIONS.find((option) => option.id === schemeId) ?? SCHEME_OPTIONS[0]!;

  useEffect(() => {
    const mode = schemeId.startsWith('light') ? 'light' : 'dark';
    applyScheme(document.documentElement, schemeOption.scheme, mode);
  }, [schemeId, schemeOption]);

  const handleMatch = useCallback((templateName: string) => {
    setLastMatch(`${templateName} · ${new Date().toLocaleTimeString()}`);
  }, []);

  const gesture = useGestureCanvas({
    templates: TEMPLATES,
    appCategory,
    hourOfDay,
    isPrivacySensitive: telemetry.isPrivacySensitive,
    predictionEnabled: budget.prefixPrediction,
    onMatch: (result) => {
      if (result.winner) handleMatch(result.winner.templateName);
    },
  });

  const engines: EngineStatus[] = useMemo(
    () => [
      {
        id: 'Gesture Engine',
        module: ':engine:gesture',
        role: 'Hybrid Protractor + $P point-cloud + banded DTW + directional guard.',
        metrics: [
          { label: 'templates', value: String(TEMPLATES.length) },
          {
            label: 'confidence',
            value: `${(((gesture.recognition?.candidates[0]?.confidence ?? 0) * 100) | 0)}%`,
          },
        ],
      },
      {
        id: 'Prediction Engine',
        module: ':engine:prediction',
        role: 'Online MLP + Markov chain, ghost-trail completion, no network calls.',
        metrics: [
          { label: 'hypotheses', value: String(gesture.predictions.length) },
          { label: 'context', value: appCategory.replaceAll('_', ' ').toLowerCase() },
        ],
      },
      {
        id: 'Smart-Action Engine',
        module: ':engine:smartaction',
        role: 'DAG runner with circuit breakers, retries, fallbacks and anti-recursion tokens.',
        metrics: [
          { label: 'graph', value: lastMatch ? 'armed' : 'idle' },
          { label: 'mode', value: budget.mode === 'PERFORMANCE' ? 'full' : 'fast' },
        ],
      },
      {
        id: 'Haptics Engine',
        module: ':engine:haptics',
        role: 'VibrationEffect.Composition → waveform → predefined tier negotiation.',
        metrics: [
          { label: 'gain', value: budget.hapticScale.toFixed(2) },
          { label: 'tier', value: budget.touchSampleRateHz > 0 ? 'composition' : 'off' },
        ],
      },
      {
        id: 'Power Engine',
        module: ':engine:power',
        role: 'Deterministic budget: privacy → Doze → thermal → saver → battery → preference.',
        metrics: [
          { label: 'battery', value: `${budget.estimatedBatteryPercentPerHour.toFixed(2)}%/h` },
          { label: 'sample', value: `${budget.touchSampleRateHz}Hz` },
        ],
      },
    ],
    [appCategory, budget, gesture.predictions.length, gesture.recognition, lastMatch],
  );

  return (
    <div className={styles.shell}>
      <a className={styles.skip} href="#main">
        Skip to workbench
      </a>

      <header className={styles.topBar}>
        <div className={styles.brand}>
          <span className={styles.mark} aria-hidden="true">
            ⧉
          </span>
          <div>
            <h1>VodiGesture</h1>
            <p>Obsidian Cinema · Material Design 3 workbench</p>
          </div>
        </div>

        <nav className={styles.schemes} aria-label="Color scheme">
          {SCHEME_OPTIONS.map((option) => (
            <button
              key={option.id}
              type="button"
              aria-pressed={schemeId === option.id}
              data-active={schemeId === option.id}
              className={styles.schemeButton}
              onClick={() => setSchemeId(option.id)}
              title={option.description}
            >
              {option.label}
            </button>
          ))}
        </nav>
      </header>

      <nav className={styles.tabs} aria-label="Workbench sections">
        {TABS.map((item) => (
          <button
            key={item}
            type="button"
            aria-selected={tab === item}
            role="tab"
            className={styles.tab}
            data-active={tab === item}
            onClick={() => setTab(item)}
          >
            {tab === item && (
              <motion.span
                layoutId="tab-pill"
                className={styles.tabPill}
                transition={MOTION.spatialExpressive}
              />
            )}
            <span className={styles.tabLabel}>{item}</span>
          </button>
        ))}
      </nav>

      <main id="main" className={styles.main}>
        <AnimatePresence mode="wait">
          <motion.div
            key={tab}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={MOTION.effects}
            className={styles.tabPanel}
          >
            {tab === 'Studio' && (
              <div className={styles.studioGrid}>
                <GestureCanvas
                  strokes={gesture.strokes}
                  ghost={gesture.ghost}
                  recognition={gesture.recognition}
                  predictions={gesture.predictions}
                  hoveredTemplateId={null}
                  isLocked={!permissions.accessibilityEnabled}
                  onBegin={gesture.begin}
                  onExtend={gesture.extend}
                  onCommit={gesture.commit}
                  onReset={gesture.reset}
                />
                <aside className={styles.side}>
                  <section className={styles.card}>
                    <h2>Foreground context</h2>
                    <div className={styles.segmented} role="group" aria-label="Foreground app category">
                      {['MEDIA_VIDEO', 'BROWSER', 'PRODUCTIVITY', 'SENSITIVE_PROTECTED'].map((category) => (
                        <button
                          key={category}
                          type="button"
                          data-active={appCategory === category}
                          onClick={() => setAppCategory(category)}
                        >
                          {category.replaceAll('_', ' ')}
                        </button>
                      ))}
                    </div>
                    <p className={styles.note}>
                      Category priors re-weight the prediction engine — no app content is inspected.
                    </p>
                  </section>
                  <section className={styles.card}>
                    <h2>Template library</h2>
                    <ul className={styles.templates}>
                      {TEMPLATES.map((template) => (
                        <li key={template.id}>
                          <span className={styles.glyph} aria-hidden="true">
                            {template.glyph}
                          </span>
                          <div>
                            <strong>{template.name}</strong>
                            <span>{template.category.replaceAll('_', ' ').toLowerCase()}</span>
                          </div>
                          <span className={styles.minConfidence}>
                            {(template.minConfidence * 100) | 0}%
                          </span>
                        </li>
                      ))}
                    </ul>
                  </section>
                </aside>
              </div>
            )}

            {tab === 'Haptics' && (
              <div className={styles.singleColumn}>
                <HapticChoreographer powerScale={budget.hapticScale} apiLevel={34} />
                <EngineMatrix engines={engines} />
              </div>
            )}

            {tab === 'Power' && (
              <div className={styles.singleColumn}>
                <PowerGovernor
                  telemetry={telemetry}
                  budget={budget}
                  onChange={(patch) => setTelemetry((current) => ({ ...current, ...patch }))}
                />
                <EngineMatrix engines={engines} />
              </div>
            )}

            {tab === 'Privacy' && (
              <div className={styles.singleColumn}>
                <PermissionFlow
                  state={permissions}
                  onChange={(patch) => setPermissions((current) => ({ ...current, ...patch }))}
                />
                <EngineMatrix engines={engines} />
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </main>

      <footer className={styles.footer}>
        <span>
          Kotlin 2.1 · Compose M3 1.3 · Room 2.7 · Hilt 2.55 · API 29–35 · 34 JVM tests passing
        </span>
        <span className={styles.footerStatus}>
          {lastMatch ? `last match: ${lastMatch}` : 'no gesture matched yet'}
        </span>
      </footer>
    </div>
  );
}
