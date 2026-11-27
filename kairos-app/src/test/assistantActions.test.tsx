import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from '@/App';
import { createProject, createTask, db, ensureSettings, updateSettings } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import { applyConfirmedChange, interpretAssistantRequest, previewChange, revertAssistantChange } from '@/lib/assistantActions';
import { completeFirstRun } from '@/test/helpers/firstRun';

async function task(title = 'گزارش') {
  return createTask({ title, description: 'دادهٔ واقعی', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
}
beforeEach(async () => {
  window.localStorage.clear();
  await Promise.all([db.tasks.clear(), db.projects.clear(), db.settings.clear(), db.undoHistory.clear(), db.eventHistory.clear(), db.assistantHistory.clear()]);
});

describe('local assistant actions', () => {
  it('supports navigation without interpreting a question as navigation', () => {
    expect(interpretAssistantRequest('برو به تنظیمات', [], [])).toMatchObject({ type: 'navigation', tab: 'settings' });
    expect(interpretAssistantRequest('وظایف امروز را نشان بده', [], [])).toEqual({ type: 'none' });
    expect(interpretAssistantRequest('برو به عادت و وظایف', [], [])).toMatchObject({ type: 'blocked' });
    expect(interpretAssistantRequest('برو به وظایف و وظیفه گزارش را تکمیل کن', [], [])).toMatchObject({ type: 'blocked' });
    expect(interpretAssistantRequest('پروژه کتاب را متوقف کن و فعال کن', [], [])).toMatchObject({ type: 'blocked' });
  });
  it('blocks destructive and negative commands, and does not guess when names collide', async () => {
    const one = await task(); const two = await task();
    expect(interpretAssistantRequest('وظیفه گزارش را حذف کن', [one, two], [])).toMatchObject({ type: 'blocked' });
    expect(interpretAssistantRequest('وظیفه گزارش را تکمیل نکن', [one], [])).toMatchObject({ type: 'blocked' });
    const request = interpretAssistantRequest('وظیفه گزارش را تکمیل کن', [one, two], []);
    expect(request.type).toBe('change');
    if (request.type === 'change') expect(request.candidates).toHaveLength(2);
  });
  it('commits only on confirmation and safely undoes without deleting a row', async () => {
    const before = await task();
    const preview = previewChange('task.complete', before);
    await expect(applyConfirmedChange(preview, false)).rejects.toThrow();
    expect((await db.tasks.get(before.id))?.status).toBe('todo');
    const id = await applyConfirmedChange(preview, true);
    expect(await db.tasks.get(before.id)).toMatchObject({ status: 'completed', completedDate: getTodayKey() });
    expect(await db.undoHistory.get(id)).toMatchObject({ entityId: before.id, before });
    await revertAssistantChange(id);
    expect(await db.tasks.get(before.id)).toEqual(before);
    await expect(revertAssistantChange(id)).rejects.toThrow();
  });
  it('allows only one of two simultaneous confirmations of the same preview', async () => {
    const before = await task();
    const preview = previewChange('task.high', before);
    const results = await Promise.allSettled([applyConfirmedChange(preview, true), applyConfirmedChange(preview, true)]);
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    expect(await db.undoHistory.count()).toBe(1);
    expect((await db.tasks.get(before.id))?.priority).toBe('high');
  });

  it('rejects stale previews and rejects undo after a later edit', async () => {
    const before = await task();
    const stale = previewChange('task.high', before);
    await db.tasks.update(before.id, { description: 'ویرایش دیگر' });
    await expect(applyConfirmedChange(stale, true)).rejects.toThrow(/تغییر کرده/);
    const current = (await db.tasks.get(before.id))!;
    const id = await applyConfirmedChange(previewChange('task.high', current), true);
    await db.tasks.update(before.id, { description: 'تغییر بعدی' });
    await expect(revertAssistantChange(id)).rejects.toThrow(/امن نیست/);
    expect((await db.tasks.get(before.id))?.description).toBe('تغییر بعدی');
  });
  it('never accepts an injected after snapshot and does not change hidden samples', async () => {
    await ensureSettings();
    const before = await task();
    const preview = previewChange('task.high', before);
    preview.after = { ...preview.after, title: 'دستکاری', description: 'دستکاری' };
    await applyConfirmedChange(preview, true);
    expect(await db.tasks.get(before.id)).toMatchObject({ title: 'گزارش', description: 'دادهٔ واقعی', priority: 'high' });
    await db.tasks.update(before.id, { sample: true, priority: 'medium' });
    const sample = (await db.tasks.get(before.id))!;
    const hidden = previewChange('task.high', sample);
    await updateSettings({ sampleVisible: false });
    await expect(applyConfirmedChange(hidden, true)).rejects.toThrow(/پنهان/);
    expect((await db.tasks.get(before.id))?.priority).toBe('medium');
  });
  it('preserves recurring tasks and supports project changes with undo', async () => {
    const recurring = await task();
    expect(() => previewChange('task.complete', { ...recurring, recurrence: 'daily' })).toThrow(/تکرار/);
    expect(() => previewChange('task.tomorrow', { ...recurring, status: 'completed' })).toThrow(/تکمیل/);
    const project = await createProject({ title: 'کتاب', description: '', goalId: null, category: 'work', startDate: null, dueDate: null });
    const change = previewChange('project.pause', project);
    const id = await applyConfirmedChange(change, true);
    expect((await db.projects.get(project.id))?.status).toBe('paused');
    await revertAssistantChange(id);
    expect(await db.projects.get(project.id)).toEqual(project);
  });
  it('uses the real interface to preview, confirm and revert a task', async () => {
    const before = await task();
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await completeFirstRun();
    await screen.findByTestId('home-ready');
    fireEvent.click(within(screen.getByRole('navigation', { name: 'ناوبری اصلی' })).getByRole('button', { name: 'دستیار' }));
    const dialog = await screen.findByRole('region', { name: 'مرکز فرمان' });
    await waitFor(() => expect(within(dialog).getByTestId('manager-brief')).toHaveTextContent('گزارش'));
    fireEvent.change(within(dialog).getByLabelText('فرمان'), { target: { value: 'وظیفه گزارش را تکمیل کن' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'بپرس' }));
    const area = within(dialog).getByRole('region', { name: 'تغییر پیشنهادی دستیار' });
    fireEvent.click(within(area).getByRole('button', { name: 'گزارش' }));
    expect(within(area).getByRole('region', { name: 'پیش‌نمایش تغییر' })).toHaveTextContent('تکمیل‌شده');
    expect((await db.tasks.get(before.id))?.status).toBe('todo');
    fireEvent.click(within(area).getByRole('button', { name: 'تأیید تغییر' }));
    await waitFor(async () => expect((await db.tasks.get(before.id))?.status).toBe('completed'));
    fireEvent.click(await within(dialog).findByRole('button', { name: 'بازگرداندن آخرین تغییر دستیار' }));
    await waitFor(async () => expect((await db.tasks.get(before.id))?.status).toBe('todo'));
  });
});
