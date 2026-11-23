import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LuxuryBarChart } from '@/components/charts/LuxuryBarChart';
import { LuxuryAreaChart } from '@/components/charts/LuxuryAreaChart';
import { WeeklyTrendChart } from '@/components/charts/WeeklyTrendChart';

const days = [
  { key: 'a', label: 'شنبه', value: 40, sublabel: 'شنبه • ۴۰ دقیقه' },
  { key: 'b', label: 'یکشنبه', value: 65, highlighted: true },
  { key: 'c', label: 'دوشنبه', value: 0 },
  { key: 'd', label: 'سه‌شنبه', value: 90 },
];

describe('charts', () => {
  it('renders the bar chart with an axis and every label', () => {
    render(<LuxuryBarChart data={days} unitSuffix="m" />);
    expect(screen.getByRole('img', { name: 'نمودار ستونی' })).toBeInTheDocument();
    days.forEach((day) => expect(screen.getByText(day.label)).toBeInTheDocument());
  });

  it('renders the area chart with axis ticks and the peak readout', () => {
    render(
      <LuxuryAreaChart
        points={[
          { key: '1', label: 'روز ۱', value: 30 },
          { key: '2', label: 'روز ۲', value: 74 },
          { key: '3', label: 'روز ۳', value: 55 },
        ]}
        maxValue={100}
        unitSuffix="pts"
      />
    );
    expect(screen.getByRole('img', { name: 'نمودار روند' })).toBeInTheDocument();
    expect(screen.getByText('اوج: روز ۲')).toBeInTheDocument();
    expect(screen.getByText('روز ۱')).toBeInTheDocument();
  });

  it('survives an empty series', () => {
    const { container } = render(<LuxuryAreaChart points={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('weekly trend', () => {
  const week = [
    { key: '1', label: 'ش', dateLabel: '۲۳ آبان', value: 40, previous: 30 },
    { key: '2', label: 'ی', dateLabel: '۲۴ آبان', value: 0, previous: 20 },
    { key: '3', label: 'د', dateLabel: '۲۵ آبان', value: 75, previous: 75 },
    { key: '4', label: 'س', dateLabel: '۲۶ آبان', value: 55, previous: 0, isToday: true },
  ];

  it('summarises the week and labels itself for a screen reader', () => {
    render(<WeeklyTrendChart data={week} target={60} unitSuffix=" دقیقه" valueLabel="تمرکز" />);
    const chart = screen.getByRole('img');
    expect(chart.getAttribute('aria-label')).toContain('تمرکز');
    expect(chart.getAttribute('aria-label')).toMatch(/[۰-۹]/);
    expect(screen.getByTestId('weekly-trend')).toBeInTheDocument();
  });

  it('draws no NaN geometry, even for a week of zeros', () => {
    const { container } = render(
      <WeeklyTrendChart
        data={week.map((day) => ({ ...day, value: 0, previous: 0 }))}
        target={60}
      />
    );
    expect(container.innerHTML).not.toContain('NaN');
    expect(container.innerHTML).not.toContain('Infinity');
    expect(screen.getByText('این هفته چیزی ثبت نشده')).toBeInTheDocument();
  });

  it('keeps the numbers reachable without the picture', () => {
    render(<WeeklyTrendChart data={week} />);
    // A hidden table carries the same data for assistive tech.
    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getAllByRole('row').length).toBe(week.length + 1);
  });

  it('quotes the honest comparison instead of a bare number in the header', () => {
    render(<WeeklyTrendChart data={week} />);
    expect(screen.getByText(/بهترین روز/)).toBeInTheDocument();
  });
});
