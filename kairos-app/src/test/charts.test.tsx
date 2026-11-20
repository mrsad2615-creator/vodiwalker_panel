import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LuxuryBarChart } from '@/components/charts/LuxuryBarChart';
import { LuxuryAreaChart } from '@/components/charts/LuxuryAreaChart';

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
