import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { SectionHeader } from '@/components/ui/SectionHeader';

describe('section header hierarchy', () => {
  it('keeps the page title, context and action accessible', () => {
    render(<SectionHeader eyebrow="PLANNER" title="وظایف" meta="کارهای امروز" action={<button type="button">افزودن</button>} />);
    expect(screen.getByRole('heading', { level: 1, name: 'وظایف' })).toBeInTheDocument();
    expect(screen.getByText('کارهای امروز')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'افزودن' })).toBeInTheDocument();
  });
  it('uses a subordinate heading for an in-page section', () => {
    render(<SectionHeader size="section" title="آرشیو" />);
    expect(screen.getByRole('heading', { level: 2, name: 'آرشیو' })).toBeInTheDocument();
  });
});
