/**
 * Scroll chrome.
 *
 * The top bar is not a decoration: on a phone it competes for every pixel of a
 * 6-inch screen. Flagship behaviour is that it tucks itself away the moment the
 * reader starts reading downward, and comes back the instant they reverse
 * direction (or reach the top). This module is the whole decision rule, kept
 * pure so it can be proven without a layout engine.
 */
export interface ScrollChrome {
  /** Last scroll offset we made a decision from. */
  lastY: number;
  /** True while the bar is tucked away. */
  hidden: boolean;
}

export const CHROME_TOP_ANCHOR = 28;
export const CHROME_JITTER = 6;

export const INITIAL_CHROME: ScrollChrome = { lastY: 0, hidden: false };

/**
 * Resolves the next bar state for a scroll offset.
 *
 * Returns the *same* object when nothing changed, so React can bail out of the
 * render — a scroll loop must never re-render for sub-pixel movement.
 */
export function resolveScrollChrome(state: ScrollChrome, y: number): ScrollChrome {
  const safeY = Math.max(0, Math.round(Number.isFinite(y) ? y : 0));

  if (Math.abs(safeY - state.lastY) < CHROME_JITTER) return state;

  // At the top the bar is always shown, and a programmatic jump back to the top
  // (tab change) must reveal it too.
  const hidden = safeY <= CHROME_TOP_ANCHOR ? false : safeY > state.lastY;

  if (hidden === state.hidden) return { lastY: safeY, hidden };
  return { lastY: safeY, hidden };
}
