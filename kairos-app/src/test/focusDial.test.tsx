import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CinematicFocusTimer } from '@/components/focus/CinematicFocusTimer';
import type { FocusModeType } from '@/types/domain';

const props = {
  phase: 'focus' as const, isRunning: false, remainingSeconds: 125,
  elapsedSeconds: 17, progressPercent: 30, completedCycles: 0,
  cyclesBeforeLong: 4, linkedTaskTitle: null,
};

describe('focus dial', () => {
  for (const mode of ['pomodoro', 'interval', 'countdown'] as FocusModeType[]) {
    it(`shows remaining time for ${mode}`, () => {
      render(<CinematicFocusTimer {...props} mode={mode} />);
      expect(screen.getByTestId('timer-readout')).toHaveTextContent('02:05');
      expect(screen.getByRole('group')).toHaveAccessibleName(/باقی‌مانده، 2 دقیقه و 5 ثانیه/);
      expect(screen.getByText('زمان باقی‌مانده')).toBeInTheDocument();
    });
  }
  it('shows elapsed time for free mode', () => {
    render(<CinematicFocusTimer {...props} mode="free" />);
    expect(screen.getByTestId('timer-readout')).toHaveTextContent('00:17');
    expect(screen.getByRole('group')).toHaveAccessibleName(/سپری‌شده، 0 دقیقه و 17 ثانیه/);
  });
  it('shows an approximate finish only while running', () => {
    render(<CinematicFocusTimer {...props} mode="countdown" isRunning />);
    expect(screen.getByText('پایان تقریبی')).toBeInTheDocument();
  });
});
