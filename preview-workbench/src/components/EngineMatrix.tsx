import { motion } from 'framer-motion';
import { MOTION } from '../theme/workbenchTheme';
import styles from './EngineMatrix.module.css';

export interface EngineStatus {
  readonly id: string;
  readonly module: string;
  readonly role: string;
  readonly metrics: readonly { label: string; value: string }[];
}

interface EngineMatrixProps {
  readonly engines: readonly EngineStatus[];
}

/**
 * Multi-engine observability board: one card per Clean Architecture engine, each with the live
 * metric that proves it is doing work (latency, request counts, learned steps).
 */
export function EngineMatrix({ engines }: EngineMatrixProps) {
  return (
    <section className={styles.panel} aria-labelledby="engines-heading">
      <header className={styles.header}>
        <h2 id="engines-heading">Multi-engine matrix</h2>
        <span className={styles.count}>{engines.length} engines · pure Kotlin · zero Android coupling</span>
      </header>
      <div className={styles.grid}>
        {engines.map((engine, index) => (
          <motion.article
            key={engine.id}
            className={styles.card}
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ ...MOTION.effects, delay: index * 0.05 }}
            whileHover={{ y: -3 }}
          >
            <div className={styles.cardHead}>
              <span className={styles.dot} aria-hidden="true" />
              <h3>{engine.id}</h3>
            </div>
            <code className={styles.module}>{engine.module}</code>
            <p className={styles.role}>{engine.role}</p>
            <dl className={styles.metrics}>
              {engine.metrics.map((metric) => (
                <div key={metric.label}>
                  <dt>{metric.label}</dt>
                  <dd className="mono-num">{metric.value}</dd>
                </div>
              ))}
            </dl>
          </motion.article>
        ))}
      </div>
    </section>
  );
}
