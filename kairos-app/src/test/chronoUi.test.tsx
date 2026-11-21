import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';
import { BOOT_TIMEOUT } from './utils';

/**
 * The Chrono UI as a reader meets it: four modes on a rule, the plan laid out as a
 * rail, and one switch that means "keep going".
 */

async function openFocus() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
  await waitFor(() => expect(screen.getByTestId('home-ready')).toBeInTheDocument(), { timeout: BOOT_TIMEOUT });
  fireEvent.click(screen.getByRole('button', { name: 'تمرکز' }));
  await waitFor(() => expect(screen.getByText('محیط تمرکز')).toBeInTheDocument());
}

describe('Chrono Core — the focus screen', () => {
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

  it('offers all four modes, each with its own configuration', async () => {
    await openFocus();

    for (const label of ['پومودورو', 'بلوک‌های عمیق', 'شمارش معکوس', 'زمان‌سنج آزاد']) {
      expect(screen.getByRole('button', { name: new RegExp(label) })).toBeInTheDocument();
    }
    // The hint is the real configuration, not decoration.
    expect(screen.getByRole('button', { name: /پومودورو/ })).toHaveTextContent('25/5');
  });

  it('lays the session plan out as a rail you can jump around', async () => {
    await openFocus();

    const plan = screen.getByRole('region', { name: 'برنامهٔ جلسه' });
    const chips = within(plan).getAllByRole('button');
    // Default pomodoro: four cycles × (focus + break).
    expect(chips).toHaveLength(8);
    expect(chips[0]).toHaveAccessibleName(/بخش 1: تمرکز، 25 دقیقه/);
    expect(chips[0]).toHaveAttribute('aria-current', 'step');

    // Jumping to the fourth segment moves the "now" marker with it.
    fireEvent.click(chips[3]);
    await waitFor(() =>
      expect(
        within(screen.getByRole('region', { name: 'برنامهٔ جلسه' })).getAllByRole('button')[3]
      ).toHaveAttribute('aria-current', 'step')
    );
  });

  it('rebuilds the plan when the mode changes', async () => {
    await openFocus();

    fireEvent.click(screen.getByRole('button', { name: /بلوک‌های عمیق/ }));

    await waitFor(() => {
      const plan = screen.getByRole('region', { name: 'برنامهٔ جلسه' });
      const chips = within(plan).getAllByRole('button');
      // Four 45-minute blocks with three rests between them.
      expect(chips).toHaveLength(7);
      expect(chips[0]).toHaveAccessibleName(/بلوک 1، 45 دقیقه/);
    });
  });

  it('keeps the no-interruption offer native-only and opt-in', async () => {
    await openFocus();
    // The web build must not pretend it can silence a phone.
    expect(
      screen.queryByRole('switch', { name: 'بدون مزاحمت در جلسه' })
    ).toBeNull();

    await waitFor(async () => {
      const settings = await db.settings.get('default');
      expect(settings?.dndInFocus).toBe(false);
      expect(settings?.autoAdvance).toBe(false);
    });
  });

  it('toggles auto-advance as a real switch and stores the choice', async () => {
    await openFocus();

    const toggle = screen.getByRole('switch', { name: 'ادامهٔ خودکار بخش‌ها' });
    expect(toggle).toHaveAttribute('aria-checked', 'false');

    fireEvent.click(toggle);
    await waitFor(() =>
      expect(screen.getByRole('switch', { name: 'ادامهٔ خودکار بخش‌ها' })).toHaveAttribute(
        'aria-checked',
        'true'
      )
    );
    expect(screen.getByText(/بدون توقف از تمرکز به استراحت/)).toBeInTheDocument();

    await waitFor(async () => {
      const settings = await db.settings.get('default');
      expect(settings?.autoAdvance).toBe(true);
    });
  });
});
