import { useEffect, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { db } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import { isNativeAndroid } from '@/lib/native';
import { buildWidgetSnapshot, type WidgetSnapshot } from '@/lib/widgetSnapshot';
import type { NavTab, Task } from '@/types/domain';

interface WidgetPlugin { sync(options: { snapshot: WidgetSnapshot }): Promise<void> }
const Widgets = registerPlugin<WidgetPlugin>('KairosWidgets');
// Sequential writes prevent an older snapshot from finishing after a newer one.
let queue: Promise<void> = Promise.resolve();
export function syncWidgetSnapshot(snapshot: WidgetSnapshot): Promise<void> {
  if (!isNativeAndroid()) return Promise.resolve();
  queue = queue.catch(() => undefined).then(() => Widgets.sync({ snapshot }));
  return queue;
}

export function widgetDestination(url: string): NavTab | null {
  if (url === 'kairos-widget://open/tasks') return 'tasks';
  if (url === 'kairos-widget://open/analytics') return 'analytics';
  if (url === 'kairos-widget://open/focus') return 'focus';
  return null;
}

/** Opens a form only; widget URLs never mutate application data. */
export function widgetCreateTask(url: string): boolean {
  return url === 'kairos-widget://open/create-task';
}

/** Accept only an opaque task ID; no widget URI can mutate data. */
export function widgetTaskId(url: string): string | null {
  return /^kairos-widget:\/\/open\/task\/([A-Za-z0-9_-]{1,100})$/.exec(url)?.[1] ?? null;
}

/** Re-read permissions and task after a tap; a stale widget never opens hidden,
 * archived, deleted, or no-longer-today content. */
export async function resolveWidgetTask(id: string, today = getTodayKey()): Promise<Task | null> {
  return db.transaction('r', [db.tasks, db.settings], async () => {
    const [task, settings] = await Promise.all([db.tasks.get(id), db.settings.get('default')]);
    if (!task || !settings || settings.widgetShowTitles === false || task.archived || task.dueDate !== today || (task.sample && settings.sampleVisible === false)) return null;
    return task;
  });
}

/** Runs once in the shell. LiveQuery's undefined loading state must never erase data. */
export function WidgetSync({ onNavigate, onOpenTask, onCreateTask }: { onNavigate: (tab: NavTab) => void; onOpenTask: (task: Task) => void; onCreateTask: () => void }) {
  const [day, setDay] = useState(getTodayKey);
  const snapshot = useLiveQuery(async () => {
    const [tasks, settings] = await db.transaction('r', db.tasks, db.settings, async () =>
      Promise.all([db.tasks.toArray(), db.settings.get('default')]));
    if (!settings) return undefined;
    return buildWidgetSnapshot(tasks, day, settings.widgetShowTitles !== false, settings.sampleVisible !== false, settings.accentColor);
  }, [day]);
  useEffect(() => { if (snapshot) void syncWidgetSnapshot(snapshot).catch(() => { window.dispatchEvent(new Event('kairos-widget-sync-error')); }); }, [snapshot]);
  useEffect(() => {
    const refresh = () => setDay(getTodayKey());
    const timer = window.setInterval(refresh, 60_000);
    document.addEventListener('visibilitychange', refresh);
    return () => { window.clearInterval(timer); document.removeEventListener('visibilitychange', refresh); };
  }, []);
  useEffect(() => {
    if (!isNativeAndroid()) return;
    let disposed = false;
    let remove: (() => void) | undefined;
    let receivedNewUrl = false;
    let sequence = 0;
    const open = (url: string) => {
      const request = ++sequence;
      if (!disposed && widgetCreateTask(url)) { onNavigate('tasks'); onCreateTask(); return; }
      const tab = widgetDestination(url);
      if (!disposed && tab) { onNavigate(tab); return; }
      const id = widgetTaskId(url);
      if (!id) return;
      void resolveWidgetTask(id).then((task) => {
        if (disposed || request !== sequence) return;
        onNavigate('tasks');
        if (task) onOpenTask(task);
      }).catch(() => { if (!disposed && request === sequence) onNavigate('tasks'); });
    };
    void App.addListener('appUrlOpen', ({ url }) => { receivedNewUrl = true; open(url); }).then((listener) => {
      if (disposed) { void listener.remove(); return; }
      remove = () => { void listener.remove(); };
      // A newer tap must not be overwritten by an old cold-start URL.
      void App.getLaunchUrl().then((result) => { if (!receivedNewUrl && result?.url) open(result.url); }).catch(() => undefined);
    }).catch(() => undefined);
    return () => { disposed = true; remove?.(); };
  }, [onNavigate, onOpenTask, onCreateTask]);
  return null;
}
