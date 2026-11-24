import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, renderHook, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { clearAllUserData, createTask, db, ensureSettings, exportBackup } from '@/lib/db';
import { chooseSampleVisibility } from '@/lib/sampleVisibility';
import { useFocusSessions, useGoals, useHabitLogs, useHabits, useProjects, useProjectStages, useTasks } from '@/lib/queries';
import { SAMPLE_PROJECT_ID } from '@/lib/sampleProject';

beforeEach(async () => { window.localStorage.clear(); await clearAllUserData(); await db.activeRun.clear(); await db.settings.clear(); });
async function realTask() {
  return createTask({ title: 'کار واقعی', description: '', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
}
async function showSetup() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
  await screen.findByRole('heading', { name: 'با داده‌های نمونه شروع کنیم؟' });
}

describe('first-run sample choice', () => {
  it('defaults to enabled but does not add data before confirmation, including upgrades', async () => {
    const real = await realTask();
    await showSetup();
    expect(screen.getByRole('checkbox', { name: /نمایش دادهٔ نمونه/ })).toBeChecked();
    expect(await db.tasks.count()).toBe(1);
    fireEvent.click(screen.getByRole('button', { name: 'تأیید و ورود' }));
    await screen.findByTestId('home-ready');
    expect(await db.tasks.get(real.id)).toEqual(real);
    expect(await db.projects.get(SAMPLE_PROJECT_ID)).toMatchObject({ sample: true });
    expect((await db.settings.get('default'))?.sampleSetupComplete).toBe(true);
  });

  it('can enter with samples off and enable them later', async () => {
    await showSetup();
    fireEvent.click(screen.getByRole('checkbox', { name: /نمایش دادهٔ نمونه/ }));
    fireEvent.click(screen.getByRole('button', { name: 'تأیید و ورود' }));
    await screen.findByTestId('home-ready');
    expect(await db.tasks.count()).toBe(0);
    expect((await db.settings.get('default'))?.sampleVisible).toBe(false);
    await chooseSampleVisibility(true);
    expect(await db.tasks.count()).toBeGreaterThan(0);
    expect((await db.settings.get('default'))?.sampleVisible).toBe(true);
  });

  it('rolls back the setup and all records on a collision', async () => {
    await ensureSettings();
    await db.projects.add({ id: SAMPLE_PROJECT_ID, title: 'کاربر', description: '', category: 'work', status: 'active', goalId: null, startDate: null, dueDate: null, createdAt: 'x', updatedAt: 'x' });
    await expect(chooseSampleVisibility(true, true)).rejects.toThrow();
    expect(await db.tasks.count()).toBe(0);
    expect((await db.settings.get('default'))?.sampleSetupComplete).not.toBe(true);
    expect((await db.projects.get(SAMPLE_PROJECT_ID))?.title).toBe('کاربر');
  });
});

describe('hide and restore samples', () => {
  it('the settings switch hides and restores samples in the task list', async () => {
    await chooseSampleVisibility(true, true);
    const real = await realTask();
    const sample = (await db.tasks.toArray()).find((task) => task.sample && !task.archived)!;
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await screen.findByTestId('home-ready');
    fireEvent.click(screen.getByRole('button', { name: 'وظایف' }));
    await screen.findByText(sample.title);
    fireEvent.click(screen.getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(await screen.findByRole('button', { name: 'بخش تنظیمات' }));
    const toggle = await screen.findByRole('switch', { name: 'نمایش داده‌های نمونه' });
    expect(toggle).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(toggle);
    await waitFor(() => expect(toggle).toHaveAttribute('aria-checked', 'false'));
    fireEvent.click(screen.getByRole('button', { name: 'وظایف' }));
    await screen.findByText(real.title);
    await waitFor(() => expect(screen.queryByText(sample.title)).not.toBeInTheDocument());
    const chart = screen.getByRole('figure', { name: 'نمای وضعیت وظایف' });
    expect(within(chart).getByRole('listitem', { name: 'انجام‌نشده: 1 کار' })).toBeInTheDocument();
    expect(await db.tasks.get(sample.id)).toBeDefined();
  });

  it('filters each normal query while keeping edits and exports intact', async () => {
    const real = await realTask();
    await chooseSampleVisibility(true, true);
    const sampleTask = (await db.tasks.toArray()).find((row) => row.sample)!;
    await db.tasks.update(sampleTask.id, { title: 'نمونهٔ ویرایش‌شده' });
    const { result } = renderHook(() => ({ tasks: useTasks(), habits: useHabits(), logs: useHabitLogs(), focus: useFocusSessions(), goals: useGoals(), projects: useProjects(), stages: useProjectStages() }));
    await waitFor(() => expect(result.current.habits.length).toBeGreaterThan(0));
    await chooseSampleVisibility(false);
    await waitFor(() => {
      expect(result.current.tasks.map((row) => row.id)).toEqual([real.id]);
      expect(result.current.habits).toHaveLength(0);
      expect(result.current.logs).toHaveLength(0);
      expect(result.current.focus).toHaveLength(0);
      expect(result.current.goals).toHaveLength(0);
      expect(result.current.projects).toHaveLength(0);
      expect(result.current.stages).toHaveLength(0);
    });
    const backup = await exportBackup();
    expect(backup.tasks.find((task) => task.id === sampleTask.id)?.title).toBe('نمونهٔ ویرایش‌شده');
    await chooseSampleVisibility(true);
    await waitFor(() => expect(result.current.tasks.some((task) => task.title === 'نمونهٔ ویرایش‌شده')).toBe(true));
  });
});
