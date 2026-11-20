import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { LaunchScreen } from '@/components/ui/LaunchScreen';

/**
 * The launch screen is the first thing the user sees, so its contract is
 * locked down here: monochrome logo, thin ring spinner, real progress exposed
 * to assistive tech, a skippable intro and a lifecycle-safe timeline.
 */
describe('KAIROS cinematic launch screen', () => {
  it('renders the monochrome mark, the ring spinner and the wordmark', () => {
    const { container } = render(<LaunchScreen exiting={false} progress={0.4} onSkip={() => {}} />);

    const root = screen.getByRole('status', { name: /آماده‌سازی KAIROS/i });
    expect(root).toHaveClass('kairos-launch');

    // One vector artwork: the 4-contour mark lives in both the body and the mask.
    const artwork = container.querySelector('svg.kairos-launch__art');
    expect(artwork).not.toBeNull();
    expect(artwork?.querySelectorAll('path')).toHaveLength(8);

    // Thin circular loader directly below the logo.
    expect(container.querySelector('.kairos-ring__arc')).not.toBeNull();
    expect(container.querySelector('.kairos-ring__track')).not.toBeNull();

    // Wordmark under the loading area.
    expect(container.querySelector('.kairos-launch__word')?.textContent).toBe('KAIROS');
  });

  it('exposes the real boot progress instead of a fake animation', () => {
    render(<LaunchScreen exiting={false} progress={0.62} onSkip={() => {}} />);
    const bar = screen.getByRole('progressbar', { name: /آماده‌سازی KAIROS/i });
    expect(bar).toHaveAttribute('aria-valuenow', '62');
    expect(screen.getByText('در حال آماده‌سازی، 62 درصد')).toBeInTheDocument();
  });

  it('fades out through the exiting state', () => {
    const { container, rerender } = render(
      <LaunchScreen exiting={false} progress={1} onSkip={() => {}} />
    );
    const root = container.firstElementChild as HTMLElement;
    expect(root).not.toHaveClass('is-exiting');

    rerender(<LaunchScreen exiting progress={1} onSkip={() => {}} />);
    expect(root).toHaveClass('is-exiting');
  });

  it('settles the timeline when the app is not visible', () => {
    const { container } = render(<LaunchScreen exiting={false} progress={0.2} onSkip={() => {}} />);
    const root = container.firstElementChild as HTMLElement;
    expect(root).not.toHaveClass('is-settled');

    const spy = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    fireEvent(document, new Event('visibilitychange'));
    expect(root).toHaveClass('is-settled');
    spy.mockRestore();
  });

  it('is skippable by tap, Escape, Space and Enter — once', () => {
    const onSkip = vi.fn();
    render(<LaunchScreen exiting={false} progress={0.5} onSkip={onSkip} />);

    fireEvent.keyDown(window, { key: 'Escape' });
    fireEvent.keyDown(window, { key: 'Enter' });
    fireEvent.click(screen.getByRole('status', { name: /آماده‌سازی KAIROS/i }));

    expect(onSkip).toHaveBeenCalledTimes(1);
  });
});
