import { describe, expect, it } from 'vitest';
import {
  DEFAULT_CHRONO_CONFIG,
  buildPlan,
  describePlan,
  planFocusSeconds,
  planTotalSeconds,
  planWorkSegments,
  type ClockSegment,
} from '@/lib/chrono/plan';
import { chronoConfigFromSettings, modeHint, modeLabel } from '@/lib/chrono/config';
import { DEFAULT_SETTINGS } from '@/lib/db';

/**
 * A plan is the whole session, so these tests describe the four modes the app
 * can run — including the exact segment order, which is what the clock walks.
 */

const kinds = (plan: ClockSegment[]) => plan.map((segment) => segment.kind);

describe('Chrono Core — plans', () => {
  it('builds a pomodoro plan that ends every cycle on its break, longest last', () => {
    const plan = buildPlan('pomodoro', { ...DEFAULT_CHRONO_CONFIG, cyclesBeforeLong: 3 });
    expect(plan).toHaveLength(6);
    expect(kinds(plan)).toEqual(['focus', 'break', 'focus', 'break', 'focus', 'break']);
    expect(plan[0].seconds).toBe(25 * 60);
    expect(plan[1].seconds).toBe(5 * 60);
    // The long break closes the set.
    expect(plan[5].seconds).toBe(15 * 60);
    expect(plan[5].long).toBe(true);
    expect(plan[3].long).toBeFalsy();
  });

  it('builds interval blocks with a rest between them and none after the last', () => {
    const plan = buildPlan('interval', {
      ...DEFAULT_CHRONO_CONFIG,
      blocks: 3,
      blockMinutes: 50,
      restMinutes: 8,
    });
    expect(kinds(plan)).toEqual(['focus', 'break', 'focus', 'break', 'focus']);
    expect(plan[0].label).toBe('بلوک 1');
    expect(plan[4].label).toBe('بلوک 3');
    expect(plan[4].seconds).toBe(50 * 60);
    expect(planTotalSeconds(plan)).toBe(3 * 50 * 60 + 2 * 8 * 60);
    expect(planFocusSeconds(plan)).toBe(3 * 50 * 60);
    expect(planWorkSegments(plan)).toBe(3);
  });

  it('reduces countdown to a single focus segment and free to one open segment', () => {
    const countdown = buildPlan('countdown', { ...DEFAULT_CHRONO_CONFIG, countdownMinutes: 12 });
    expect(countdown).toHaveLength(1);
    expect(countdown[0]).toMatchObject({ kind: 'focus', seconds: 720 });

    const free = buildPlan('free', DEFAULT_CHRONO_CONFIG);
    expect(free).toHaveLength(1);
    expect(free[0].seconds).toBe(0);
    // An open segment has no total, by definition.
    expect(planTotalSeconds(free)).toBe(0);
    expect(describePlan(free)).toBe('بدون پایان خودکار');
  });

  it('clamps hostile configuration instead of trusting it', () => {
    const plan = buildPlan('interval', {
      ...DEFAULT_CHRONO_CONFIG,
      blocks: 999,
      blockMinutes: -4,
      restMinutes: Number.NaN,
    });
    // Blocks are capped, minutes fall back to 1 and never go negative.
    expect(planWorkSegments(plan)).toBe(24);
    expect(plan[0].seconds).toBe(60);
    expect(plan[1].seconds).toBeGreaterThan(0);
  });

  it('describes a plan in Persian for the UI', () => {
    const plan = buildPlan('interval', { ...DEFAULT_CHRONO_CONFIG, blocks: 2, blockMinutes: 30, restMinutes: 10 });
    expect(describePlan(plan)).toBe('2 بخش کار · مجموع 70 دقیقه');
  });

  it('reads its numbers from settings and labels each mode', () => {
    const config = chronoConfigFromSettings(DEFAULT_SETTINGS);
    expect(config.blockMinutes).toBe(DEFAULT_SETTINGS.intervalBlockMin);
    expect(config.countdownMinutes).toBe(DEFAULT_SETTINGS.countdownMin);
    expect(modeHint('pomodoro', config)).toBe('25/5');
    expect(modeHint('interval', config)).toBe('4×45');
    expect(modeHint('free', config)).toBe('بی‌پایان');
    expect(modeLabel('interval')).toBe('بلوک‌های عمیق');
    expect(modeLabel('countdown')).toBe('شمارش معکوس');
  });
});
