import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { YearOverview } from '@/features/calendar/YearOverview';
import {
  dateKeyToJalaali,
  getJalaaliMonthDaysCount,
  getTodayKey,
  jalaaliToDateKey,
} from '@/lib/date';

const todayKey = getTodayKey();
const year = dateKeyToJalaali(todayKey).jy;

function renderYear(overrides: Partial<Parameters<typeof YearOverview>[0]> = {}) {
  const props = {
    year,
    todayKey,
    selectedDateKey: todayKey,
    tasksByDate: new Map(),
    habitCountByDate: new Map(),
    focusMinutesByDate: new Map(),
    onShiftYear: vi.fn(),
    onPickDate: vi.fn(),
    onPickMonth: vi.fn(),
    ...overrides,
  };
  render(<YearOverview {...props} />);
  return props;
}

const daysInYear = Array.from({ length: 12 }, (_, i) => getJalaaliMonthDaysCount(year, i + 1)).reduce(
  (a, b) => a + b,
  0
);

describe('Year overview', () => {
  it('draws all twelve Jalali months as full grids', () => {
    renderYear();
    expect(screen.getAllByRole('region')).toHaveLength(12);
    expect(screen.getAllByRole('button', { name: /^\d+ \S+ \d{4}/ })).toHaveLength(daysInYear);
  });

  it('marks today exactly once and opens the year on the real current month', () => {
    renderYear();
    const today = document.querySelectorAll('[aria-current="date"]');
    expect(today).toHaveLength(1);
    expect(today[0].getAttribute('aria-label')).toContain('امروز');
  });

  it('reports the year, leap state and real totals', () => {
    renderYear();
    expect(screen.getByText('YEAR')).toBeInTheDocument();
    expect(screen.getByText(String(year))).toBeInTheDocument();
    expect(screen.getAllByText(/سال (کبیسه|عادی)/)).toHaveLength(1);
  });

  it('counts only the data that exists', () => {
    const tasksByDate = new Map([
      [jalaaliToDateKey(year, 1, 1), [{ id: 't1', status: 'todo' }]],
    ]);
    const focusMinutesByDate = new Map([[jalaaliToDateKey(year, 1, 2), 45]]);
    const habitCountByDate = new Map([[jalaaliToDateKey(year, 1, 3), 2]]);

    renderYear({ tasksByDate, habitCountByDate, focusMinutesByDate } as never);

    // 45 focused minutes appear twice: once in the year total, once in Farvardin's footer.
    expect(screen.getAllByText('45m')).toHaveLength(2);
  });

  it('picks a day with its exact Jalali key and jumps to a month', () => {
    const props = renderYear({ selectedDateKey: '' });

    fireEvent.click(screen.getByRole('button', { name: `1 فروردین ${year}` }));
    expect(props.onPickDate).toHaveBeenCalledWith(jalaaliToDateKey(year, 1, 1));

    fireEvent.click(screen.getAllByRole('button', { name: 'ماه کامل' })[0]);
    expect(props.onPickMonth).toHaveBeenCalledWith(1);
  });

  it('steps the year in both directions', () => {
    const props = renderYear();
    fireEvent.click(screen.getByRole('button', { name: 'سال بعد' }));
    fireEvent.click(screen.getByRole('button', { name: 'سال قبل' }));
    expect(props.onShiftYear).toHaveBeenNthCalledWith(1, 1);
    expect(props.onShiftYear).toHaveBeenNthCalledWith(2, -1);
  });
});
