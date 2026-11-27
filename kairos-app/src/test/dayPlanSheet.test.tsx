import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createTask, db } from '@/lib/db';
import { buildDayPlan } from '@/lib/planner';
import { DayPlanSheet } from '@/features/dashboard/DayPlanSheet';

beforeEach(async () => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date(2026, 10, 20, 9, 0));
  await db.transaction('rw', db.tables, async () => { await Promise.all(db.tables.map((table) => table.clear())); });
});
afterEach(() => vi.useRealTimers());

describe('daily plan sheet', () => {
  it('previews without modifying a task and writes only after confirmation', async () => {
    const task = await createTask({ title: 'گزارش', description: '', dueDate: '2026-11-20', dueTime: null,
      priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none',
      reminderEnabled: false, reminderOffsetMin: 0, estimateMin: 30 });
    const plan = buildDayPlan({ tasks: [task], sessions: [], todayKey: '2026-11-20', goalMinutes: 60 });
    const applied = vi.fn();
    render(<DayPlanSheet open onClose={() => {}} plan={plan} onApplied={applied} />);
    fireEvent.change(screen.getByLabelText('شروع از'), { target: { value: '10:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'پیش‌نمایش زمان‌ها' }));
    const preview = await screen.findByRole('region', { name: 'پیش‌نمایش برنامه' });
    expect(within(preview).getByText('گزارش')).toBeVisible();
    expect((await db.tasks.get(task.id))?.dueTime).toBeNull();
    fireEvent.click(within(preview).getByRole('button', { name: 'تأیید و ثبت زمان‌ها' }));
    await waitFor(() => expect(applied).toHaveBeenCalledWith(1));
    expect((await db.tasks.get(task.id))?.dueTime).toBe('10:00');
  });
});
