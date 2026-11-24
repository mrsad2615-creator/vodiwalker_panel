import { completeFirstRun } from '@/test/helpers/firstRun';
import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { clearAllUserData, db, ensureSettings, exportBackup, importBackup, setTaskStatus, upsertHabitLog } from '@/lib/db';
import { populateStarterWorkspace } from '@/lib/seed';
import { changeShortcut, DEFAULT_SHORTCUTS, normalizeShortcuts } from '@/lib/navigation';
import { dailyLineIndex, MOTIVATION_LINES } from '@/lib/motivation';

beforeEach(async () => {
  window.localStorage.clear();
  await clearAllUserData();
  await db.activeRun.clear();
  await db.settings.clear();
  await ensureSettings();
});

async function boot() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await completeFirstRun();
  await screen.findByTestId('home-ready');
}

describe('shortcut model', () => {
  it('rejects corrupted settings and swaps an existing destination without duplicates', () => {
    expect(normalizeShortcuts(['tasks', 'tasks', 'focus', 'settings'])).toEqual(DEFAULT_SHORTCUTS);
    const original = [...DEFAULT_SHORTCUTS];
    const swapped = changeShortcut(original, 0, 'focus');
    expect(original).toEqual(DEFAULT_SHORTCUTS);
    expect(swapped).toEqual(['focus', 'habits', 'tasks', 'motivation']);
    expect(new Set(swapped).size).toBe(4);
    expect(changeShortcut(swapped, 1, 'calendar')).toEqual(['focus', 'calendar', 'tasks', 'motivation']);
  });

  it('offers six destinations, saves custom shortcuts, and keeps every section in the directory', async () => {
    await db.settings.update('default', { navShortcuts: ['tasks', 'habits', 'focus', 'motivation'] });
    await boot();
    const nav = screen.getByRole('navigation', { name: 'ناوبری اصلی' });
    expect(within(nav).getAllByRole('button')).toHaveLength(6);
    expect(within(nav).getByRole('button', { name: 'خانه' })).toHaveAttribute('aria-current', 'page');
    fireEvent.click(within(nav).getByRole('button', { name: 'دستیار' }));
    expect(within(nav).getByRole('button', { name: 'دستیار' })).toHaveAttribute('aria-current', 'page');
    expect(await screen.findByRole('region', { name: 'مرکز فرمان' })).toBeVisible();
    expect(within(nav).getByRole('button', { name: 'بیشتر' })).not.toHaveAttribute('aria-current');
    expect(normalizeShortcuts((await db.settings.get('default'))?.navShortcuts)).toEqual(DEFAULT_SHORTCUTS);
    fireEvent.click(within(nav).getByRole('button', { name: 'بیشتر' }));
    const directory = await screen.findByRole('dialog', { name: 'بخش‌های دیگر' });
    for (const label of ['خانه', 'دستیار', 'وظایف', 'عادت‌ها', 'تمرکز', 'انگیزه', 'پروژه‌ها', 'تقویم', 'تحلیل عملکرد', 'تنظیمات']) {
      expect(within(directory).getByRole('button', { name: `بخش ${label}` })).toBeInTheDocument();
    }
    fireEvent.click(within(directory).getByRole('button', { name: 'شخصی‌سازی' }));
    fireEvent.change(within(directory).getByLabelText('میان‌بر 1'), { target: { value: 'projects' } });
    fireEvent.click(within(directory).getByRole('button', { name: 'ذخیرهٔ میان‌برها' }));
    await waitFor(async () => expect((await db.settings.get('default'))?.navShortcuts?.[0]).toBe('projects'));
    expect((await db.settings.get('default'))?.navShortcuts?.[3]).toBe('motivation');
    fireEvent.click(screen.getByRole('button', { name: 'بستن فهرست' }));
    await waitFor(() => expect(within(nav).getByRole('button', { name: 'پروژه‌ها' })).toBeInTheDocument());
    fireEvent.click(within(nav).getByRole('button', { name: 'پروژه‌ها' }));
    await screen.findByTestId('projects-ready');
    expect(within(nav).getByRole('button', { name: 'پروژه‌ها' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('button', { name: 'خانه' })).not.toHaveAttribute('aria-current');
  });
});

describe('motivation', () => {
  it('has a stable, local daily selection with unique identifiable entries', () => {
    expect(new Set(MOTIVATION_LINES.map((item) => item.id)).size).toBe(MOTIVATION_LINES.length);
    expect(dailyLineIndex('2026-01-01', MOTIVATION_LINES.length)).toBe(dailyLineIndex('2026-01-01', MOTIVATION_LINES.length));
    expect(dailyLineIndex('2026-01-01', 0)).toBe(0);
  });

  it('filters, advances and persists favorites in the local database', async () => {
    await boot();
    fireEvent.click(within(screen.getByRole('navigation', { name: 'ناوبری اصلی' })).getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(within(screen.getByRole('dialog', { name: 'بخش‌های دیگر' })).getByRole('button', { name: 'بخش انگیزه' }));
    const page = await screen.findByTestId('motivation-page');
    fireEvent.click(within(page).getByRole('button', { name: 'قدرت' }));
    const first = within(page).getByRole('blockquote').textContent;
    fireEvent.click(within(page).getByRole('button', { name: 'افزودن به علاقه‌مندی‌ها' }));
    await waitFor(async () => expect((await db.settings.get('default'))?.motivationFavorites).toHaveLength(1));
    fireEvent.click(within(page).getByRole('button', { name: 'جملهٔ بعدی' }));
    expect(within(page).getByRole('blockquote').textContent).not.toBe(first);
    fireEvent.click(within(page).getByRole('button', { name: 'علاقه‌مندی‌ها' }));
    expect(within(page).getByRole('blockquote').textContent).toBe(first);
    await waitFor(() => expect(within(page).getByRole('button', { name: 'حذف از علاقه‌مندی‌ها' })).toBeEnabled());
    fireEvent.click(within(page).getByRole('button', { name: 'حذف از علاقه‌مندی‌ها' }));
    await waitFor(async () => expect((await db.settings.get('default'))?.motivationFavorites).toHaveLength(0));
    await within(page).findByText('هنوز جمله‌ای ذخیره نکرده‌ای');
  });
});

describe('sample safety', () => {
  it('requires explicit confirmation from the dashboard', async () => {
    await boot();
    fireEvent.click(screen.getByRole('button', { name: /افزودن داده‌های نمونه/ }));
    const confirm = await screen.findByRole('region', { name: 'تأیید داده‌های نمونه' });
    expect(await db.tasks.count()).toBe(0);
    fireEvent.click(within(confirm).getByRole('button', { name: 'انصراف' }));
    expect(await db.tasks.count()).toBe(0);
    fireEvent.click(screen.getByRole('button', { name: /افزودن داده‌های نمونه/ }));
    fireEvent.click(within(screen.getByRole('region', { name: 'تأیید داده‌های نمونه' })).getByRole('button', { name: 'تأیید و افزودن نمونه' }));
    await waitFor(async () => expect(await db.tasks.count()).toBeGreaterThan(0));
    await screen.findByText(/آمار و تحلیل‌ها می‌توانند ترکیبی/);
  });

  it('aborts the whole sample transaction on an ID collision', async () => {
    await populateStarterWorkspace();
    const task = (await db.tasks.toArray())[0];
    await clearAllUserData();
    await db.tasks.add({ ...task, sample: false, title: 'رکورد واقعی محافظت‌شده' });
    await expect(populateStarterWorkspace()).rejects.toThrow();
    expect((await db.tasks.toArray()).map((row) => row.title)).toEqual(['رکورد واقعی محافظت‌شده']);
    expect(await db.habits.count()).toBe(0);
    expect((await db.settings.get('default'))?.hasSampleData).toBe(false);
  });

  it('retains sample provenance in recurrence, logs and backup restoration', async () => {
    await populateStarterWorkspace();
    const task = (await db.tasks.toArray()).find((row) => row.recurrence === 'weekly' && row.status === 'todo');
    expect(task).toBeDefined();
    const result = await setTaskStatus(task!.id, 'completed');
    expect(result.spawnedNext?.sample).toBe(true);
    const habit = (await db.habits.toArray())[0];
    expect((await upsertHabitLog(habit.id, '2030-01-01', 'completed', 1)).sample).toBe(true);
    const backup = await exportBackup();
    await clearAllUserData();
    await importBackup(backup);
    expect((await db.settings.get('default'))?.hasSampleData).toBe(true);
    expect((await db.tasks.toArray()).some((row) => row.sample)).toBe(true);
    expect((await db.habits.toArray()).every((row) => row.reminderTime === null)).toBe(true);
  });

  it('keeps the global warning for legacy backup samples without changing existing rows', async () => {
    await populateStarterWorkspace();
    const backup = await exportBackup();
    backup.tasks = backup.tasks.map(({ sample: _sample, ...task }) => task);
    backup.habits = [];
    backup.habitLogs = [];
    backup.focusSessions = [];
    backup.goals = [];
    await clearAllUserData();
    await db.settings.clear();
    await importBackup(backup);
    expect((await db.settings.get('default'))?.hasSampleData).toBe(true);
    expect((await db.tasks.toArray()).every((row) => row.sample === undefined)).toBe(true);
  });
});
