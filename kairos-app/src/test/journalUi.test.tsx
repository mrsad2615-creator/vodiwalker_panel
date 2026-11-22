import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';
import { addDays, getTodayKey } from '@/lib/date';

/**
 * The two 3.2 features as a user meets them: the journal card on Home, and the
 * year view's .ics import. Both are tested through the real shell, because the
 * interesting part is that the data actually lands in IndexedDB.
 */

const today = getTodayKey();

async function boot() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
  await waitFor(() => expect(screen.getByTestId('home-ready')).toBeInTheDocument(), {
    timeout: 8000,
  });
}

describe('KAIROS 3.2 — ژورنال و ورود تقویم', () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await db.transaction(
      'rw',
      [db.tasks, db.journal, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings],
      async () => {
        await Promise.all([
          db.tasks.clear(),
          db.journal.clear(),
          db.habits.clear(),
          db.habitLogs.clear(),
          db.focusSessions.clear(),
          db.goals.clear(),
        ]);
      }
    );
  });

  it('writes today’s mood from the home card and opens the note sheet', async () => {
    await boot();

    expect(screen.getByText('ژورنال امروز')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'حال خوب' }));

    await waitFor(async () => {
      const row = await db.journal.get(`journal-${today}`);
      expect(row?.mood).toBe(4);
    });

    const sheet = await screen.findByRole('dialog', { name: 'ژورنال امروز' });
    expect(sheet).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('بهترین لحظهٔ امروز'), {
      target: { value: 'بخش سخت گزارش تمام شد' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'ذخیرهٔ روز' }));

    await waitFor(async () => {
      const row = await db.journal.get(`journal-${today}`);
      expect(row?.highlight).toBe('بخش سخت گزارش تمام شد');
    });
  });

  it('imports a .ics file into real tasks, once', async () => {
    await boot();
    // Calendar sits behind «بیشتر» in the rail.
    fireEvent.click(screen.getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(await screen.findByRole('button', { name: 'بخش تقویم' }));
    await waitFor(() => expect(screen.getByText('تقویم و برنامه‌ریزی')).toBeInTheDocument());

    const ics = [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'BEGIN:VEVENT',
      `DTSTART;VALUE=DATE:${addDays(today, 2).replace(/-/g, '')}`,
      'SUMMARY:جلسهٔ بودجه',
      'UID:budget@google.com',
      'END:VEVENT',
      'END:VCALENDAR',
    ].join('\r\n');

    const input = screen.getByLabelText('انتخاب فایل تقویم') as HTMLInputElement;
    fireEvent.change(input, {
      target: { files: [new File([ics], 'calendar.ics', { type: 'text/calendar' })] },
    });

    await waitFor(
      async () => {
        const tasks = await db.tasks.toArray();
        expect(tasks.some((task) => task.title === 'جلسهٔ بودجه')).toBe(true);
      },
      { timeout: 8000 }
    );

    // Re-importing the same file is a no-op, not a duplicate.
    const before = await db.tasks.count();
    fireEvent.change(input, {
      target: { files: [new File([ics], 'calendar.ics', { type: 'text/calendar' })] },
    });
    await waitFor(() => expect(screen.getByText('ورود تقویم انجام شد')).toBeInTheDocument());
    expect(await db.tasks.count()).toBe(before);
  });
});
