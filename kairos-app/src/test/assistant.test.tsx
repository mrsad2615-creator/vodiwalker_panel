import { completeFirstRun } from '@/test/helpers/firstRun';
import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';
import { addDays, getTodayKey } from '@/lib/date';

/**
 * The three new surfaces, tested as a user meets them: a project you can build
 * stage by stage, an insight card that must not lie when there is no data, and a
 * command box that answers from real rows while admitting what it does not know.
 */

async function boot() {
  render(<App />);
  fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await completeFirstRun();
  await waitFor(() => expect(screen.getByTestId('home-ready')).toBeInTheDocument(), {
    timeout: 8000,
  });
}

beforeEach(async () => {
  window.localStorage.clear();
  await db.transaction(
    'rw',
    [
      db.tasks,
      db.habits,
      db.habitLogs,
      db.focusSessions,
      db.goals,
      db.journal,
      db.projects,
      db.projectStages,
      db.settings,
      db.eventHistory,
    ],
    async () => {
      await Promise.all([
        db.tasks.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.focusSessions.clear(),
        db.goals.clear(),
        db.journal.clear(),
        db.projects.clear(),
        db.projectStages.clear(),
        db.eventHistory.clear(),
      ]);
    }
  );
});

describe('projects room', () => {
  it('creates a project and adds a weighted stage to it', async () => {
    await boot();

    fireEvent.click(screen.getByRole('button', { name: 'بیشتر' }));
    fireEvent.click(await screen.findByRole('button', { name: /پروژه‌ها/ }));

    await waitFor(() => expect(screen.getByTestId('projects-ready')).toBeInTheDocument());

    fireEvent.click(screen.getByRole('button', { name: 'پروژه جدید' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('نام پروژه'), { target: { value: 'کتاب' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'ساختن' }));

    await waitFor(async () => {
      expect(await db.projects.count()).toBe(1);
    });

    // Creating keeps the editor open on the new project, so the stages can be
    // written straight away instead of hunting the card again.
    expect(await screen.findByRole('heading', { name: 'ویرایش پروژه' })).toBeInTheDocument();

    const stageInput = await screen.findByPlaceholderText('مرحلهٔ تازه…');
    fireEvent.change(stageInput, { target: { value: 'فصل اول' } });
    fireEvent.click(screen.getByRole('button', { name: 'افزودن مرحله' }));

    await waitFor(async () => {
      expect(await db.projectStages.count()).toBe(1);
    });

    const stage = (await db.projectStages.toArray())[0];
    expect(stage.title).toBe('فصل اول');
    expect(stage.projectId).toBe((await db.projects.toArray())[0].id);

    // Completing the only stage must move the bar to 100.
    fireEvent.click(await screen.findByRole('button', { name: 'فصل اول باز' }));
    await waitFor(async () => {
      const updated = await db.projectStages.get(stage.id);
      expect(updated?.done).toBe(true);
    });
  });
});

describe('home intelligence', () => {
  it('says "not enough data" instead of inventing an insight', async () => {
    await boot();
    const card = screen.getByTestId('insight-showcase');
    expect(card).toBeInTheDocument();
    expect(card.textContent).toContain('هنوز چیزی برای گفتن ندارم');
    expect(card.textContent).toContain('جلسهٔ تمرکز');
  });

  it('shows a measured plan and applies times that can be undone', async () => {
    // Three measured days → the planner is allowed a real capacity.
    await boot();
    const today = getTodayKey();
    const now = new Date();
    for (const offset of [1, 2, 3]) {
      await db.focusSessions.put({
        id: `foc-${offset}`,
        mode: 'pomodoro',
        durationMinutes: 60,
        durationSeconds: 3600,
        dateKey: addDays(today, -offset),
        taskId: null,
        taskTitleSnapshot: null,
        completedNaturally: true,
        startedAt: new Date(now.getTime() - offset * 86400000).toISOString(),
        endedAt: new Date(now.getTime() - offset * 86400000).toISOString(),
      });
    }
    await db.tasks.put({
      id: 'tsk-plan-1',
      title: 'گزارش هفتگی',
      description: '',
      dueDate: today,
      dueTime: null,
      priority: 'high',
      quadrant: 'q1',
      category: 'work',
      tags: [],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 0,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      completedDate: null,
      completedAt: null,
    });

    // The card is a live query, so the plan must react to rows arriving after boot.
    const plan = await screen.findByTestId('plan-showcase');
    await waitFor(() => expect(plan.textContent).toContain('گزارش هفتگی'));
    expect(plan.textContent).toContain('گزارش هفتگی');
    expect(plan.textContent).not.toContain('⚠');

    fireEvent.click(screen.getByRole('button', { name: 'اعمال در برنامهٔ روز' }));

    await waitFor(async () => {
      const task = await db.tasks.get('tsk-plan-1');
      expect(task?.dueTime).not.toBeNull();
    });

    // The change is on the ledger and has an undo entry, because the planner is
    // only allowed to write through the normal path.
    // The ledger and the undo entry are written just after the row itself, so
    // they are awaited rather than read once — a fast machine hid that race.
    await waitFor(async () => {
      const events = await db.eventHistory.toArray();
      expect(
        events.some((event) => event.kind === 'task.updated' && event.entityId === 'tsk-plan-1')
      ).toBe(true);
    });
    await waitFor(async () => {
      const undo = await db.undoHistory.toArray();
      expect(undo.some((entry) => entry.entityId === 'tsk-plan-1')).toBe(true);
    });
  });
});

describe('the manager', () => {
  it('speaks first, with a decision and its evidence', async () => {
    await db.tasks.put({
      id: 'tsk-mgr-1',
      title: 'قرارداد',
      description: '',
      dueDate: '2020-01-01',
      dueTime: null,
      priority: 'high',
      quadrant: 'q1',
      category: 'work',
      tags: [],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 5,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      completedDate: null,
      completedAt: null,
    });

    await boot();
    fireEvent.click(screen.getByRole('button', { name: 'مرکز فرمان' }));

    const brief = await screen.findByTestId('manager-brief');
    expect(brief.textContent).toContain('قرارداد');
    // A deterministic recommendation is not presented as statistical confidence.
    expect(brief.textContent).toMatch(/قواعد محلی/);
    expect(brief.textContent).not.toMatch(/اطمینان/);
    expect(brief.textContent).not.toMatch(/high|medium|low/);
  });
});

describe('brain switch', () => {
  it('appears only when the cloud manager is configured', async () => {
    await boot();
    fireEvent.click(screen.getByRole('button', { name: 'مرکز فرمان' }));
    await screen.findByTestId('manager-brief');
    expect(screen.queryByTestId('brain-switch')).toBeNull();
  });

  it('remains local even when legacy cloud settings exist', async () => {
    await db.settings.put({
      ...(await db.settings.get('default'))!,
      aiEnabled: true,
      aiKey: 'sk-test',
      aiModel: 'test/model',
      aiBaseUrl: 'https://example.test/v1',
    });
    await boot();
    fireEvent.click(screen.getByRole('button', { name: 'مرکز فرمان' }));
    await screen.findByTestId('manager-brief');
    expect(screen.queryByTestId('brain-switch')).toBeNull();
    expect(screen.queryByTestId('cloud-chat')).toBeNull();
    expect((await db.settings.get('default'))?.aiKey).toBe('sk-test');
  });
});

describe('command centre', () => {
  it('answers a real question from real rows', async () => {
    await db.tasks.put({
      id: 'tsk-cmd-1',
      title: 'مالیات',
      description: '',
      dueDate: '2020-01-01',
      dueTime: null,
      priority: 'high',
      quadrant: 'q1',
      category: 'work',
      tags: [],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 3,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      completedDate: null,
      completedAt: null,
    });

    await boot();
    fireEvent.click(screen.getByRole('button', { name: 'مرکز فرمان' }));

    await waitFor(() => expect(screen.getByTestId('manager-brief')).toHaveTextContent('مالیات'));
    const input = await screen.findByLabelText('فرمان');
    fireEvent.change(input, { target: { value: 'کدام کارها عقب افتاده؟' } });
    fireEvent.click(screen.getByRole('button', { name: 'بپرس' }));

    const sheet = screen.getByRole('dialog');
    await waitFor(() => {
      expect(within(sheet).getByText(/کار عقب‌افتاده/)).toBeInTheDocument();
    });
    expect(within(sheet).getAllByText(/مالیات/).length).toBeGreaterThan(0);
  });

  it('admits when it does not understand, and offers what it does', async () => {
    await boot();
    fireEvent.click(screen.getByRole('button', { name: 'مرکز فرمان' }));

    const input = await screen.findByLabelText('فرمان');
    fireEvent.change(input, { target: { value: 'قیمت دلار چند است' } });
    fireEvent.click(screen.getByRole('button', { name: 'بپرس' }));

    const sheet = screen.getByRole('dialog');
    await waitFor(() => {
      expect(within(sheet).getByText('این فرمان را نمی‌شناسم')).toBeInTheDocument();
    });
    expect(within(sheet).getByText(/این‌ها را می‌فهمم/)).toBeInTheDocument();
  });
});
