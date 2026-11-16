import { describe, expect, it } from 'vitest';
import type { FocusSession, Habit, HabitLog, Task } from '@/types/domain';
import {
  computeAnnualHeatmap,
  computeAnnualReview,
  computeHabitStats,
  computeMonthlyReview,
} from '@/lib/analytics';
import { addDays, dateKeyToJalaali } from '@/lib/date';

const TODAY = '2026-11-16'; // 25 Aban 1405

const SAMPLE_HABIT: Habit = {
  id: 'hab_1',
  name: 'مطالعه روزانه',
  description: '25 صفحه',
  icon: 'book',
  accentColor: '#8052D6',
  scheduleType: 'daily',
  scheduledDays: [0, 1, 2, 3, 4, 5, 6],
  weeklyTarget: 7,
  trackingMode: 'boolean',
  targetValue: 1,
  unit: 'بار',
  reminderTime: null,
  archived: false,
  startDate: addDays(TODAY, -9), // 10 days inclusive
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('Analytics, Heatmap, Habit Stats & Monthly/Annual Review Engine', () => {
  it('strictly separates unlogged days from explicitly missed days in habit stats and heatmap', () => {
    // 10 scheduled days: 4 completed, 1 explicitly missed, 5 unlogged!
    const logs: HabitLog[] = [
      { id: '1', habitId: 'hab_1', dateKey: addDays(TODAY, -9), status: 'completed', value: 1, updatedAt: '' },
      { id: '2', habitId: 'hab_1', dateKey: addDays(TODAY, -8), status: 'missed', value: 0, updatedAt: '' },
      // days -7, -6, -5, -4, today (0) are UNLOGGED!
      { id: '3', habitId: 'hab_1', dateKey: addDays(TODAY, -3), status: 'completed', value: 1, updatedAt: '' },
      { id: '4', habitId: 'hab_1', dateKey: addDays(TODAY, -2), status: 'completed', value: 1, updatedAt: '' },
      { id: '5', habitId: 'hab_1', dateKey: addDays(TODAY, -1), status: 'completed', value: 1, updatedAt: '' },
    ];

    const stats = computeHabitStats(SAMPLE_HABIT, logs, TODAY);
    expect(stats.completedDays).toBe(4);
    expect(stats.missedDays).toBe(1);
    expect(stats.unloggedDays).toBe(5);
    expect(stats.loggedDaysCount).toBe(5);
    // Adherence over logged days = 4 / 5 = 80% (NOT 4 / 10 = 40%!)
    expect(stats.adherencePercent).toBe(80);
    // Current streak stays 3 even though today hasn't been logged yet
    expect(stats.currentStreak).toBe(3);
    expect(stats.bestStreak).toBe(3);

    const { jy } = dateKeyToJalaali(TODAY);
    const heatmap = computeAnnualHeatmap({
      jalaaliYear: jy,
      habits: [SAMPLE_HABIT],
      habitLogs: logs,
      tasks: [],
      focusSessions: [],
      todayKey: TODAY,
      mode: 'habits',
    });

    const cellMissed = heatmap.cells.find((c) => c.dateKey === addDays(TODAY, -8));
    const cellUnlogged = heatmap.cells.find((c) => c.dateKey === addDays(TODAY, -5));
    const cellCompleted = heatmap.cells.find((c) => c.dateKey === addDays(TODAY, -1));

    expect(cellMissed?.state).toBe('missed');
    expect(cellUnlogged?.state).toBe('unlogged');
    expect(cellCompleted?.state).toBe('level4');
  });

  it('produces zero fabricated stats on empty months and accurate real analytics when data exists', () => {
    const jToday = dateKeyToJalaali(TODAY);

    const emptyReport = computeMonthlyReview(jToday.jy, jToday.jm, [], [], [], [], TODAY);
    expect(emptyReport.hasAnyData).toBe(false);
    expect(emptyReport.totalTasks).toBe(0);
    expect(emptyReport.overallSuccessRate).toBe(0);
    expect(emptyReport.bestDay).toBeNull();

    const tasks: Task[] = [
      {
        id: 't1',
        title: 'طراحی داشبورد',
        description: '',
        dueDate: TODAY,
        dueTime: '10:00',
        priority: 'high',
        quadrant: 'q1',
        category: 'work',
        tags: [],
        status: 'completed',
        subtasks: [],
        recurrence: 'none',
        reminderEnabled: false,
        reminderOffsetMin: 0,
        archived: false,
        postponeCount: 0,
        createdAt: '',
        updatedAt: '',
        completedDate: TODAY,
        completedAt: '',
      },
      {
        id: 't2',
        title: 'مستندسازی API',
        description: '',
        dueDate: addDays(TODAY, -2),
        dueTime: null,
        priority: 'medium',
        quadrant: 'q2',
        category: 'work',
        tags: [],
        status: 'todo',
        subtasks: [],
        recurrence: 'none',
        reminderEnabled: false,
        reminderOffsetMin: 0,
        archived: false,
        postponeCount: 2,
        createdAt: '',
        updatedAt: '',
        completedDate: null,
        completedAt: null,
      },
    ];

    const sessions: FocusSession[] = [
      {
        id: 'f1',
        mode: 'pomodoro',
        durationMinutes: 50,
        durationSeconds: 3000,
        dateKey: TODAY,
        taskId: 't1',
        taskTitleSnapshot: 'طراحی داشبورد',
        completedNaturally: true,
        startedAt: '',
        endedAt: '',
      },
    ];

    const logs: HabitLog[] = [
      { id: 'l1', habitId: 'hab_1', dateKey: TODAY, status: 'completed', value: 1, updatedAt: '' },
    ];

    const report = computeMonthlyReview(
      jToday.jy,
      jToday.jm,
      tasks,
      [SAMPLE_HABIT],
      logs,
      sessions,
      TODAY
    );

    expect(report.hasAnyData).toBe(true);
    expect(report.totalTasks).toBe(2);
    expect(report.completedTasks).toBe(1);
    expect(report.overdueTasks).toBe(1);
    expect(report.taskSuccessRate).toBe(50);
    expect(report.totalFocusMinutes).toBe(50);
    expect(report.habitAdherenceRate).toBe(100);
    expect(report.bestDay?.dateKey).toBe(TODAY);
    expect(report.insights.length).toBeGreaterThanOrEqual(4);

    const annual = computeAnnualReview(jToday.jy, tasks, [SAMPLE_HABIT], logs, sessions, TODAY);
    expect(annual.hasAnyData).toBe(true);
    expect(annual.totalCompletedTasks).toBe(1);
    expect(annual.totalFocusMinutes).toBe(50);
    expect(annual.bestMonth?.jm).toBe(jToday.jm);
    expect(annual.weakestMonth).toBeNull(); // Only 1 active month so doesn't falsely pick an empty month!
  });
});
