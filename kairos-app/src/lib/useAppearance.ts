import { useEffect, useState } from 'react';
import type { MotionLevel } from './theme';

/**
 * Reads the motion level the theme engine wrote on <html data-motion>.
 *
 * CSS can switch loops off with an attribute selector, but JS-driven chart
 * entrances (SVG dash offsets) cannot — they need to know before they paint, or
 * they end up invisible when the animation is suppressed. This hook keeps them
 * in sync and re-renders when the user changes the level in the studio.
 */
export function useMotionLevel(): MotionLevel {
  const read = (): MotionLevel => {
    if (typeof document === 'undefined') return 'cinematic';
    const value = document.documentElement.dataset.motion;
    return value === 'standard' || value === 'ultra' ? value : 'cinematic';
  };

  const [level, setLevel] = useState<MotionLevel>(read);

  useEffect(() => {
    if (typeof document === 'undefined') return;
    const observer = new MutationObserver(() => setLevel(read()));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-motion'] });
    setLevel(read());
    return () => observer.disconnect();
  }, []);

  return level;
}
