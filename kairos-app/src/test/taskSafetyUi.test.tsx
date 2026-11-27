import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { AppProvider, useApp } from '@/context/AppContext';
import { TaskModal } from '@/features/tasks/TaskModal';
import { createTask, db, updateTask } from '@/lib/db';
import type { Task } from '@/types/domain';

function Harness({ task }: { task: Task }) {
  const { ready, openEditTask, toasts } = useApp();
  return <><button disabled={!ready} onClick={() => openEditTask(task)}>open editor</button>{toasts.map((toast) => <p key={toast.id}>{toast.title}</p>)}<TaskModal /></>;
}

async function setup() {
  const task = await createTask({ title: 'کار خصوصی', description: 'متن اولیه', dueDate: null, dueTime: null, priority: 'medium', quadrant: 'q2', category: 'work', tags: [], status: 'todo', subtasks: [], recurrence: 'none', reminderEnabled: false, reminderOffsetMin: 0 });
  render(<AppProvider><Harness task={task} /></AppProvider>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'open editor' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'open editor' }));
  const dialog = await screen.findByRole('dialog', { name: 'ویرایش وظیفه' });
  await within(dialog).findByDisplayValue('کار خصوصی');
  return { task, dialog };
}

beforeEach(async () => {
  localStorage.clear();
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
});

describe('task editor safety', () => {
  it('does not overwrite a newer edit and retains the unsaved form', async () => {
    const { task, dialog } = await setup();
    fireEvent.change(within(dialog).getByDisplayValue('کار خصوصی'), { target: { value: 'پیش‌نویس من' } });
    await updateTask(task.id, { description: 'ویرایش هم‌زمان' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'ذخیره تغییرات' }));
    await screen.findByText('وظیفه در این فاصله تغییر کرده است');
    expect(within(dialog).getByDisplayValue('پیش‌نویس من')).toBeInTheDocument();
    expect(await db.tasks.get(task.id)).toMatchObject({ title: 'کار خصوصی', description: 'ویرایش هم‌زمان' });
  });

  it('requires a second explicit confirmation and allows cancelling deletion', async () => {
    const { task, dialog } = await setup();
    fireEvent.click(within(dialog).getByRole('button', { name: 'حذف وظیفه' }));
    const confirmation = within(dialog).getByRole('region', { name: 'تأیید حذف وظیفه' });
    expect(await db.tasks.get(task.id)).toBeDefined();
    fireEvent.click(within(confirmation).getByRole('button', { name: 'انصراف از حذف' }));
    expect(await db.tasks.get(task.id)).toBeDefined();
    fireEvent.click(within(dialog).getByRole('button', { name: 'حذف وظیفه' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'تأیید حذف' }));
    await waitFor(async () => expect(await db.tasks.get(task.id)).toBeUndefined());
  });
});
