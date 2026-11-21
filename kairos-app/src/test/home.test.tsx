import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { createHabit, createTask, db } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import { BOOT_TIMEOUT } from './utils';

/**
 * Home is the reference screen: one heading, three numbers, and a *real* list.
 * These tests assert the behaviour a reader would notice if it broke — the day's
 * numbers, completing a task, logging a habit, and the honest empty state.
 */

async function boot() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
  await waitFor(() => expect(screen.getByTestId('home-ready')).toBeInTheDocument(), { timeout: BOOT_TIMEOUT });
}

const todayKey = getTodayKey();

describe('Home — امروز', () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await db.transaction(
      'rw',
      [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings, db.activeRun],
      async () => {
        await Promise.all([
          db.tasks.clear(),
          db.habits.clear(),
          db.habitLogs.clear(),
          db.focusSessions.clear(),
          db.goals.clear(),
          db.settings.clear(),
          db.activeRun.clear(),
        ]);
      }
    );
  });

  it('opens with a greeting, the Persian date and three honest numbers', async () => {
    await boot();
    const home = screen.getByTestId('home-ready');

    expect(within(home).getByRole('heading', { level: 1 })).toBeInTheDocument();
    // Weekday names are rendered in Persian, never as ISO dates.
    expect(home.textContent).toMatch(/شنبه|یکشنبه|دوشنبه|سه‌شنبه|چهارشنبه|پنجشنبه|جمعه/);
    // The three numbers of the day.
    expect(within(home).getByText('کار')).toBeInTheDocument();
    expect(within(home).getByText('عادت')).toBeInTheDocument();
    expect(within(home).getByText('دقیقه تمرکز')).toBeInTheDocument();
  });

  it('says a day is empty instead of drawing an empty chart', async () => {
    await boot();
    expect(screen.getByText('امروز کاری برنامه‌ریزی نشده است.')).toBeInTheDocument();
    expect(screen.getByText(/روز خالی است/)).toBeInTheDocument();
  });

  it('lists today’s work and completes it from the row', async () => {
    await createTask({
      title: 'مرور قرارداد',
      description: '',
      dueDate: todayKey,
      dueTime: '10:30',
      priority: 'high',
      quadrant: 'q1',
      category: 'work',
      tags: [],
      status: 'todo',
      subtasks: [],
      recurrence: null,
      reminderEnabled: false,
    } as never);

    await boot();
    // Live queries settle a tick after the shell paints.
    expect(await screen.findByText('مرور قرارداد', {}, { timeout: BOOT_TIMEOUT })).toBeInTheDocument();
    expect(screen.getByText('امروز 10:30')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'تکمیل مرور قرارداد' }));
    await waitFor(
      async () => {
        const stored = await db.tasks.toCollection().first();
        expect(stored?.status).toBe('completed');
      },
      { timeout: BOOT_TIMEOUT }
    );
  });

  it('logs a habit with one tap and shows the streak dot', async () => {
    const habit = await createHabit({
      name: 'مطالعهٔ شبانه',
      description: '',
      icon: 'book',
      accentColor: '#C9A063',
      scheduleType: 'daily',
      scheduledDays: [],
      weeklyTarget: 7,
      trackingMode: 'simple',
      targetValue: 1,
      unit: '',
      reminderTime: null,
      startDate: todayKey,
    } as never);

    await boot();
    const toggle = await screen.findByRole('button', { name: 'مطالعهٔ شبانه — ثبت نشده' }, { timeout: BOOT_TIMEOUT });
    fireEvent.click(toggle);

    await waitFor(
      async () => {
        const log = await db.habitLogs.get(`${habit.id}::${todayKey}`);
        expect(log?.status).toBe('completed');
      },
      { timeout: BOOT_TIMEOUT }
    );
    await waitFor(
      () =>
        expect(
          screen.getByRole('button', { name: 'مطالعهٔ شبانه — انجام شد' })
        ).toBeInTheDocument(),
      { timeout: BOOT_TIMEOUT }
    );
  });

  it('keeps the week summary to one sentence and links to analytics', async () => {
    await boot();
    const week = screen.getByRole('region', { name: 'این هفته' });
    expect(within(week).getByText(/این هفته/)).toBeInTheDocument();

    fireEvent.click(within(week).getByRole('button', { name: /تحلیل کامل/ }));
    await waitFor(() => expect(screen.getByText('تحلیل و گزارش عملکرد')).toBeInTheDocument(), {
      timeout: BOOT_TIMEOUT,
    });
  });
});
