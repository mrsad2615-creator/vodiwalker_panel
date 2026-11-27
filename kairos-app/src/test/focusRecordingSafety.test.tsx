import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProvider, useApp } from '@/context/AppContext';
import { clearActiveRun } from '@/lib/chrono/persistence';
import { db } from '@/lib/db';

function Harness() {
  const app = useApp();
  return <div>
    <div data-testid="clock">{`${app.ready}:${app.focus.mode}:${app.focus.isRunning}:${app.focus.recordedFocusSeconds ?? 0}`}</div>
    <button onClick={() => app.setChronoConfig({ blocks: 2, blockMinutes: 2, restMinutes: 1 })}>configure</button>
    <button onClick={() => app.setFocusMode('interval')}>interval</button>
    <button onClick={() => app.setAutoAdvance(true)}>auto</button>
    <button onClick={app.startOrResumeFocus}>start</button>
    <button onClick={() => void app.finishAndSaveFocus()}>finish</button>
    {app.toasts.map((toast) => <p key={toast.id}>{toast.title}</p>)}
  </div>;
}

beforeEach(async () => {
  localStorage.clear();
  await clearActiveRun();
  await db.transaction('rw', db.tables, () => Promise.all(db.tables.map((table) => table.clear())));
});
afterEach(() => vi.restoreAllMocks());

describe('focus recording safety', () => {
  it('keeps the recovery snapshot and retries when a completion cannot be saved', async () => {
    let now = 1_800_000_000_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    render(<AppProvider><Harness /></AppProvider>);
    await waitFor(() => expect(screen.getByTestId('clock')).toHaveTextContent('true:pomodoro'));
    fireEvent.click(screen.getByText('configure'));
    await waitFor(async () => expect((await db.settings.get('default'))?.intervalBlockMin).toBe(2));
    fireEvent.click(screen.getByText('interval'));
    fireEvent.click(screen.getByText('auto'));
    fireEvent.click(screen.getByText('start'));
    await waitFor(() => expect(screen.getByTestId('clock')).toHaveTextContent('interval:true'));
    await waitFor(async () => expect(await db.activeRun.get('active')).toMatchObject({ mode: 'interval', isRunning: true, autoAdvance: true }));
    const spy = vi.spyOn(db.activeRun, 'put').mockRejectedValueOnce(new Error('disk failure'));
    now += 190_000;
    fireEvent.focus(window);
    await screen.findByText('ثبت تمرکز انجام نشد');
    expect(await db.focusSessions.count()).toBe(0);
    expect((await db.activeRun.get('active'))?.segmentIndex).toBe(0);
    expect(screen.queryByText('جلسه تمرکز ثبت شد')).not.toBeInTheDocument();
    now += 11_000;
    fireEvent.focus(window);
    await waitFor(async () => expect(await db.focusSessions.count()).toBe(1));
    expect((await db.focusSessions.toArray())[0]).toMatchObject({ mode: 'interval', durationSeconds: 120 });
    spy.mockRestore();
  });

  it('records catch-up focus across a break, and manual finish records only the unrecorded portion', async () => {
    let now = 1_800_000_000_000;
    vi.spyOn(Date, 'now').mockImplementation(() => now);
    render(<AppProvider><Harness /></AppProvider>);
    await waitFor(() => expect(screen.getByTestId('clock')).toHaveTextContent('true:pomodoro'));
    fireEvent.click(screen.getByText('configure'));
    await waitFor(async () => expect((await db.settings.get('default'))?.intervalBlockMin).toBe(2));
    fireEvent.click(screen.getByText('interval'));
    fireEvent.click(screen.getByText('auto'));
    fireEvent.click(screen.getByText('start'));
    await waitFor(() => expect(screen.getByTestId('clock')).toHaveTextContent('interval:true'));

    now += 190_000; // 120 seconds focus, 60 seconds break, 10 seconds focus
    fireEvent.focus(window);
    await waitFor(async () => expect(await db.focusSessions.count()).toBe(1));
    await waitFor(() => expect(screen.getByTestId('clock')).toHaveTextContent('interval:true:120'));
    expect((await db.focusSessions.toArray())[0]).toMatchObject({ mode: 'interval', durationSeconds: 120 });
    expect((await db.activeRun.get('active'))?.recordedFocusSeconds).toBe(120);

    now += 60_000; // 70 seconds of the second block; don't send another tick first
    await act(async () => { fireEvent.click(screen.getByText('finish')); });
    await waitFor(async () => expect(await db.focusSessions.count()).toBe(2));
    expect((await db.focusSessions.toArray()).map((session) => session.durationSeconds).sort((a, b) => (a ?? 0) - (b ?? 0))).toEqual([70, 120]);
    expect(await db.activeRun.get('active')).toBeUndefined();
  });
});
