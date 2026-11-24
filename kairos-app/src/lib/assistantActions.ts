import { db } from './db';
import { addDays, formatJalaaliFull, getTodayKey } from './date';
import { createId } from './format';
import { normalizeCommand } from './commands';
import type { NavTab, Project, Task } from '@/types/domain';
import type { UndoEntry } from './undo';

type ChangeKind = 'task.complete' | 'task.today' | 'task.tomorrow' | 'task.high' | 'project.pause' | 'project.resume';
type Candidate = { id: string; title: string; kind: 'task' | 'project'; sample?: boolean };
export type AssistantRequest =
  | { type: 'navigation'; tab: NavTab; label: string }
  | { type: 'change'; kind: ChangeKind; candidates: Candidate[] }
  | { type: 'blocked'; message: string }
  | { type: 'none' };

const destinations: { tab: NavTab; label: string; match: RegExp }[] = [
  { tab: 'dashboard', label: 'خانه', match: /خانه|داشبورد/ },
  { tab: 'tasks', label: 'وظایف', match: /وظایف|وظیفه|کارها/ },
  { tab: 'habits', label: 'عادت‌ها', match: /عادت/ },
  { tab: 'projects', label: 'پروژه‌ها', match: /پروژه/ },
  { tab: 'calendar', label: 'تقویم', match: /تقویم/ },
  { tab: 'focus', label: 'تمرکز', match: /تمرکز|تایمر/ },
  { tab: 'analytics', label: 'تحلیل عملکرد', match: /تحلیل|آمار/ },
  { tab: 'motivation', label: 'انگیزه', match: /انگیزه|جملات/ },
  { tab: 'settings', label: 'تنظیمات', match: /تنظیمات/ },
];

/** Deterministic intent recognition. No destructive operation is exposed. */
export function interpretAssistantRequest(raw: string, tasks: Task[], projects: Project[]): AssistantRequest {
  const q = normalizeCommand(raw);
  if (!q) return { type: 'none' };
  if (/(حذف|پاک|دیلیت|delete|clear|نکن|نشه|نشود|نیست)/.test(q)) {
    return { type: 'blocked', message: 'حذف داده یا دستور منفی را اجرا نمی‌کنم. تغییر موردنظر را مثبت و روشن بگو.' };
  }
  if (/(برو|باز کن|ببر)/.test(q) && /(تکمیل کن|تمام کن|متوقف کن|مکث کن|فعال کن|منتقل کن|حذف کن|بساز|اضافه کن)/.test(q)) {
    return { type: 'blocked', message: 'بازکردن بخش و تغییر داده را جداگانه درخواست کن.' };
  }
  if (/(برو|باز کن|ببر)/.test(q)) {
    const matches = destinations.filter((item) => item.match.test(q));
    if (matches.length === 1) return { type: 'navigation', tab: matches[0].tab, label: matches[0].label };
    if (matches.length > 1) return { type: 'blocked', message: 'چند بخش نام بردی؛ لطفاً مقصد را مشخص کن.' };
  }
  let kind: ChangeKind | null = null;
  if (/پروژه/.test(q)) {
    const pause = /(مکث|متوقف|توقف)/.test(q);
    const resume = /(از سر بگیر|دوباره فعال|فعال کن)/.test(q);
    if (pause && resume) return { type: 'blocked', message: 'دو تغییر متفاوت گفتی؛ هر تغییر را جداگانه درخواست کن.' };
    if (pause) kind = 'project.pause';
    else if (resume) kind = 'project.resume';
  } else if (/(وظیفه|وظایف|کار)/.test(q)) {
    const actions: ChangeKind[] = [];
    if (/(تکمیل کن|انجام شد|تمام کن|انجام دادم)/.test(q)) actions.push('task.complete');
    if (/(انتقال|منتقل|موعد|بنداز|بذار|بگذار|برنامه ریزی)/.test(q)) {
      if (/امروز/.test(q) && !/فردا/.test(q)) actions.push('task.today');
      if (/فردا/.test(q) && !/امروز/.test(q)) actions.push('task.tomorrow');
    }
    if (/(اولویت بالا|اولویت زیاد)/.test(q) && /(کن|بگذار|بذار|تغییر)/.test(q)) actions.push('task.high');
    if (actions.length > 1 || (/امروز/.test(q) && /فردا/.test(q))) return { type: 'blocked', message: 'چند تغییر یا تاریخ متفاوت گفتی؛ هر تغییر را جداگانه درخواست کن.' };
    kind = actions[0] ?? null;
  }
  if (!kind) return { type: 'none' };
  const pool: Candidate[] = kind.startsWith('task.')
    ? tasks.filter((t) => !t.archived).map((t) => ({ id: t.id, title: t.title, kind: 'task', sample: t.sample }))
    : projects.filter((p) => p.status !== 'archived').map((p) => ({ id: p.id, title: p.title, kind: 'project', sample: p.sample }));
  const named = pool.filter((item) => {
    const name = normalizeCommand(item.title);
    return name.length >= 2 && (` ${q} `).includes(` ${name} `);
  });
  return { type: 'change', kind, candidates: named.length ? named : pool };
}

