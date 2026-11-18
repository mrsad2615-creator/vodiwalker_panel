import { describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';

function renderApp() {
  return render(<App />);
}

async function armAutomation() {
  renderApp();
  fireEvent.click(screen.getByRole('tab', { name: 'Privacy' }));
  const flow = await screen.findByRole('region', { name: /permission & privacy flow/i });

  // Step 1: prominent disclosure.
  fireEvent.click(within(flow).getByRole('button', { name: /review & consent/i }));
  // Step 2: accessibility service.
  fireEvent.click(within(flow).getByRole('button', { name: /open system settings/i }));
  return flow;
}

describe('VodiGesture workbench', () => {
  it('renders the cinematic shell with all four M3 schemes', () => {
    renderApp();
    expect(screen.getByRole('heading', { level: 1, name: /vodigesture/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /obsidian cinema/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    for (const label of ['Obsidian Cinema', 'Daylight Studio', 'Obsidian AAA', 'Daylight AAA']) {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument();
    }
    for (const tab of ['Studio', 'Haptics', 'Power', 'Privacy']) {
      expect(screen.getByRole('tab', { name: tab })).toBeInTheDocument();
    }
  });

  it('switches M3 color schemes and repaints role tokens', () => {
    renderApp();
    fireEvent.click(screen.getByRole('button', { name: /daylight studio/i }));
    expect(document.documentElement.dataset.schemeMode).toBe('light');
    expect(screen.getByRole('button', { name: /daylight studio/i })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('locks the gesture canvas until the accessibility step is granted', () => {
    renderApp();
    const canvas = screen.getByRole('img', { name: /draw a gesture/i });
    expect(canvas).toBeInTheDocument();
    expect(screen.getByTestId('canonical-gesture-name')).toHaveTextContent(/awaiting stroke/i);
  });

  it('completes the permission flow and reports automation armed', async () => {
    const flow = await armAutomation();
    await waitFor(() =>
      expect(within(flow).getByTestId('permission-readiness')).toHaveTextContent(/automation armed/i),
    );
    // Enabling the service proves Restricted Settings no longer blocks it.
    await waitFor(() =>
      expect(
        within(flow).queryByText(/restricted settings active/i),
      ).not.toBeInTheDocument(),
    );
    // Disclosure + accessibility are both recorded as completed steps.
    expect(within(flow).getAllByRole('button', { name: 'Completed' })).toHaveLength(2);
  });

  it('draws a stroke on the canvas and produces a recognition verdict', () => {
    renderApp();
    const canvas = screen.getByRole('img', { name: /draw a gesture/i });
    fireEvent.pointerDown(canvas, { clientX: 40, clientY: 120, pointerId: 1 });
    for (let index = 0; index < 24; index += 1) {
      fireEvent.pointerMove(canvas, { clientX: 40 + index * 22, clientY: 120, pointerId: 1 });
    }
    fireEvent.pointerUp(canvas, { clientX: 560, clientY: 120, pointerId: 1 });
    expect(screen.getByTestId('canonical-confidence')).toBeInTheDocument();
    expect(screen.getByTestId('canonical-latency')).toHaveTextContent(/ms/);
  });

  it('shows the strict priority chain in the power governor', async () => {
    renderApp();
    fireEvent.click(screen.getByRole('tab', { name: 'Power' }));
    return waitFor(() => {
      expect(screen.getByTestId('power-mode')).toHaveTextContent(/balanced|performance/i);
    }).then(() => {
      fireEvent.click(screen.getByLabelText(/privacy-sensitive window/i));
      return waitFor(() => {
        expect(screen.getByTestId('power-mode')).toHaveTextContent(/privacy shield suspended/i);
      });
    }).then(() => {
      fireEvent.click(screen.getByLabelText(/privacy-sensitive window/i));
      fireEvent.click(screen.getByLabelText(/system battery saver/i));
      return waitFor(() => {
        expect(screen.getByTestId('power-mode')).toHaveTextContent(/eco/i);
      });
    });
  });

  it('negotiates haptic tiers and replays the primitive timeline', async () => {
    renderApp();
    fireEvent.click(screen.getByRole('tab', { name: 'Haptics' }));
    await waitFor(() => expect(screen.getByTestId('haptic-tier')).toHaveTextContent(/composition/i));
    fireEvent.click(screen.getByRole('button', { name: /replay pattern/i }));
    const timeline = screen.getByTestId('haptic-timeline');
    expect(timeline.children.length).toBeGreaterThan(0);
  });
});
