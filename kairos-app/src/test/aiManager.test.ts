import { beforeEach, describe, expect, it, vi } from 'vitest';
import { db, exportBackup } from '@/lib/db';
import { executeAiTool } from '@/lib/ai/tools';
import { isAiReady, runAiTurn, type FetchLike } from '@/lib/ai/client';
import { DEFAULT_SETTINGS } from '@/lib/db';
import { getTodayKey } from '@/lib/date';

/**
 * The authority model of the cloud manager, pinned by tests.
 *
 * These are the promises that make an AI writing into a personal database
 * acceptable: it can only use the tools listed, every write lands in the same
 * ledger as a thumb, refusals are honoured, deletion is impossible, and every
 * failure is a Persian sentence.
 */

const settings = { ...DEFAULT_SETTINGS };

beforeEach(async () => {
  await db.transaction(
    'rw',
    [db.tasks, db.projects, db.projectStages, db.habits, db.habitLogs, db.eventHistory, db.undoHistory, db.settings],
    async () => {
      await Promise.all([
        db.tasks.clear(),
        db.projects.clear(),
        db.projectStages.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.eventHistory.clear(),
        db.undoHistory.clear(),
      ]);
      await db.settings.put({ ...DEFAULT_SETTINGS, aiKey: 'sk-test', aiModel: 'test/model' });
    }
  );
});

describe('ai readiness', () => {
  it('is off until a key, a model and an address all exist', () => {
    expect(isAiReady({ ...settings, aiEnabled: false })).toBe(false);
    expect(isAiReady({ ...settings, aiEnabled: true, aiKey: '', aiModel: 'x' })).toBe(false);
    expect(isAiReady({ ...settings, aiEnabled: true, aiKey: 'k', aiModel: 'm', aiBaseUrl: '' })).toBe(false);
    expect(isAiReady({ ...settings, aiEnabled: true, aiKey: 'k', aiModel: 'm' })).toBe(true);
  });
});

describe('tools write through the normal path', () => {
  it('creates a task and leaves an event in the ledger', async () => {
    const result = await executeAiTool('create_task', {
      title: 'تماس با وکیل',
      dueDate: getTodayKey(),
      priority: 'high',
      estimateMin: 30,
    });
    expect(result.ok).toBe(true);

    const tasks = await db.tasks.toArray();
    expect(tasks).toHaveLength(1);
    expect(tasks[0].title).toBe('تماس با وکیل');
    expect(tasks[0].estimateMin).toBe(30);

    const events = await db.eventHistory.toArray();
    expect(events.some((event) => event.kind === 'task.created')).toBe(true);
  });

  it('completes a task found by part of its title', async () => {
    await executeAiTool('create_task', { title: 'گزارش مالی مهر' });
    const result = await executeAiTool('complete_task', { titleQuery: 'گزارش مالی' });
    expect(result.ok).toBe(true);
    expect((await db.tasks.toArray())[0].status).toBe('completed');
  });

  it('refuses to touch a task it cannot find instead of guessing', async () => {
    const result = await executeAiTool('complete_task', { titleQuery: 'چیزی که نیست' });
    expect(result.ok).toBe(false);
    expect(await db.tasks.count()).toBe(0);
  });

  it('builds the goal → project → stage chain', async () => {
    const project = await executeAiTool('create_project', { title: 'کتاب' });
    expect(project.ok).toBe(true);
    const projectId = (project.data as { id: string }).id;

    const stage = await executeAiTool('add_stage', { projectId, title: 'فصل اول' });
    expect(stage.ok).toBe(true);
    const stageId = (stage.data as { id: string }).id;

    const toggled = await executeAiTool('toggle_stage', { stageId });
    expect(toggled.ok).toBe(true);
    expect((await db.projectStages.get(stageId))?.done).toBe(true);

    // A stage tick is undoable, exactly like a manual one.
    const undo = await db.undoHistory.toArray();
    expect(undo.some((entry) => entry.kind === 'stage.toggle')).toBe(true);
  });

  it('logs a habit by name and reports honestly when the name is unknown', async () => {
    await db.habits.put({
      id: 'h1',
      name: 'ورزش صبحگاهی',
      description: '',
      icon: 'dumbbell',
      accentColor: '#a78bfa',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'binary',
      targetValue: 1,
      unit: '',
      archived: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      order: 0,
    } as never);

    expect((await executeAiTool('log_habit', { habitQuery: 'ورزش' })).ok).toBe(true);
    expect(await db.habitLogs.count()).toBe(1);
    expect((await executeAiTool('log_habit', { habitQuery: 'شنا' })).ok).toBe(false);
  });

  it('changes only allow-listed settings and clamps their values', async () => {
    const ok = await executeAiTool('update_settings', { key: 'dailyFocusGoalMinutes', value: '90' });
    expect(ok.ok).toBe(true);
    expect((await db.settings.get('default'))?.dailyFocusGoalMinutes).toBe(90);

    const clamped = await executeAiTool('update_settings', {
      key: 'dailyFocusGoalMinutes',
      value: '99999',
    });
    expect(clamped.ok).toBe(true);
    expect((await db.settings.get('default'))?.dailyFocusGoalMinutes).toBe(720);

    const forbidden = await executeAiTool('update_settings', { key: 'aiKey', value: 'leak' });
    expect(forbidden.ok).toBe(false);
    expect((await db.settings.get('default'))?.aiKey).toBe('sk-test');
  });

  it('has no destructive tool at all', async () => {
    for (const name of ['delete_task', 'clear_all', 'delete_project', 'wipe']) {
      const result = await executeAiTool(name, { id: 'x' });
      expect(result.ok).toBe(false);
    }
    expect(await db.tasks.count()).toBe(0);
  });
});

