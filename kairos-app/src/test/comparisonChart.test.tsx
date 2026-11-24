import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { ComparisonChart } from '@/components/charts/ComparisonChart';

describe('comparison charts', () => {
  it('shows exact values, accessible labels and bars scaled from zero', () => {
    const { container } = render(<ComparisonChart title="وضعیت" subtitle="فقط داده‌های موجود" unit="کار" data={[{ label: 'انجام‌شده', value: 2 }, { label: 'در جریان', value: 1 }, { label: 'مانده', value: 0 }]} />);
    const figure = screen.getByRole('figure', { name: 'وضعیت' });
    expect(within(figure).getByRole('listitem', { name: 'انجام‌شده: 2 کار' })).toBeInTheDocument();
    const bars = container.querySelectorAll('[style*="width"]');
    expect(bars[0]).toHaveStyle({ width: '100%' });
    expect(bars[1]).toHaveStyle({ width: '50%' });
    expect(bars[2]).toHaveStyle({ width: '0%' });
  });
  it('does not invent bars when there is no data', () => {
    const { container } = render(<ComparisonChart title="وضعیت" subtitle="روز انتخاب‌شده" data={[{ label: 'انجام‌شده', value: 0 }]} />);
    expect(screen.getByText('برای این بازه هنوز داده‌ای ثبت نشده است.')).toBeInTheDocument();
    expect(container.querySelectorAll('[style*="width"]')).toHaveLength(0);
  });
});