export type ChangePreview = {
  kind: ChangeKind;
  entity: 'task' | 'project';
  before: Task | Project;
  after: Task | Project;
  description: string;
  preparedDay: string;
};

export function previewChange(kind: ChangeKind, row: Task | Project, today = getTodayKey()): ChangePreview {
  const now = new Date().toISOString();
  if (kind.startsWith('task.')) {
    const task = row as Task;
    if (task.archived) throw new Error('این وظیفه بایگانی شده است.');
    if (task.status === 'completed') throw new Error('این وظیفه تکمیل شده است؛ تغییرش را در بخش وظایف انجام بده.');
    let after: Task;
    let description: string;
    if (kind === 'task.complete') {
      if (task.recurrence !== 'none') throw new Error('برای حفظ تکرار، این وظیفه را از بخش وظایف تکمیل کن.');
      after = { ...task, status: 'completed', completedAt: now, completedDate: today, updatedAt: now };
      description = 'وضعیت: تکمیل‌شده. تاریخ تکمیل امروز ثبت می‌شود.';
    } else if (kind === 'task.high') {
      if (task.priority === 'high') throw new Error('اولویت این وظیفه از قبل بالا است.');
      after = { ...task, priority: 'high', updatedAt: now };
      description = `اولویت: ${task.priority === 'low' ? 'کم' : 'متوسط'} ← بالا.`;
    } else {
      const date = kind === 'task.today' ? today : addDays(today, 1);
      if (task.dueDate === date) throw new Error('موعد این وظیفه از قبل همین تاریخ است.');
      after = { ...task, dueDate: date, postponeCount: task.dueDate && date > task.dueDate ? task.postponeCount + 1 : task.postponeCount, updatedAt: now };
      description = `موعد: ${task.dueDate ? formatJalaaliFull(task.dueDate).compact : 'بدون تاریخ'} ← ${formatJalaaliFull(date).compact}؛ ساعت و یادآوری قبلی حفظ می‌شوند.`;
    }
    return { kind, entity: 'task', before: task, after, description, preparedDay: today };
  }
  const project = row as Project;
  if (project.status === 'archived' || project.status === 'done') throw new Error('این پروژه بایگانی یا تمام شده است؛ از بخش پروژه‌ها تغییرش بده.');
  const status = kind === 'project.pause' ? 'paused' : 'active';
  if (project.status === status) throw new Error('وضعیت پروژه از قبل همین است.');
  return { kind, entity: 'project', before: project, after: { ...project, status, updatedAt: now }, description: status === 'paused' ? 'وضعیت پروژه: فعال ← متوقف.' : 'وضعیت پروژه: متوقف ← فعال.', preparedDay: today };
}

