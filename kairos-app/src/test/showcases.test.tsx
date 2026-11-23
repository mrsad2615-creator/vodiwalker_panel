import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import {
  AnalyticsShowcase,
  FocusShowcase,
  HabitsShowcase,
  TasksShowcase,
} from '@/features/dashboard/Showcases';
import { buildDailySeries, habitWeekMatrix, taskBreakdown } from '@/lib/homeMetrics';
import type { Habit } from '@/types/domain';

const TODAY = '2025-06-10';
const series = buildDailySeries({ focusSessions: [], tasks: [], habitLogs: [] }, TODAY, 14);

describe('Home showcases', () => {
  it('focus ring is driven by the real minutes', () => {
    render(
      <FocusShowcase minutes={45} goalMinutes={120} series={series} onStart={vi.fn()} />
    );
    expect(screen.getByText('45m')).toBeInTheDocument();
    // Numeric renders the digits and the suffix as separate nodes.
    expect(screen.getByText('38')).toBeInTheDocument();
    expect(screen.getByText('تمرکز امروز')).toBeInTheDocument();
  });

  it('tasks bar counts every status and toggles a row', () => {
    const onToggle = vi.fn();
    render(
      <TasksShowcase
        breakdown={taskBreakdown([])}
        nextTasks={[{ id: 't1', title: 'کار آزمایشی', dueTime: '10:00' }]}
        onToggle={onToggle}
        onOpenTask={vi.fn()}
        onOpenAll={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole('button', { name: 'تکمیل کار آزمایشی' }));
    expect(onToggle).toHaveBeenCalledWith('t1');
  });

  it('habit matrix draws one cell per habit per day', () => {
    const habits = [
      { id: 'h1', name: 'کتاب', icon: 'book', accentColor: '#fff', archived: false },
      { id: 'h2', name: 'ورزش', icon: 'dumbbell', accentColor: '#fff', archived: false },
    ] as unknown as Habit[];
    const matrix = habitWeekMatrix(habits, [], TODAY, 7);

    render(<HabitsShowcase matrix={matrix} onOpenAll={vi.fn()} onStart={vi.fn()} />);
    expect(screen.getAllByTestId('habit-cell')).toHaveLength(14);
  });

  it('habit rail is a control: one tap on a card logs that habit', () => {
    const habits = [
      { id: 'h1', name: 'کتاب', icon: 'book', accentColor: '#fff', archived: false },
      { id: 'h2', name: 'ورزش', icon: 'dumbbell', accentColor: '#fff', archived: false },
    ] as unknown as Habit[];
    const matrix = habitWeekMatrix(habits, [], TODAY, 7);
    const onToggle = vi.fn();

    render(
      <HabitsShowcase matrix={matrix} onToggle={onToggle} onOpenAll={vi.fn()} onStart={vi.fn()} />
    );

    const card = screen.getByRole('button', { name: /کتاب — ثبت نشده/ });
    expect(card).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(card);
    expect(onToggle).toHaveBeenCalledWith('h1');
  });

  it('habit rail shows today as done when the log exists', () => {
    const habits = [
      { id: 'h1', name: 'کتاب', icon: 'book', accentColor: '#fff', archived: false },
    ] as unknown as Habit[];
    const matrix = habitWeekMatrix(
      habits,
      [{ id: 'l1', habitId: 'h1', dateKey: TODAY, status: 'completed', value: 1 } as never],
      TODAY,
      7
    );

    render(
      <HabitsShowcase matrix={matrix} onToggle={vi.fn()} onOpenAll={vi.fn()} onStart={vi.fn()} />
    );
    expect(screen.getByRole('button', { name: /کتاب — انجام شد/ })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });

  it('analytics sparkline plots one point per day of the window', () => {
    const { container } = render(
      <AnalyticsShowcase
        series={series}
        comparison={{ focusMinutes: 0, tasksDone: 0, habitsDone: 0, deltaPercent: null }}
        onOpen={vi.fn()}
      />
    );
    // A smooth path (not a raw polyline) that visits one point per day: the
    // curve has 13 segments for 14 days, and the last point is marked.
    const line = container.querySelector('path[stroke="var(--accent-fg)"]');
    expect(line).not.toBeNull();
    const commands = line!.getAttribute('d') ?? '';
    expect((commands.match(/ C /g) ?? []).length).toBe(13);
    expect(container.querySelectorAll('circle').length).toBe(2);
    expect(screen.getByRole('img', { name: /روند فعالیت/ })).toBeInTheDocument();
  });

  it('renders an empty habit board as an invitation, not a blank box', () => {
    const matrix = habitWeekMatrix([], [], TODAY, 7);
    render(<HabitsShowcase matrix={matrix} onOpenAll={vi.fn()} onStart={vi.fn()} />);
    expect(screen.getByText(/اولین عادت/)).toBeInTheDocument();
  });
});
