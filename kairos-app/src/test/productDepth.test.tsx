import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';
import { BOOT_TIMEOUT } from './utils';

/**
 * Bundle C as a reader meets it: one box over the whole workspace, and a weekly
 * report you can actually send somewhere.
 */

async function boot() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
  await waitFor(() => expect(screen.getByTestId('home-ready')).toBeInTheDocument(), { timeout: BOOT_TIMEOUT });
}

async function openSearch() {
  await boot();
  fireEvent.click(screen.getByRole('button', { name: 'جست‌وجوی سراسری' }));
  return waitFor(() => screen.getByRole('dialog', { name: 'جست‌وجوی سراسری' }));
}

/** Always ask the document, never a node captured before an async render. */
const sheet = () => screen.getByRole('dialog', { name: 'جست‌وجوی سراسری' });

describe('Bundle C — global search and shareable report', () => {
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

  it('opens the box, ranks sections, and navigates on Enter', async () => {
    await openSearch();
    const input = within(sheet()).getByLabelText(/جست‌وجو در وظایف/);

    fireEvent.change(input, { target: { value: 'تقویم' } });
    const options = await within(sheet()).findAllByRole('option');
    expect(options[0]).toHaveTextContent('تقویم');

    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() => expect(screen.getByText('تقویم و برنامه‌ریزی')).toBeInTheDocument());
    expect(screen.queryByRole('dialog', { name: 'جست‌وجوی سراسری' })).toBeNull();
  });

  it('moves the highlight with the arrow keys before choosing', async () => {
    await openSearch();
    const input = within(sheet()).getByLabelText(/جست‌وجو در وظایف/);
    fireEvent.change(input, { target: { value: 'ی' } });

    const options = await within(sheet()).findAllByRole('option');
    expect(options.length).toBeGreaterThan(1);
    expect(options[0]).toHaveAttribute('aria-selected', 'true');

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    await waitFor(() =>
      expect(within(sheet()).getAllByRole('option')[1]).toHaveAttribute('aria-selected', 'true')
    );

    // Enter opens whatever is highlighted; the point here is that the sheet
    // closes and the workspace takes over.
    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'جست‌وجوی سراسری' })).toBeNull()
    );
  });

  it('says so when nothing matches, and Escape closes', async () => {
    await openSearch();
    const input = within(sheet()).getByLabelText(/جست‌وجو در وظایف/);

    fireEvent.change(input, { target: { value: 'قققققق' } });
    expect(await within(sheet()).findByText(/چیزی با/)).toBeInTheDocument();

    fireEvent.keyDown(input, { key: 'Escape' });
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'جست‌وجوی سراسری' })).toBeNull()
    );
  });

  it('shows the weekly report text and copies it on the web', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    });

    await openSearch();
    const input = within(sheet()).getByLabelText(/جست‌وجو در وظایف/);
    fireEvent.change(input, { target: { value: 'تحلیل' } });
    await within(sheet()).findAllByRole('option');
    fireEvent.keyDown(input, { key: 'Enter' });

    await waitFor(() => expect(screen.getByText('تحلیل و گزارش عملکرد')).toBeInTheDocument());
    const card = await screen.findByRole('region', { name: 'گزارش قابل اشتراک' });
    expect(within(card).getByText('گزارش این هفته')).toBeInTheDocument();
    // The preview is the text that will be shared, verbatim.
    expect(within(card).getByText(/کایروس · گزارش ۷ روزه/)).toBeInTheDocument();
    expect(within(card).getByText(/تمرکز: /)).toBeInTheDocument();

    fireEvent.click(within(card).getByRole('button', { name: /اشتراک‌گذاری/ }));
    await waitFor(() => expect(writeText).toHaveBeenCalled());
    expect(writeText.mock.calls[0][0]).toContain('کایروس · گزارش ۷ روزه');
  });
});
