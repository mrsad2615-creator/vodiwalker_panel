import { useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db } from '@/lib/db';
import { undoEntry, type UndoEntry } from '@/lib/undo';
import type { Task } from '@/types/domain';

/** Persisted, conflict-checked undo for recent task time edits. Never expose
 * hidden sample rows, including in the period before settings are loaded. */
export function RecentTimeChanges() {
  const [busy, setBusy] = useState<string | null>(null);
  const busyRef = useRef(false);
  const [message, setMessage] = useState('');
  const entries = useLiveQuery(() => db.transaction('r', [db.undoHistory, db.settings, db.tasks], async () => {
    const [rows, settings, tasks] = await Promise.all([db.undoHistory.orderBy('at').reverse().toArray(), db.settings.get('default'), db.tasks.toArray()]);
    const hiddenIds = new Set(tasks.filter((task) => task.sample === true).map((task) => task.id));
    return rows.filter((row: UndoEntry) => {
      if (row.undoneAt || row.kind !== 'task.update' || !row.before || !row.after) return false;
      const before = row.before as Task; const after = row.after as Task;
      if (before.dueTime === after.dueTime) return false;
      if (!settings || settings.sampleVisible === false) return !before.sample && !after.sample && !hiddenIds.has(row.entityId);
      return true;
    }).slice(0, 5);
  }), [], []);

  const undo = async (id: string) => {
    if (busyRef.current) return;
    busyRef.current = true; setBusy(id); setMessage('');
    try {
      const result = await undoEntry(id);
      setMessage(result.ok ? 'زمان قبلی بازگردانده شد.' : 'بازگردانی انجام نشد؛ ممکن است وظیفه بعداً تغییر کرده باشد.');
    } catch { setMessage('بازگردانی انجام نشد؛ دوباره تلاش کن.'); }
    finally { busyRef.current = false; setBusy(null); }
  };
  if (entries.length === 0 && !message) return null;
  return <section aria-label="بازگردانی زمان وظایف" className="rail space-y-2 p-4">
    <h2 className="text-sm font-semibold text-[var(--text)]">زمان‌های تغییرکردهٔ اخیر</h2>
    <p className="text-xs leading-6 text-[var(--text-2)]">اگر وظیفه بعداً تغییر کرده باشد، بازگردانی برای محافظت از آن رد می‌شود.</p>
    {entries.map((entry) => {
      const after = entry.after as Task;
      return <div key={entry.id} className="flex items-center justify-between gap-3 border-t border-[var(--border)] pt-2">
        <span className="min-w-0 truncate text-sm text-[var(--text)]">{after.title}{after.sample ? ' · نمونه' : ''}</span>
        <button type="button" disabled={busy !== null} onClick={() => void undo(entry.id)} aria-label={`بازگردانی زمان ${after.title}`} className="min-h-11 shrink-0 rounded-xl border border-[var(--border-strong)] px-3 text-xs text-[var(--accent-fg)] disabled:opacity-50">بازگردانی</button>
      </div>;
    })}
    {message && <p role="status" className="text-xs leading-6 text-[var(--text-2)]">{message}</p>}
  </section>;
}