describe('the key never leaves', () => {
  it('is stripped from a backup file', async () => {
    const backup = await exportBackup();
    expect(backup.settings.aiKey).toBe('');
    // Everything else about the settings is still in the file.
    expect(backup.settings.dailyFocusGoalMinutes).toBe(DEFAULT_SETTINGS.dailyFocusGoalMinutes);
  });
});

describe('the cloud turn', () => {
  const configured = { ...DEFAULT_SETTINGS, aiEnabled: true, aiKey: 'sk-test', aiModel: 'test/model' };

  function fakeFetch(responses: unknown[]): { fetchImpl: FetchLike; calls: unknown[] } {
    const calls: unknown[] = [];
    let index = 0;
    const fetchImpl: FetchLike = async (_url, init) => {
      calls.push(JSON.parse(init?.body ?? '{}'));
      const body = responses[Math.min(index, responses.length - 1)];
      index += 1;
      return {
        ok: true,
        status: 200,
        json: async () => body,
      };
    };
    return { fetchImpl, calls };
  }

  it('runs a tool call, feeds the result back, and answers in words', async () => {
    const { fetchImpl, calls } = fakeFetch([
      {
        choices: [
          {
            message: {
              content: null,
              tool_calls: [
                {
                  id: 'call1',
                  type: 'function',
                  function: { name: 'create_task', arguments: '{"title":"تمرین ریتم"}' },
                },
              ],
            },
          },
        ],
      },
      { choices: [{ message: { content: 'کار «تمرین ریتم» ساخته شد.' } }] },
    ]);

    const result = await runAiTurn({
      settings: configured,
      history: [],
      userText: 'یک کار برای تمرین ریتم بساز',
      fetchImpl,
    });

    expect(result.ok).toBe(true);
    expect(result.reply).toContain('تمرین ریتم');
    expect(result.steps).toHaveLength(1);
    expect(result.steps[0].ok).toBe(true);
    expect(await db.tasks.count()).toBe(1);
    // The second request must carry the tool result, or the model is blind.
    expect(JSON.stringify(calls[1])).toContain('تمرین ریتم');
  });

  it('honours a refusal and tells the model, so it does not pretend it worked', async () => {
    const { fetchImpl } = fakeFetch([
      {
        choices: [
          {
            message: {
              content: null,
              tool_calls: [
                {
                  id: 'call1',
                  type: 'function',
                  function: { name: 'create_task', arguments: '{"title":"ناخواسته"}' },
                },
              ],
            },
          },
        ],
      },
      { choices: [{ message: { content: 'پس انجام ندادم.' } }] },
    ]);

    const result = await runAiTurn({
      settings: configured,
      history: [],
      userText: 'یک کار بساز',
      fetchImpl,
      confirmWrite: async () => false,
    });

    expect(result.steps[0].ok).toBe(false);
    expect(await db.tasks.count()).toBe(0);
  });

  it('turns an HTTP refusal into a Persian sentence, not a crash', async () => {
    const fetchImpl: FetchLike = async () => ({
      ok: false,
      status: 401,
      json: async () => ({ error: { message: 'invalid api key' } }),
    });
    const result = await runAiTurn({
      settings: configured,
      history: [],
      userText: 'سلام',
      fetchImpl,
    });
    expect(result.ok).toBe(false);
    expect(result.error).toContain('کلید');
    expect(result.error).not.toContain('invalid api key');
  });

  it('does nothing at all when the feature is off', async () => {
    const fetchImpl = vi.fn();
    const result = await runAiTurn({
      settings: { ...configured, aiEnabled: false },
      history: [],
      userText: 'سلام',
      fetchImpl: fetchImpl as unknown as FetchLike,
    });
    expect(result.ok).toBe(false);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});

describe('the wider tool set', () => {
  it('creates a batch of tasks in one call', async () => {
    const result = await executeAiTool('create_tasks', { titles: ['الف', 'ب', 'ج'] });
    expect(result.ok).toBe(true);
    expect(await db.tasks.count()).toBe(3);
  });

  it('moves overdue work to today and leaves it undoable', async () => {
    await executeAiTool('create_task', { title: 'کهنه', dueDate: '2020-01-01' });
    const result = await executeAiTool('reschedule_overdue', {});
    expect(result.ok).toBe(true);
    const task = (await db.tasks.toArray())[0];
    expect(task.dueDate).toBe(getTodayKey());
    const undo = await db.undoHistory.toArray();
    expect(undo.some((entry) => entry.kind === 'task.update')).toBe(true);
  });

  it('postpones one task by a number of days', async () => {
    await executeAiTool('create_task', { title: 'قرارداد', dueDate: getTodayKey() });
    const result = await executeAiTool('postpone_task', { titleQuery: 'قرارداد', days: 3 });
    expect(result.ok).toBe(true);
    const expected = new Date(`${getTodayKey()}T00:00:00Z`);
    expected.setUTCDate(expected.getUTCDate() + 3);
    expect((await db.tasks.toArray())[0].dueDate).toBe(expected.toISOString().slice(0, 10));
  });

  it('searches across every collection and says when nothing matches', async () => {
    await executeAiTool('create_project', { title: 'بازطراحی سایت' });
    const hit = await executeAiTool('search', { query: 'بازطراحی' });
    expect((hit.data as unknown[]).length).toBe(1);
    const miss = await executeAiTool('search', { query: 'هیچ‌چیز' });
    expect(miss.summary).toContain('پیدا نشد');
  });

  it('plans the day with measured capacity rather than a fixed guess', async () => {
    await executeAiTool('create_task', { title: 'کار امروز', dueDate: getTodayKey() });
    const result = await executeAiTool('plan_day', {});
    expect(result.ok).toBe(true);
    expect((result.data as { capacityUnknown: boolean }).capacityUnknown).toBe(true);
  });

  it('undoes the last restorable change and refuses when there is nothing to undo', async () => {
    await executeAiTool('create_task', { title: 'قابل برگشت', dueDate: getTodayKey() });
    await executeAiTool('update_task', { titleQuery: 'قابل برگشت', priority: 'high' });
    const undone = await executeAiTool('undo_last', {});
    expect(undone.ok).toBe(true);
    expect((await db.tasks.toArray())[0].priority).toBe('medium');

    // Drain whatever is left, then the honest refusal.
    for (let index = 0; index < 5; index += 1) await executeAiTool('undo_last', {});
    const empty = await executeAiTool('undo_last', {});
    expect(empty.ok).toBe(false);
  });
});
