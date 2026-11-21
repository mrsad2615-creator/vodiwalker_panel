import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '@/App';
import { db } from '@/lib/db';

/** Reads the collapsible wrapper the top bar lives in. */
function headerWrapper(): HTMLElement {
  const header = document.querySelector('.app-safe-header');
  if (!header?.parentElement) throw new Error('top bar not mounted');
  return header.parentElement as HTMLElement;
}

describe('Top bar behaves like a product, not a fixed slab', () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await db.transaction(
      'rw',
      [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings],
      async () => {
        await Promise.all([
          db.tasks.clear(),
          db.habits.clear(),
          db.habitLogs.clear(),
          db.focusSessions.clear(),
          db.goals.clear(),
          db.settings.clear(),
        ]);
      }
    );
  });

  it('tucks itself away on scroll down and returns on scroll up', async () => {
    render(<App />);
    fireEvent.click(screen.getByRole('status', { name: /kairos/i }));
    await waitFor(() => expect(screen.getByText('پیشرفت امروز')).toBeInTheDocument());

    const main = document.querySelector('main');
    expect(main).not.toBeNull();
    expect(headerWrapper().style.gridTemplateRows).toBe('1fr');

    Object.defineProperty(main as HTMLElement, 'scrollTop', {
      value: 260,
      writable: true,
      configurable: true,
    });
    fireEvent.scroll(main as HTMLElement);

    await waitFor(() => expect(headerWrapper().style.gridTemplateRows).toBe('0fr'), { timeout: 3000 });

    (main as HTMLElement).scrollTop = 120;
    fireEvent.scroll(main as HTMLElement);

    await waitFor(() => expect(headerWrapper().style.gridTemplateRows).toBe('1fr'), { timeout: 3000 });
  });

  it('never lets the grain layer become full-screen static', () => {
    // Regression guard for the 1.4.2 defect: a per-section rule raised the grain
    // layer to ~1.0 and turned every screen into television noise.
    const css = readFileSync(join(process.cwd(), 'src/styles/design-system.css'), 'utf8');

    const grains = [...css.matchAll(/--atmosphere-grain:\s*([\d.]+)/g)].map((m) =>
      Number(m[1])
    );
    expect(grains.length).toBeGreaterThan(4);
    for (const value of grains) expect(value).toBeLessThanOrEqual(0.03);

    // The grain layer's own opacity must always come from the token.
    const after = css.match(/\.kairos-ambient-bg::after\s*\{([^}]*)\}/);
    expect(after?.[1]).toContain('opacity: var(--atmosphere-grain');
    expect(css).not.toMatch(/kairos-ambient-bg::after\s*\{[^}]*opacity:\s*0?\.[5-9]/);
  });
});
