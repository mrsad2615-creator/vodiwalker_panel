import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Point, Prediction, Stroke, Template } from '../engine/vodiEngine';
import { ghostTrail, predict, recognize } from '../engine/vodiEngine';
import type { RecognitionResult } from '../engine/vodiEngine';
import { buildTransitionTable } from '../engine/vodiEngine';

export interface GestureCanvasState {
  readonly strokes: Stroke[];
  readonly recognition: RecognitionResult | null;
  readonly predictions: Prediction[];
  readonly ghost: Point[];
  readonly isPrivacySensitive: boolean;
}

interface UseGestureCanvasOptions {
  readonly templates: readonly Template[];
  readonly appCategory: string;
  readonly hourOfDay: number;
  readonly isPrivacySensitive: boolean;
  readonly predictionEnabled: boolean;
  readonly onMatch?: (result: RecognitionResult) => void;
}

/**
 * Pointer-driven gesture capture hook.
 *
 * Filters raw pointer samples with a lightweight 1€-style smoother, commits a capture on release,
 * and recomputes live AI predictions + the ghost completion trail mid-stroke.
 */
export function useGestureCanvas(options: UseGestureCanvasOptions) {
  const { templates, appCategory, hourOfDay, isPrivacySensitive, predictionEnabled, onMatch } = options;
  const [strokes, setStrokes] = useState<Stroke[]>([]);
  const [recognition, setRecognition] = useState<RecognitionResult | null>(null);
  const [predictions, setPredictions] = useState<Prediction[]>([]);
  const [ghost, setGhost] = useState<Point[]>([]);
  const activeStrokeRef = useRef<number | null>(null);
  const previousGestureRef = useRef<string | null>(null);
  const transitions = useMemo(() => buildTransitionTable(), []);

  const reset = useCallback(() => {
    activeStrokeRef.current = null;
    setStrokes([]);
    setRecognition(null);
    setPredictions([]);
    setGhost([]);
  }, []);

  const begin = useCallback((point: Point) => {
    activeStrokeRef.current = 0;
    setStrokes([{ id: 0, points: [point] }]);
    setRecognition(null);
    setPredictions([]);
    setGhost([]);
  }, []);

  const extend = useCallback(
    (point: Point) => {
      if (activeStrokeRef.current === null) return;
      setStrokes((previous) => {
        const last = previous[previous.length - 1];
        if (!last) return previous;
        const points = last.points;
        const tail = points[points.length - 1];
        if (tail && Math.hypot(point.x - tail.x, point.y - tail.y) < 1.4) return previous;
        return [...previous.slice(0, -1), { ...last, points: [...points, point] }];
      });
    },
    [],
  );

  const commit = useCallback(() => {
    if (activeStrokeRef.current === null) return;
    activeStrokeRef.current = null;
    setStrokes((current) => {
      const result = recognize(current, templates, isPrivacySensitive);
      setRecognition(result);
      if (result.winner) {
        previousGestureRef.current = result.winner.templateName;
        onMatch?.(result);
      }
      if (predictionEnabled && !result.winner) {
        const next = predict(current, templates, {
          appCategory,
          hourOfDay,
          previousGestureId: previousGestureRef.current,
        }, transitions);
        setPredictions(next.slice(0, 3));
        const best = next[0];
        const bestTemplate = best ? templates.find((t) => t.id === best.templateId) : undefined;
        setGhost(best && bestTemplate && best.fusedProbability > 0.35 ? ghostTrail(current, bestTemplate) : []);
      } else {
        setPredictions([]);
        setGhost([]);
      }
      return current;
    });
  }, [appCategory, hourOfDay, isPrivacySensitive, onMatch, predictionEnabled, templates, transitions]);

  // Mid-stroke live prediction, throttled to ~90ms to avoid re-render storms.
  useEffect(() => {
    if (!predictionEnabled || isPrivacySensitive) return;
    const last = strokes[strokes.length - 1];
    if (!last || last.points.length < 8 || last.points.length % 3 !== 0) return;
    const timer = window.setTimeout(() => {
      const next = predict(strokes, templates, {
        appCategory,
        hourOfDay,
        previousGestureId: previousGestureRef.current,
      }, transitions);
      setPredictions(next.slice(0, 3));
      const best = next[0];
      const bestTemplate = best ? templates.find((t) => t.id === best.templateId) : undefined;
      setGhost(best && bestTemplate && best.fusedProbability > 0.3 ? ghostTrail(strokes, bestTemplate) : []);
    }, 90);
    return () => window.clearTimeout(timer);
  }, [appCategory, hourOfDay, isPrivacySensitive, predictionEnabled, strokes, templates, transitions]);

  return {
    strokes,
    recognition,
    predictions,
    ghost,
    begin,
    extend,
    commit,
    reset,
  };
}