/** Checks the full snapshot and visibility inside the write transaction. */
export async function applyConfirmedChange(preview: ChangePreview, confirmed: boolean): Promise<string> {
  const allowed: ChangeKind[] = ['task.complete', 'task.today', 'task.tomorrow', 'task.high', 'project.pause', 'project.resume'];
  if (!confirmed || !allowed.includes(preview.kind) || preview.entity !== (preview.kind.startsWith('task.') ? 'task' : 'project') || preview.preparedDay !== getTodayKey()) throw new Error('تأیید یا تاریخ معتبر نیست؛ دوباره پیش‌نمایش بگیر.');
  const table = preview.entity === 'task' ? db.tasks : db.projects;
  return db.transaction('rw', [db.tasks, db.projects, db.settings, db.undoHistory, db.eventHistory], async () => {
    const current = await table.get(preview.before.id);
    const settings = await db.settings.get('default');
    if (!current || JSON.stringify(current) !== JSON.stringify(preview.before) || (current.sample && settings?.sampleVisible === false)) {
      throw new Error('داده تغییر کرده یا پنهان شده است؛ دوباره درخواست بده.');
    }
    if (preview.preparedDay !== getTodayKey()) throw new Error('تاریخ روز عوض شده؛ دوباره پیش‌نمایش بگیر.');
    // Rebuild the permitted field change at commit time, never trust a caller's
    // supplied after-snapshot to alter fields outside the chosen action.
    const safeAfter = previewChange(preview.kind, current, preview.preparedDay).after;
    const latest = await db.undoHistory.orderBy('at').last();
    const previous = latest ? Date.parse(latest.at) : NaN;
    const at = new Date(Math.max(Date.now(), Number.isFinite(previous) ? previous + 1 : 0)).toISOString();
    const undoId = createId('und');
    const undo: UndoEntry = {
      id: undoId, at, kind: preview.entity === 'task' ? (preview.kind === 'task.complete' ? 'task.status' : 'task.update') : 'project.status',
      entity: preview.entity, entityId: current.id, label: 'تغییر تأییدشده در دستیار', before: current, after: safeAfter, undoneAt: null,
    };
    await table.put(safeAfter as never);
    await db.undoHistory.add(undo);
    await db.eventHistory.add({ id: createId('evt'), at, kind: preview.entity === 'task' ? 'task.updated' : 'project.updated', entity: preview.entity, entityId: current.id, source: 'user', level: 2, data: { via: 'assistant-confirmed', operation: preview.kind } });
    return undoId;
  });
}

/** Revert only this assistant edit and only while its result is untouched. Never delete. */
export async function revertAssistantChange(id: string): Promise<void> {
  await db.transaction('rw', [db.tasks, db.projects, db.settings, db.undoHistory, db.eventHistory], async () => {
    const entry = await db.undoHistory.get(id);
    if (!entry || entry.undoneAt || !entry.before || !entry.after || entry.label !== 'تغییر تأییدشده در دستیار' || !['task', 'project'].includes(entry.entity)) throw new Error('بازگشت برای این تغییر در دسترس نیست.');
    const table = entry.entity === 'task' ? db.tasks : db.projects;
    const current = await table.get(entry.entityId);
    const settings = await db.settings.get('default');
    if (!current || JSON.stringify(current) !== JSON.stringify(entry.after) || (current.sample && settings?.sampleVisible === false)) throw new Error('داده بعد از این تغییر ویرایش یا پنهان شده است؛ بازگشت خودکار امن نیست.');
    await table.put(entry.before as never);
    await db.undoHistory.put({ ...entry, undoneAt: new Date().toISOString() });
    await db.eventHistory.add({ id: createId('evt'), at: new Date().toISOString(), kind: 'undo.applied', entity: entry.entity, entityId: entry.entityId, source: 'undo', level: 3, data: { via: 'assistant-confirmed' } });
  });
}
