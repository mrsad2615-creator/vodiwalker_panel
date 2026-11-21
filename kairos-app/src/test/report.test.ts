import { describe, expect, it } from 'vitest';
import { buildWeeklyReport, faDigits, reportToText } from '@/lib/report';
import type { FocusSession, HabitLog, Task } from '@/types/domain';

/**
 * The report is a string first. These tests pin the numbers and the wording, so a
 * shared message can never silently claim something the data does not support.
 */

const todayKey = '2026-11-20';

const focus = (dateKey: string, minutes: number): FocusSession => ({
  id: `f-${dateKey}-${minutes}`,
  mode: 'pomodoro',
  durationMinutes: minutes,
  durationSeconds: minutes * 60,
  dateKey,
  taskId: null,
  taskTitleSnapshot: null,
  completedNaturally: true,
  startedAt: `${dateKey}T10:00:00.000Z`,
  endedAt: `${dateKey}T10:${String(minutes % 60).padStart(2, '0')}:00.000Z`,
});

const done = (completedDate: string, id = completedDate): Task =>
  ({ id, status: 'completed', completedDate, archived: false, dueDate: completedDate }) as Task;

const log = (dateKey: string): HabitLog => ({
  id: `h::${dateKey}`,
  habitId: 'h',
  dateKey,
  status: 'completed',
  value: 1,
  updatedAt: `${dateKey}T09:00:00.000Z`,
});

describe('Shareable weekly report', () => {
  it('counts only the last seven days and compares with the seven before', () => {
    const report = buildWeeklyReport(
      {
        tasks: [done('2026-11-19'), done('2026-11-18'), done('2026-11-01')],
        habitLogs: [log('2026-11-19'), log('2026-11-17')],
        // 8 days ago is outside the window; 2 days ago is inside.
        focusSessions: [focus('2026-11-12', 50), focus('2026-11-18', 25), focus('2026-11-19', 35)],
      },
      todayKey
    );

    expect(report.startKey).toBe('2026-11-14');
    expect(report.endKey).toBe('2026-11-20');
    expect(report.focusMinutes).toBe(60);
    expect(report.focusSessions).toBe(2);
    expect(report.tasksDone).toBe(2);
    expect(report.habitsDone).toBe(2);
    expect(report.activeDays).toBe(3);
    expect(report.hasData).toBe(true);
  });

  it('reports the best day by activity, not by focus alone', () => {
    const report = buildWeeklyReport(
      {
        tasks: [done('2026-11-17'), done('2026-11-17', 'b'), done('2026-11-17', 'c')],
        habitLogs: [],
        focusSessions: [focus('2026-11-15', 90)],
      },
      todayKey
    );
    // 3 tasks = 30 activity vs 90 focus minutes.
    expect(report.bestDay?.dateKey).toBe('2026-11-15');
  });

  it('says plainly that a quiet week had no activity', () => {
    const report = buildWeeklyReport({ tasks: [], habitLogs: [], focusSessions: [] }, todayKey);
    expect(report.hasData).toBe(false);
    expect(report.deltaPercent).toBeNull();

    const text = reportToText(report);
    expect(text).toContain('هیچ فعالیتی ثبت نشده');
    // No invented trend line.
    expect(text).not.toContain('روند');
  });

  it('writes Persian digits and a real trend into the text', () => {
    const report = buildWeeklyReport(
      {
        tasks: [done('2026-11-19')],
        habitLogs: [log('2026-11-19')],
        focusSessions: [focus('2026-11-19', 40), focus('2026-11-13', 10)],
      },
      todayKey
    );
    const text = reportToText(report);

    expect(text.split('\n')[0]).toBe('کایروس · گزارش ۷ روزه');
    // The 10-minute session on 11-13 belongs to the *previous* window.
    expect(text).toContain('تمرکز: ۴۰ دقیقه در ۱ جلسه');
    // Dates are Persian digits too.
    expect(text).toContain('آبان');
    expect(text).toContain('وظایف انجام‌شده: ۱');
    expect(text).toContain('روزهای فعال: ۱ از ۷');
    expect(text).toContain('روند نسبت به دورهٔ قبل');
    // Latin digits must not leak into a Persian sentence.
    expect(/[0-9]/.test(text)).toBe(false);
  });

  it('converts digits without touching words', () => {
    expect(faDigits('2026 · 14')).toBe('۲۰۲۶ · ۱۴');
    expect(faDigits(0)).toBe('۰');
  });
});
