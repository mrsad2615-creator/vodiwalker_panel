import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { SoundStudio } from '@/components/focus/SoundStudio';
import { LAYERS } from '@/lib/audio/layers';

describe('Sound Studio', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  it('lists every synthesised layer with its own slider', () => {
    render(<SoundStudio />);
    for (const layer of LAYERS) {
      expect(screen.getByLabelText(`سطح ${layer.label}`)).toBeInTheDocument();
      expect(screen.getByText(layer.label)).toBeInTheDocument();
    }
    expect(screen.getByText('فضای صوتی تمرکز (آفلاین)')).toBeInTheDocument();
  });

  it('applies a profile to the mixer', () => {
    render(<SoundStudio />);
    fireEvent.click(screen.getByText('تمرکز عمیق'));

    // deep = brown 0.55 + rain 0.3
    expect(screen.getByLabelText('نویز قهوه‌ای — روشن')).toBeInTheDocument();
    expect(screen.getByLabelText('باران — روشن')).toBeInTheDocument();
    expect(screen.getByLabelText('باد — خاموش')).toBeInTheDocument();
  });

  it('starts and stops cleanly without an audio device', () => {
    render(<SoundStudio />);
    fireEvent.click(screen.getByText('پخش ترکیب'));
    // No layers chosen → the studio falls back to the deep profile.
    expect(screen.getByText('توقف صدا')).toBeInTheDocument();

    fireEvent.click(screen.getByText('توقف صدا'));
    expect(screen.getByText('پخش ترکیب')).toBeInTheDocument();
  });

  it('remembers the mix between mounts', () => {
    const first = render(<SoundStudio />);
    fireEvent.click(screen.getByText('آرامش'));
    first.unmount();

    render(<SoundStudio />);
    expect(screen.getByLabelText('اقیانوس — روشن')).toBeInTheDocument();
  });

  it('switches the breathing pattern and starts the pacer', () => {
    const { container } = render(<SoundStudio />);
    const pacer = screen.getByText('پیسر تنفس (خودآگاهی)').closest('div')!.parentElement!;

    fireEvent.click(within(pacer).getByText('آرام‌سازی ۴-۷-۸'));
    expect(within(pacer).getByText(/دم کوتاه/)).toBeInTheDocument();

    fireEvent.click(within(pacer).getByText('شروع'));
    expect(within(pacer).getByText('پایان')).toBeInTheDocument();
    expect(container.querySelectorAll('svg circle').length).toBeGreaterThan(0);
  });

  it('sets a sleep timer without pretending sound is playing', () => {
    render(<SoundStudio />);
    fireEvent.click(screen.getByText('30د'));
    // A sleep timer on a loaded-but-silent mixer says «آماده», not «در حال پخش»:
    // the studio never pretends to be playing before the user presses play.
    expect(screen.getByText('آماده')).toBeInTheDocument();
  });
});
