import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { createTask, db, ensureSettings, updateTask } from '@/lib/db';
import { RecentTimeChanges } from '@/features/dashboard/RecentTimeChanges';

beforeEach(async () => {
  await db.transaction('rw', db.tables, async () => { await Promise.all(db.tables.map((table) => table.clear())); });
  await ensureSettings();
});
async function makeTask(title: string, sample = false) {
  return createTask({ title, sample, description: '', dueDate: '2026-11-20', dueTime: null,
    priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none',
    reminderEnabled: false, reminderOffsetMin: 0 });
}

describe('recent time changes', () => {
  it('shows persisted changes and restores the previous value', async () => {
    const task = await makeTask('گزارش');
    await updateTask(task.id, { dueTime: '10:00' });
    render(<RecentTimeChanges />);
    fireEvent.click(await screen.findByRole('button', { name: 'بازگردانی زمان گزارش' }));
    await screen.findByText('زمان قبلی بازگردانده شد.');
    expect((await db.tasks.get(task.id))?.dueTime).toBeNull();
  });
  it('refuses to overwrite a later edit and hides sample history', async () => {
    const task = await makeTask('گزارش');
    const sample = await makeTask('عنوان نمونه پنهان', true);
    await updateTask(task.id, { dueTime: '10:00' });
    await updateTask(sample.id, { dueTime: '11:00' });
    await db.settings.update('default', { sampleVisible: false });
    await updateTask(task.id, { title: 'عنوان تازه' });
    render(<RecentTimeChanges />);
    const button = await screen.findByRole('button', { name: 'بازگردانی زمان گزارش' });
    expect(screen.queryByText(/عنوان نمونه پنهان/)).not.toBeInTheDocument();
    fireEvent.click(button);
    await screen.findByText(/بازگردانی انجام نشد/);
    await waitFor(async () => expect((await db.tasks.get(task.id))?.title).toBe('عنوان تازه'));
    expect((await db.tasks.get(task.id))?.dueTime).toBe('10:00');
  });
});
