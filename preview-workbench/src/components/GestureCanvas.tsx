import { useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import type { Point, Prediction, Stroke } from '../engine/vodiEngine';
import type { RecognitionResult } from '../engine/vodiEngine';
import { MOTION } from '../theme/workbenchTheme';
import styles from './GestureCanvas.module.css';

interface GestureCanvasProps {
  readonly strokes: readonly Stroke[];
  readonly ghost: readonly Point[];
  readonly recognition: RecognitionResult | null;
  readonly predictions: readonly Prediction[];
  readonly hoveredTemplateId: string | null;
  readonly isLocked: boolean;
  readonly onBegin: (point: Point) => void;
  readonly onExtend: (point: Point) => void;
  readonly onCommit: () => void;
  readonly onReset: () => void;
}

const VIEWBOX = { width: 720, height: 420 };

/** Renders a polyline as a smoothed path using quadratic midpoints. */
function toPath(points: readonly Point[]): string {
  if (points.length === 0) return '';
  if (points.length === 1) {
    const p = points[0]!;
    return `M ${p.x} ${p.y} l 0.1 0.1`;
  }
  let path = `M ${points[0]!.x} ${points[0]!.y}`;
  for (let i = 1; i < points.length - 1; i += 1) {
    const current = points[i]!;
    const next = points[i + 1]!;
    path += ` Q ${current.x} ${current.y} ${(current.x + next.x) / 2} ${(current.y + next.y) / 2}`;
  }
  const last = points[points.length - 1]!;
  path += ` L ${last.x} ${last.y}`;
  return path;
}

/**
 * The cinematic gesture surface: layered aura, live ink, AI ghost completion trail and an
 * arrival ripple. Pointer events are captured on the SVG so the whole bed is drawable.
 */
export function GestureCanvas(props: GestureCanvasProps) {
  const { strokes, ghost, recognition, predictions, hoveredTemplateId, isLocked } = props;
  const { onBegin, onExtend, onCommit, onReset } = props;
  const svgRef = useRef<SVGSVGElement | null>(null);
  const drawingRef = useRef(false);

  const toLocal = useCallback((event: React.PointerEvent<SVGSVGElement>): Point => {
    const svg = svgRef.current;
    if (!svg) return { x: 0, y: 0, t: performance.now() };
    const rect = svg.getBoundingClientRect();
    const scaleX = VIEWBOX.width / rect.width;
    const scaleY = VIEWBOX.height / rect.height;
    return {
      x: (event.clientX - rect.left) * scaleX,
      y: (event.clientY - rect.top) * scaleY,
      t: performance.now(),
    };
  }, []);

  const handlePointerDown = (event: React.PointerEvent<SVGSVGElement>) => {
    if (isLocked) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    drawingRef.current = true;
    onBegin(toLocal(event));
  };

  const handlePointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    if (!drawingRef.current || isLocked) return;
    onExtend(toLocal(event));
  };

  const handlePointerUp = () => {
    if (!drawingRef.current) return;
    drawingRef.current = false;
    onCommit();
  };

  const top = predictions[0];
  const confidence = recognition?.winner?.confidence ?? recognition?.candidates[0]?.confidence ?? 0;

  return (
    <section className={styles.surface} aria-label="Gesture calibration canvas">
      <div className={styles.hud}>
        <div>
          <p className={styles.hudLabel}>Recognition surface</p>
          <p className={styles.hudValue} data-testid="canonical-gesture-name">
            {recognition?.winner?.templateName ?? recognition?.rejectedReason?.replaceAll('_', ' ').toLowerCase() ?? 'awaiting stroke'}
          </p>
        </div>
        <dl className={styles.metrics}>
          <div>
            <dt>Confidence</dt>
            <dd className="mono-num" data-testid="canonical-confidence">{(confidence * 100).toFixed(1)}%</dd>
          </div>
          <div>
            <dt>Margin</dt>
            <dd className="mono-num">{((recognition?.margin ?? 0) * 100).toFixed(1)}%</dd>
          </div>
          <div>
            <dt>Latency</dt>
            <dd className="mono-num" data-testid="canonical-latency">
              {(recognition?.latencyMs ?? 0).toFixed(2)} ms
            </dd>
          </div>
        </dl>
      </div>

      <svg
        ref={svgRef}
        className={styles.svg}
        viewBox={`0 0 ${VIEWBOX.width} ${VIEWBOX.height}`}
        role="img"
        aria-label="Draw a gesture to recognize it"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
      >
        <defs>
          <linearGradient id="inkGradient" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="var(--md-sys-color-primary)" />
            <stop offset="55%" stopColor="var(--md-sys-color-tertiary)" />
            <stop offset="100%" stopColor="var(--md-sys-color-secondary)" />
          </linearGradient>
          <radialGradient id="auraGradient">
            <stop offset="0%" stopColor="var(--md-sys-color-primary)" stopOpacity="0.34" />
            <stop offset="70%" stopColor="var(--md-sys-color-tertiary)" stopOpacity="0.05" />
            <stop offset="100%" stopColor="var(--md-sys-color-surface)" stopOpacity="0" />
          </radialGradient>
          <filter id="glow" x="-40%" y="-40%" width="180%" height="180%">
            <feGaussianBlur stdDeviation="7" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <rect width={VIEWBOX.width} height={VIEWBOX.height} fill="url(#auraGradient)" rx="24" />

        {/* 12×8 dot grid keeps the surface spatially legible without visual noise. */}
        <g aria-hidden="true" opacity="0.5">
          {Array.from({ length: 12 }, (_, column) =>
            Array.from({ length: 8 }, (_, row) => (
              <circle
                key={`${column}-${row}`}
                cx={30 + column * 60}
                cy={26 + row * 52}
                r={1.4}
                fill="var(--md-sys-color-outline)"
                opacity={0.45}
              />
            )),
          )}
        </g>

        {ghost.length > 1 && (
          <motion.path
            d={toPath(ghost)}
            fill="none"
            stroke="var(--md-sys-color-tertiary)"
            strokeWidth={5}
            strokeLinecap="round"
            strokeDasharray="10 12"
            initial={{ opacity: 0 }}
            animate={{ opacity: [0.15, 0.85, 0.35] }}
            transition={{ duration: 1.9, repeat: Infinity, ease: 'easeInOut' }}
            filter="url(#glow)"
            aria-hidden="true"
          />
        )}

        {strokes.map((stroke) => (
          <path
            key={stroke.id}
            d={toPath(stroke.points)}
            fill="none"
            stroke="url(#inkGradient)"
            strokeWidth={9}
            strokeLinecap="round"
            strokeLinejoin="round"
            filter={recognition?.winner ? 'url(#glow)' : undefined}
          />
        ))}

        {strokes.length > 0 && strokes[strokes.length - 1]!.points.length > 0 && (
          <motion.circle
            cx={strokes[strokes.length - 1]!.points.at(-1)!.x}
            cy={strokes[strokes.length - 1]!.points.at(-1)!.y}
            r={11}
            fill="var(--md-sys-color-on-primary)"
            initial={{ scale: 0.4, opacity: 0.9 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={MOTION.spatialFast}
          />
        )}

        {recognition?.winner && (
          <motion.circle
            key={recognition.winner.templateId}
            cx={VIEWBOX.width / 2}
            cy={VIEWBOX.height / 2}
            r={40}
            fill="none"
            stroke="var(--md-sys-color-primary)"
            initial={{ scale: 0.2, opacity: 0.85 }}
            animate={{ scale: 6.4, opacity: 0 }}
            transition={{ duration: 0.72, ease: MOTION.emphasizedDecelerate }}
            aria-hidden="true"
          />
        )}

        {hoveredTemplateId && (
          <text x={VIEWBOX.width - 24} y={36} textAnchor="end" className={styles.canvasHint}>
            template preview
          </text>
        )}
      </svg>

      <div className={styles.footer}>
        <div className={styles.chips} role="status" aria-live="polite">
          {predictions.length === 0 && <span className={styles.hint}>Draw slowly to see live AI prediction</span>}
          {predictions.map((prediction, index) => (
            <motion.span
              key={prediction.templateId}
              className={styles.chip}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ ...MOTION.effects, delay: index * 0.05 }}
            >
              {prediction.templateName}
              <strong className="mono-num">{(prediction.fusedProbability * 100).toFixed(0)}%</strong>
            </motion.span>
          ))}
          {top?.shouldPrewarm && <span className={styles.prewarm}>pre-warming action graph</span>}
        </div>
        <button type="button" className={styles.resetButton} onClick={onReset}>
          Clear canvas
        </button>
      </div>
    </section>
  );
}
