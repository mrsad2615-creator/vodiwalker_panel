import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';

/**
 * The board mode chips used to look dead: `chooseMode` only wrote the settings
 * row, and the board read that row back through a live query, so a tap showed
 * nothing until the Dexie write round-tripped (or never, if it was refused).
 * These tests pin the fixed contract: one tap == one visible board, immediately,
 * with localStorage carrying the choice across a hard reload.
 */

const HOME_MODE_KEY = 'kairos:home-mode';

async function bootHome() {
  const view = render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
  await waitFor(() => expect(screen.getByTestId('home-ready')).toBeInTheDocument(), {
    timeout: 8000,
  });
  return view;
}

describe('Home board modes', () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await db.transaction(
      'rw',
      [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings],
      async () => {
        await Promise.all([
          db.tasks.clear(),
          db.habits.clear(),
          db.habitLogs.clear(),
          db.focusSessions.clear(),
          db.goals.clear(),
          db.settings.clear(),
        ]);
      }
    );
  });

  it('switches to the detailed board on the tap itself, not on the DB write', async () => {
    await bootHome();

    // Cinematic is the default; the detailed ledgers are not on screen.
    expect(screen.queryByText('کارهای مهم امروز')).not.toBeInTheDocument();
    const detailedChip = screen.getByRole('button', { name: 'جزئی' });
    expect(detailedChip).toHaveAttribute('aria-pressed', 'false');

    fireEvent.click(detailedChip);

    // No waitFor on purpose: the board must follow the tap synchronously.
    expect(screen.getByText('کارهای مهم امروز')).toBeInTheDocument();
    expect(screen.getByText('عادت‌های امروز')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'جزئی' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(window.localStorage.getItem(HOME_MODE_KEY)).toBe('detailed');
  });

  it('collapses to the compact grid and back again with one tap each', async () => {
    await bootHome();

    fireEvent.click(screen.getByRole('button', { name: 'فشرده' }));
    expect(screen.getByRole('button', { name: 'فشرده' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    // Compact hides the full ledgers and the upcoming-calendar showcase.
    expect(screen.queryByText('کارهای مهم امروز')).not.toBeInTheDocument();
    expect(window.localStorage.getItem(HOME_MODE_KEY)).toBe('compact');

    fireEvent.click(screen.getByRole('button', { name: 'سینمایی' }));
    expect(screen.getByRole('button', { name: 'سینمایی' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(screen.getByText('روند دو هفته')).toBeInTheDocument();
    expect(window.localStorage.getItem(HOME_MODE_KEY)).toBe('cinematic');
  });

  it('restores the chosen board after a reload even before settings load', async () => {
    const first = await bootHome();
    fireEvent.click(screen.getByRole('button', { name: 'جزئی' }));
    expect(window.localStorage.getItem(HOME_MODE_KEY)).toBe('detailed');

    first.unmount();

    // Fresh mount: the settings row still says "cinematic", the mirror says
    // "detailed" — the board must open where the reader left it.
    await bootHome();
    expect(screen.getByRole('button', { name: 'جزئی' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    expect(screen.getByText('کارهای مهم امروز')).toBeInTheDocument();
  });
});
