import { AnimatePresence, motion } from 'framer-motion';
import { MOTION } from '../theme/workbenchTheme';
import styles from './PermissionFlow.module.css';

export interface PermissionState {
  readonly disclosureAccepted: boolean;
  readonly accessibilityEnabled: boolean;
  readonly notificationsGranted: boolean;
  readonly restrictedSettingsLikely: boolean;
}

interface PermissionFlowProps {
  readonly state: PermissionState;
  readonly onChange: (patch: Partial<PermissionState>) => void;
}

interface Step {
  readonly key: keyof PermissionState;
  readonly title: string;
  readonly body: string;
  readonly action: string;
}

const STEPS: readonly Step[] = [
  {
    key: 'disclosureAccepted',
    title: '1 · Accessibility data disclosure',
    body: 'Google Play Prominent Disclosure: what is read (foreground package, window changes), what is never read (text, passwords, sensitive Android 14+ views).',
    action: 'Review & consent',
  },
  {
    key: 'accessibilityEnabled',
    title: '2 · Gesture accessibility service',
    body: 'BIND_ACCESSIBILITY_SERVICE, canPerformGestures, flagRetrieveInteractiveWindows. Powers the TYPE_ACCESSIBILITY_OVERLAY handle without draw-over-other-apps.',
    action: 'Open system settings',
  },
  {
    key: 'notificationsGranted',
    title: '3 · Status alerts (optional)',
    body: 'Android 13+ POST_NOTIFICATIONS, used only for macro diagnostics. Everything works without it.',
    action: 'Allow',
  },
];

/**
 * Reproduces the Android 14/15 permission journey, including the Restricted Settings guidance that
 * sideloaded installs require before the accessibility toggle becomes available.
 */
export function PermissionFlow({ state, onChange }: PermissionFlowProps) {
  const ready = state.disclosureAccepted && state.accessibilityEnabled;

  return (
    <section className={styles.panel} aria-labelledby="permissions-heading">
      <header className={styles.header}>
        <h2 id="permissions-heading">Permission & privacy flow</h2>
        <span className={ready ? `${styles.badge} ${styles.badgeReady}` : styles.badge} data-testid="permission-readiness">
          {ready ? 'automation armed' : 'locked'}
        </span>
      </header>

      <ol className={styles.steps}>
        {STEPS.map((step, index) => {
          const complete = state[step.key];
          const enabled = index === 0 || state.disclosureAccepted;
          return (
            <motion.li
              key={step.key}
              className={styles.step}
              data-complete={complete}
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...MOTION.effects, delay: index * 0.06 }}
            >
              <div className={styles.stepHead}>
                <h3>{step.title}</h3>
                <span className={styles.status}>{complete ? 'granted' : enabled ? 'pending' : 'blocked'}</span>
              </div>
              <p>{step.body}</p>
              <button
                type="button"
                className={styles.action}
                disabled={complete || !enabled}
                onClick={() =>
                  onChange(
                    step.key === 'accessibilityEnabled'
                      ? // Granting the service proves Restricted Settings no longer blocks it.
                        { accessibilityEnabled: true, restrictedSettingsLikely: false }
                      : { [step.key]: true },
                  )
                }
              >
                {complete ? 'Completed' : step.action}
              </button>
            </motion.li>
          );
        })}
      </ol>

      <AnimatePresence>
        {state.accessibilityEnabled && state.restrictedSettingsLikely && (
          <motion.div
            className={styles.restricted}
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={MOTION.effects}
          >
            <strong>Restricted Settings active (Android 13–15)</strong>
            <p>
              Sideloaded builds hide the accessibility toggle until you open App info → ⋮ →
              “Allow restricted settings”. After that, return here and enable VodiGesture.
            </p>
            <button
              type="button"
              className={styles.action}
              onClick={() => onChange({ accessibilityEnabled: true, restrictedSettingsLikely: false })}
            >
              Simulate unlock
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      <footer className={styles.footer}>
        <span>Zero INTERNET permission · zero text capture · local-only model weights</span>
        <button
          type="button"
          className={styles.reset}
          onClick={() =>
            onChange({
              disclosureAccepted: false,
              accessibilityEnabled: false,
              notificationsGranted: false,
            })
          }
        >
          Reset flow
        </button>
      </footer>
    </section>
  );
}
