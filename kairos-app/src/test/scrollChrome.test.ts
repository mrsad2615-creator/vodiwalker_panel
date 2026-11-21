import { describe, expect, it } from 'vitest';
import {
  CHROME_JITTER,
  CHROME_TOP_ANCHOR,
  INITIAL_CHROME,
  resolveScrollChrome,
  type ScrollChrome,
} from '@/lib/scrollChrome';

const scroll = (state: ScrollChrome, ...steps: number[]) =>
  steps.reduce((acc, y) => resolveScrollChrome(acc, y), state);

describe('resolveScrollChrome', () => {
  it('starts visible and stays visible near the top', () => {
    expect(INITIAL_CHROME.hidden).toBe(false);
    expect(scroll(INITIAL_CHROME, 0, 4, CHROME_TOP_ANCHOR).hidden).toBe(false);
  });

  it('tucks the bar away as soon as the reader moves down', () => {
    expect(scroll(INITIAL_CHROME, 0, 220).hidden).toBe(true);
  });

  it('brings it straight back when the reader reverses', () => {
    const hidden = scroll(INITIAL_CHROME, 400);
    expect(hidden.hidden).toBe(true);
    expect(resolveScrollChrome(hidden, 340).hidden).toBe(false);
  });

  it('always reveals at the top, even after a long scroll', () => {
    const hidden = scroll(INITIAL_CHROME, 900, 1200);
    expect(hidden.hidden).toBe(true);
    const top = resolveScrollChrome(hidden, 0);
    expect(top.hidden).toBe(false);
    expect(top.lastY).toBe(0);
  });

  it('ignores jitter and returns the identical object (no re-render)', () => {
    const state = scroll(INITIAL_CHROME, 0, 120);
    const before = state.lastY;
    const jitter = resolveScrollChrome(state, before + CHROME_JITTER - 1);
    expect(jitter).toBe(state);
  });

  it('holds the visible state when the offset changes but the decision does not', () => {
    const state = scroll(INITIAL_CHROME, 0, 10);
    const later = resolveScrollChrome(state, 10 + CHROME_JITTER + 1);
    // Still above the anchor → visible, but it must remember the new offset.
    expect(later.hidden).toBe(false);
    expect(later.lastY).toBe(10 + CHROME_JITTER + 1);
  });

  it('survives nonsense offsets without hiding the bar', () => {
    expect(resolveScrollChrome(INITIAL_CHROME, Number.NaN).hidden).toBe(false);
    expect(resolveScrollChrome(INITIAL_CHROME, -80).hidden).toBe(false);
  });
});
