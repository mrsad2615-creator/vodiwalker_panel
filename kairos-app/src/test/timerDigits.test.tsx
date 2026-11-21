import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/react';
import { CinematicFocusTimer } from '@/components/focus/CinematicFocusTimer';

const baseProps = {
  mode: 'pomodoro' as const,
  phase: 'focus' as const,
  isRunning: true,
  elapsedSeconds: 0,
  progressPercent: 42,
  completedCycles: 1,
  cyclesBeforeLong: 4,
  linkedTaskTitle: null,
  nextFocusMinutes: 25,
};

const PERSIAN = /[\u0600-\u06FF]/;

describe('timer read-out', () => {
  it('renders a single LTR run so RTL never reorders the digits', () => {
    const { getByTestId } = render(
      <CinematicFocusTimer {...baseProps} remainingSeconds={754} />
    );
    const row = getByTestId('timer-readout');

    expect(row.getAttribute('dir')).toBe('ltr');
    expect(row.textContent).toBe('12:34');
    // A Persian word inside the numeric run is what made the read-out look jumbled.
    expect(row.textContent).not.toMatch(PERSIAN);
  });

  it('keeps hours first when they exist', () => {
    const { getByTestId } = render(
      <CinematicFocusTimer {...baseProps} mode="free" remainingSeconds={0} elapsedSeconds={3725} />
    );
    expect(getByTestId('timer-readout').textContent).toBe('01:02:05');
  });

  it('pads a fresh timer to a stable width', () => {
    const { getByTestId } = render(
      <CinematicFocusTimer {...baseProps} isRunning={false} remainingSeconds={1500} />
    );
    expect(getByTestId('timer-readout').textContent).toBe('25:00');
  });

  it('does not put a Persian label inside the numeric run', () => {
    const { getByTestId, getByText } = render(
      <CinematicFocusTimer {...baseProps} phase="short_break" remainingSeconds={300} />
    );
    expect(getByTestId('timer-readout').textContent).not.toMatch(PERSIAN);
    // The label lives in its own node, in normal RTL flow.
    expect(getByText('تمرکز بعدی')).toBeInTheDocument();
    expect(getByText('دقیقه')).toBeInTheDocument();
  });
});
