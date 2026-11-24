import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { clearAllUserData, db, exportBackup, importBackup } from '@/lib/db';
import { addSampleProject, SAMPLE_PROJECT_ID } from '@/lib/sampleProject';
import { populateStarterWorkspace } from '@/lib/seed';

beforeEach(async () => { window.localStorage.clear(); await clearAllUserData(); await db.activeRun.clear(); });

describe('sample project', () => {
  it('adds a linked, marked project with the sample pack and restores its warning', async () => {
    await populateStarterWorkspace('2026-01-01');
    const project = await db.projects.get(SAMPLE_PROJECT_ID);
    expect(project?.sample).toBe(true);
    expect(await db.projectStages.where('projectId').equals(SAMPLE_PROJECT_ID).count()).toBe(3);
    expect(await db.tasks.where('projectId').equals(SAMPLE_PROJECT_ID).count()).toBe(2);
    const backup = await exportBackup();
    backup.tasks = []; backup.habits = []; backup.habitLogs = []; backup.focusSessions = []; backup.goals = [];
    backup.settings.hasSampleData = false;
    await clearAllUserData();
    await importBackup(backup);
    expect((await db.settings.get('default'))?.hasSampleData).toBe(true);
    expect((await db.projects.get(SAMPLE_PROJECT_ID))?.sample).toBe(true);
  });

  it('refuses a collision and rolls back all related records', async () => {
    await db.projectStages.add({ id: 'sample-v1:stage-plan', projectId: 'real', title: 'واقعی', kind: 'stage', order: 0, weight: 1, done: false, dueDate: null, completedAt: null, createdAt: 'x', updatedAt: 'x' });
    await expect(addSampleProject()).rejects.toThrow();
    expect(await db.projects.count()).toBe(0);
    expect((await db.projectStages.get('sample-v1:stage-plan'))?.title).toBe('واقعی');
    expect(await db.tasks.count()).toBe(0);
  });

  it('asks for confirmation and supports users with older sample data', async () => {
    await db.settings.update('default', { hasSampleData: true });
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await screen.findByTestId('home-ready');
    fireEvent.click(screen.getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(await screen.findByRole('button', { name: 'بخش پروژه‌ها' }));
    await screen.findByTestId('projects-ready');
    fireEvent.click(screen.getByRole('button', { name: 'افزودن پروژهٔ نمونه' }));
    const confirm = screen.getByRole('region', { name: 'تأیید پروژهٔ نمونه' });
    expect(await db.projects.count()).toBe(0);
    fireEvent.click(within(confirm).getByRole('button', { name: 'تأیید پروژهٔ نمونه' }));
    await waitFor(async () => expect((await db.projects.get(SAMPLE_PROJECT_ID))?.sample).toBe(true));
    await screen.findByText('نمونه · برنامه‌ریزی یک پروژه');
  });
});
