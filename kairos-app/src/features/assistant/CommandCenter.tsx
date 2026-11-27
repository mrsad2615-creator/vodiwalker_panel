import { useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useLiveQuery } from 'dexie-react-hooks';
import { Brain, Sparkles } from '@/components/ui/icons';
import { useApp } from '@/context/AppContext';
import {
  useGoals,
  useHabitLogs,
  useHabits,
  useFocusSessions,
  useProjectStages,
  useProjects,
  useTasks,
} from '@/lib/queries';
import { buildDailySeries } from '@/lib/homeMetrics';
import { formatJalaaliFull, getTodayKey } from '@/lib/date';
import { createConfirmedTodayTasks, parseTaskLines, validateTaskTitles, wantsTodayTaskList } from '@/lib/assistantTaskDraft';
import { QUICK_COMMANDS, runCommand, type CommandAnswer } from '@/lib/commands';
import { applyConfirmedChange, interpretAssistantRequest, previewChange, revertAssistantChange, type AssistantRequest, type ChangePreview } from '@/lib/assistantActions';
import { buildDailyBrief } from '@/lib/coach';
import { triggerHaptic } from '@/lib/native';
import { db } from '@/lib/db';
import { createId } from '@/lib/format';
import { deleteAssistantHistorySnapshot } from '@/lib/assistantHistory';
import type { AssistantHistoryEntry, NavTab } from '@/types/domain';

/**
 * The command centre: a Persian question box with no model behind it.
 *
 * The current implementation uses local rules, not a language model. It reads
 * local rows and reports when a request is outside the supported vocabulary.
 */

const CHANGE_LABELS = {
  'task.complete': 'تکمیل وظیفه', 'task.today': 'انتقال موعد به امروز', 'task.tomorrow': 'انتقال موعد به فردا',
  'task.high': 'اولویت بالای وظیفه', 'project.pause': 'مکث پروژه', 'project.resume': 'فعال‌کردن پروژه',
} as const;

export function CommandCenter() {
  const [query, setQuery] = useState('');
  // Read visibility and history in one snapshot. Until settings load, fail closed
  // so a hidden sample cannot flash on screen during startup.
  const historySnapshot = useLiveQuery(() => db.transaction('r', [db.assistantHistory, db.settings], async () => {
    const [rows, settings] = await Promise.all([db.assistantHistory.orderBy('createdAt').reverse().toArray(), db.settings.get('default')]);
    return { rows, showSample: !!settings && settings.sampleVisible !== false };
  }));
  const savedHistory = historySnapshot?.rows ?? [];
  const [pendingHistory, setPendingHistory] = useState<{ entry: AssistantHistoryEntry; failed: boolean }[]>([]);
  const [showHistoryCount, setShowHistoryCount] = useState(30);
  const [clearHistorySnapshot, setClearHistorySnapshot] = useState<AssistantHistoryEntry[] | null>(null);
  const historyClock = useRef(0);
  const [request, setRequest] = useState<Extract<AssistantRequest, { type: 'change' }> | null>(null);
  const [changePreview, setChangePreview] = useState<ChangePreview | null>(null);
  const [changeError, setChangeError] = useState('');
  const [lastChange, setLastChange] = useState<string | null>(null);
  const [draftOpen, setDraftOpen] = useState(false);
  const [draftText, setDraftText] = useState('');
  const [preview, setPreview] = useState<{ titles: string[]; dateKey: string } | null>(null);
  const [draftError, setDraftError] = useState('');
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);

  const tasks = useTasks();
  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const goals = useGoals();
  const projects = useProjects();
  const stages = useProjectStages();
  const sessions = useFocusSessions();
  const { setActiveTab, pushToast } = useApp();
  const hasVisibleSample = [...tasks, ...habits, ...habitLogs, ...goals, ...projects, ...stages, ...sessions].some((row) => row.sample === true);
  const savedIds = new Set(savedHistory.map((entry) => entry.id));
  const history = [...pendingHistory.map(({ entry }) => entry).filter((entry) => !savedIds.has(entry.id)), ...savedHistory]
    .filter((entry) => historySnapshot?.showSample === true || !entry.sample)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));

  const persistHistory = (entry: AssistantHistoryEntry) => {
    void db.assistantHistory.add(entry).then(() => {
      setPendingHistory((rows) => rows.filter((row) => row.entry.id !== entry.id));
    }).catch(() => {
      setPendingHistory((rows) => rows.map((row) => row.entry.id === entry.id ? { ...row, failed: true } : row));
      pushToast({ title: 'گفت‌وگو ذخیره نشد', description: 'پاسخ فقط تا بسته‌شدن این صفحه باقی می‌ماند؛ دوباره ذخیره کنید.', tone: 'warning' });
    });
  };
  const recordHistory = (answer: CommandAnswer, prompt?: string) => {
    const timestamp = Math.max(Date.now(), historyClock.current + 1);
    historyClock.current = timestamp;
    const entry: AssistantHistoryEntry = { ...answer, id: createId('chat'), createdAt: new Date(timestamp).toISOString(), sample: hasVisibleSample, ...(prompt ? { prompt } : {}) };
    setPendingHistory((rows) => [{ entry, failed: false }, ...rows]);
    persistHistory(entry);
  };

  const todayKey = getTodayKey();
  const series = useMemo(
    () => buildDailySeries({ focusSessions: sessions, tasks, habitLogs }, todayKey, 14),
    [sessions, tasks, habitLogs, todayKey]
  );

  const brief = useMemo(
    () =>
      buildDailyBrief({
        tasks,
        habits,
        habitLogs,
        sessions,
        goals,
        projects,
        stages,
        todayKey,
      }),
    [tasks, habits, habitLogs, sessions, goals, projects, stages, todayKey]
  );

  const ask = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || savingRef.current) return;
    setClearHistorySnapshot(null);
    setRequest(null); setChangePreview(null); setChangeError(''); setDraftOpen(false);
    const local = interpretAssistantRequest(trimmed, tasks, projects);
    if (local.type === 'blocked') {
      recordHistory({ intent: 'help', title: 'این کار را اجرا نکردم', lines: [local.message] }, trimmed);
      setQuery(''); return;
    }
    if (local.type === 'navigation') {
      recordHistory({ intent: 'help', title: `بخش ${local.label} باز شد`, lines: [] }, trimmed);
      setQuery(''); setActiveTab(local.tab); return;
    }
    if (local.type === 'change') {
      recordHistory({ intent: 'help', title: 'انتخاب مورد برای پیش‌نمایش تغییر', lines: ['هنوز تغییری ثبت نشده است.'] }, trimmed);
      setDraftOpen(false); setRequest(local); setQuery(''); return;
    }
    if (wantsTodayTaskList(trimmed)) {
      recordHistory({ intent: 'help', title: 'در انتظار جزئیات وظایف', lines: ['تا تأیید شما وظیفه‌ای ساخته نمی‌شود.'] }, trimmed);
      setDraftOpen(true);
      setDraftText('');
      setPreview(null);
      setDraftError('');
      setQuery('');
      return;
    }
    // Do not turn an unsupported write request into a misleading read-only answer.
    if (/(وظیفه|وظایف|کارها|عادت|پروژه|هدف|تنظیمات|یادداشت)/.test(trimmed) && /(بساز|ایجاد کن|اضافه کن|ثبت کن|ویرایش کن|تغییر بده|تنظیم کن)/.test(trimmed)) {
      recordHistory({ intent: 'help', title: 'این تغییر هنوز پشتیبانی نمی‌شود', lines: ['فعلاً این کار را از بخش مربوط انجام بده. هیچ داده‌ای تغییر نکرد.'] }, trimmed);
      setQuery(''); return;
    }
    const answer = runCommand(trimmed, {
      tasks,
      habits,
      habitLogs,
      goals,
      projects,
      stages,
      series,
      todayKey,
      sessions,
      lastSuggestionReason: brief.recommendation?.reason ?? null,
    });
    recordHistory(answer, trimmed);
    setQuery('');
    void triggerHaptic('light');
    if (answer.unknown) {
      pushToast({ title: 'این فرمان را نمی‌شناسم', tone: 'warning' });
    }
  };

  const previewDraft = () => {
    const titles = parseTaskLines(draftText);
    const error = validateTaskTitles(titles);
    if (error) { setDraftError(error); setPreview(null); return; }
    setDraftError('');
    setPreview({ titles, dateKey: getTodayKey() });
  };

  const saveDraft = async () => {
    if (!preview || savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const created = await createConfirmedTodayTasks(preview.titles, preview.dateKey, true);
      recordHistory({ intent: 'today.tasks', title: 'وظایف ساخته شدند', lines: [`${created.length} وظیفه برای امروز ثبت شد.`, 'بدون زمان و یادآوری؛ اولویت متوسط و دستهٔ شخصی.'], action: { label: 'دیدن وظایف', tab: 'tasks' } });
      setDraftOpen(false); setDraftText(''); setPreview(null); setDraftError('');
    } catch {
      setDraftError('ثبت انجام نشد. ممکن است تاریخ روز عوض شده باشد؛ دوباره پیش‌نمایش بگیر.');
      setPreview(null);
    } finally { savingRef.current = false; setSaving(false); }
  };

  const prepareChange = (id: string) => {
    if (!request) return;
    const row = request.kind.startsWith('task.') ? tasks.find((item) => item.id === id) : projects.find((item) => item.id === id);
    if (!row) { setChangeError('این مورد دیگر در دسترس نیست؛ دوباره درخواست بده.'); return; }
    try { setChangePreview(previewChange(request.kind, row)); setChangeError(''); }
    catch (error) { setChangeError(error instanceof Error ? error.message : 'پیش‌نمایش ساخته نشد.'); }
  };

  const confirmChange = async () => {
    if (!changePreview || savingRef.current) return;
    savingRef.current = true; setSaving(true); setChangeError('');
    try {
      const id = await applyConfirmedChange(changePreview, true);
      setLastChange(id); setRequest(null); setChangePreview(null);
      recordHistory({ intent: 'help', title: 'تغییر ثبت شد', lines: [changePreview.before.title, changePreview.description] });
    } catch (error) { setChangePreview(null); setChangeError(error instanceof Error ? error.message : 'تغییر ثبت نشد.'); }
    finally { savingRef.current = false; setSaving(false); }
  };

  const revertChange = async () => {
    if (!lastChange || savingRef.current) return;
    savingRef.current = true; setSaving(true); setChangeError('');
    try { await revertAssistantChange(lastChange); setLastChange(null); recordHistory({ intent: 'help', title: 'تغییر بازگردانده شد', lines: ['دادهٔ قبلی بازیابی شد.'] }); }
    catch (error) { setChangeError(error instanceof Error ? error.message : 'بازگشت انجام نشد.'); }
    finally { savingRef.current = false; setSaving(false); }
  };

  const clearSavedHistory = async () => {
    if (!clearHistorySnapshot || pendingHistory.length || savingRef.current) return;
    savingRef.current = true; setSaving(true);
    try {
      await deleteAssistantHistorySnapshot(clearHistorySnapshot);
      setClearHistorySnapshot(null);
      pushToast({ title: 'گفت‌وگوهای انتخاب‌شده پاک شدند', description: 'پیام‌های تازهٔ ثبت‌شده در صفحه‌ای دیگر حفظ شدند.', tone: 'success' });
    } catch {
      setClearHistorySnapshot(null);
      pushToast({ title: 'پاک‌سازی گفت‌وگو انجام نشد', description: 'تاریخچه تغییر کرده یا ذخیره‌سازی در دسترس نیست؛ دوباره بررسی کنید.', tone: 'warning' });
    } finally { savingRef.current = false; setSaving(false); }
  };

  const submit = (text: string) => {
    ask(text);
  };

  return (
    <section role="region" aria-label="مرکز فرمان" data-testid="assistant-page" className="pb-5">
      <div className="mb-4 flex items-start gap-3 rounded-[var(--r-rail)] border border-[var(--border)] bg-[var(--surface-1)] p-4">
        <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[color-mix(in_srgb,var(--accent)_18%,transparent)] text-[var(--accent-fg)]"><Sparkles size={22} aria-hidden="true" /></span>
        <div><p className="eyebrow">KAIROS ASSISTANT</p><h1 className="mt-1 text-lg font-bold text-[var(--text)]">دستیار کایروس</h1>
          <p className="mt-1 text-xs leading-6 text-[var(--text-2)]">آفلاین و مبتنی بر فرمان‌های مشخص؛ مدل زبانی هنوز نصب یا فعال نشده است. تغییرات پشتیبانی‌شده پیش از اجرا تأیید می‌خواهند.</p>
          <p className="mt-1 text-xs leading-6 text-[var(--text-2)]">پرسش‌ها و پاسخ‌های ثبت‌شده روی دستگاه و در پشتیبان ذخیره می‌شوند. پاسخ‌های گذشته تصویر همان زمان‌اند، نه وضعیت فعلی داده‌ها.</p></div>
      </div>
      <form onSubmit={(event) => { event.preventDefault(); submit(query); }} className="flex items-center gap-2 rounded-2xl border border-[var(--border)] bg-[var(--surface-2)] p-2">
        <input value={query} maxLength={4000} disabled={saving} onChange={(event) => setQuery(event.target.value)}
          placeholder="مثلاً: وظایف امروز من چیست؟" aria-label="فرمان" className="luxury-input min-w-0 flex-1 py-3 text-sm" />
        <button type="submit" disabled={saving} className="rounded-xl bg-[var(--accent)] px-4 py-3 text-xs font-bold text-[var(--accent-on)] disabled:opacity-60">بپرس</button>
      </form>
        <div className="space-y-3 py-4">
          {draftOpen && (
            <section aria-label="ساخت فهرست وظایف امروز" className="space-y-3 rounded-[var(--r-rail)] border border-[var(--accent-fg)]/35 bg-[var(--surface-1)] p-4">
              <div><h3 className="text-sm font-bold text-[var(--text)]">چه کارهایی باید انجام شوند؟</h3>
                <p className="mt-1 text-xs leading-6 text-[var(--text-2)]">عنوان هر کار را در یک خط بنویس؛ تا تأیید نکنی چیزی ذخیره نمی‌شود.</p></div>
              <label htmlFor="assistant-task-lines" className="block text-xs text-[var(--text-2)]">عنوان وظایف</label>
              <textarea id="assistant-task-lines" value={draftText} disabled={saving} rows={4} maxLength={4000}
                placeholder={'گزارش هفتگی\nتماس با همکار'}
                onChange={(event) => { setDraftText(event.target.value); setPreview(null); setDraftError(''); }}
                className="luxury-input w-full text-sm leading-6" />
              {draftError && <p role="alert" className="text-xs text-[var(--danger)]">{draftError}</p>}
              {preview && <div role="region" aria-label="پیش‌نمایش وظایف" className="rounded-xl bg-[var(--surface-2)] p-3">
                <p className="text-xs font-semibold text-[var(--text)]">پیش‌نمایش · {formatJalaaliFull(preview.dateKey).compact}</p>
                <ul className="mt-2 list-inside list-disc space-y-1 text-xs leading-6 text-[var(--text-2)]">{preview.titles.map((title) => <li key={title}>{title}</li>)}</ul>
                <p className="mt-2 text-xs leading-5 text-[var(--text-3)]">بدون ساعت و یادآوری، اولویت متوسط، دستهٔ شخصی. پیش از تأیید عنوان‌ها را تغییر بده؛ موارد مشابه قبلی حذف یا جایگزین نمی‌شوند.</p>
              </div>}
              <div className="flex flex-wrap gap-2">
                {!preview ? <button type="button" disabled={saving} onClick={previewDraft} className="btn-royal rounded-xl px-4 py-2 text-xs">پیش‌نمایش</button> :
                  <button type="button" disabled={saving} onClick={() => void saveDraft()} className="btn-royal rounded-xl px-4 py-2 text-xs">{saving ? 'در حال ثبت…' : 'تأیید و ساخت وظایف'}</button>}
                <button type="button" disabled={saving} onClick={() => { setDraftOpen(false); setPreview(null); setDraftError(''); }} className="rounded-xl border border-[var(--border)] px-4 py-2 text-xs text-[var(--text-2)]">انصراف</button>
              </div>
            </section>
          )}
          {request && (
            <section aria-label="تغییر پیشنهادی دستیار" className="space-y-3 rounded-[var(--r-rail)] border border-[var(--accent-fg)]/35 bg-[var(--surface-1)] p-4">
              {!changePreview ? <>
                <h3 className="text-sm font-bold text-[var(--text)]">{CHANGE_LABELS[request.kind]}</h3>
                <p className="text-xs font-semibold text-[var(--text)]">کدام مورد را می‌خواهی تغییر دهی؟</p>
                <p className="text-xs leading-5 text-[var(--text-2)]">یک مورد را انتخاب کن؛ قبل از ثبت، تغییر را می‌بینی.</p>
                {request.candidates.length === 0 ? <p className="text-xs text-[var(--text-3)]">موردی در داده‌های قابل‌نمایش پیدا نشد.</p> :
                  <div className="max-h-48 space-y-1 overflow-y-auto">{request.candidates.slice(0, 30).map((candidate) => <button key={candidate.id} type="button" onClick={() => prepareChange(candidate.id)} className="block w-full rounded-xl border border-[var(--border)] px-3 py-2 text-start text-xs text-[var(--text)]">{candidate.title}{candidate.sample ? ' · نمونه' : ''}</button>)}</div>}
                {request.candidates.length > 30 && <p className="text-xs text-[var(--text-3)]">۳۰ مورد اول نمایش داده شد؛ برای محدودکردن فهرست نام کامل مورد را بنویس.</p>}
              </> : <div role="region" aria-label="پیش‌نمایش تغییر" className="space-y-2 rounded-xl bg-[var(--surface-2)] p-3">
                <h3 className="text-sm font-bold text-[var(--text)]">{changePreview.before.title}{changePreview.before.sample ? ' · نمونه' : ''}</h3>
                <p className="text-xs leading-6 text-[var(--text-2)]">{changePreview.description}</p>
                <p className="text-xs text-[var(--text-3)]">تا تأیید نکنی چیزی تغییر نمی‌کند.</p>
                <button type="button" disabled={saving} onClick={() => void confirmChange()} className="btn-royal rounded-xl px-4 py-2 text-xs">{saving ? 'در حال ثبت…' : 'تأیید تغییر'}</button>
                <button type="button" disabled={saving} onClick={() => setChangePreview(null)} className="ms-2 rounded-xl border border-[var(--border)] px-4 py-2 text-xs">انتخاب دوباره</button>
              </div>}
              <button type="button" disabled={saving} onClick={() => { setRequest(null); setChangePreview(null); setChangeError(''); }} className="text-xs text-[var(--text-2)] underline">انصراف</button>
            </section>
          )}
          {changeError && <p role="alert" className="text-xs leading-5 text-[var(--danger)]">{changeError}</p>}
          {lastChange && <button type="button" disabled={saving} onClick={() => void revertChange()} className="rounded-xl border border-[var(--border)] px-3 py-2 text-xs text-[var(--accent-fg)]">بازگرداندن آخرین تغییر دستیار</button>}
          <details className="rounded-2xl border border-[var(--border)] bg-[var(--surface-1)] p-3">
            <summary className="cursor-pointer text-xs font-semibold text-[var(--text)]">دسترسی به بخش‌های کایروس</summary>
            <p className="mt-2 text-xs leading-6 text-[var(--text-2)]">برای کارهایی که دستیار هنوز مستقیماً انجام نمی‌دهد، بخش مربوط را باز کن.</p>
            <div className="mt-2 flex flex-wrap gap-2">
              {([
                ['خانه', 'dashboard'], ['وظایف', 'tasks'], ['عادت‌ها', 'habits'], ['پروژه‌ها', 'projects'],
                ['تقویم', 'calendar'], ['تمرکز', 'focus'], ['تحلیل عملکرد', 'analytics'], ['انگیزه', 'motivation'], ['تنظیمات', 'settings'],
              ] as [string, NavTab][]).map(([label, tab]) => <button key={tab} type="button" onClick={() => setActiveTab(tab)} className="rounded-xl border border-[var(--border)] px-3 py-2 text-xs text-[var(--accent-fg)]">{label}</button>)}
            </div>
          </details>
          {/* The manager gives a decision and its evidence. */}
          <div
            data-testid="manager-brief"
            className="rounded-[var(--r-rail)] border border-[var(--accent-fg)]/25 bg-[color-mix(in_srgb,var(--accent)_10%,transparent)] p-3"
          >
            <p className="mb-1 text-[10px] font-bold text-[var(--accent-fg)]">پیشنهاد مربی · بر اساس داده‌های ثبت‌شده</p>
            <p className="text-[11.5px] font-semibold leading-5 text-[var(--text)]">
              {brief.headline}
            </p>
            <ul className="mt-1 space-y-1">
              {brief.lines.slice(0, 3).map((line) => (
                <li key={line} className="text-[10px] leading-4 text-[var(--text-2)]">
                  {line}
                </li>
              ))}
            </ul>
            {brief.recommendation && (
              <div className="mt-2 border-t border-[var(--accent-fg)]/20 pt-2">
                <p className="text-[11px] font-medium text-[var(--accent-fg)]">
                  {brief.recommendation.text}
                </p>
                <p className="mt-0.5 text-[9.5px] leading-4 text-[var(--text-3)]">
                  {brief.recommendation.reason} · پیشنهاد بر پایهٔ قواعد محلی است، نه پیش‌بینی قطعی.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setActiveTab(brief.recommendation!.tab);
                  }}
                  className="mt-1 text-[10px] font-medium text-[var(--accent-fg)]"
                >
                  انجامش بده ←
                </button>
              </div>
            )}
          </div>

          <div className="flex flex-wrap gap-1.5">
            {['وظیفه را تکمیل کن', 'وظیفه را به فردا منتقل کن', 'پروژه را متوقف کن', 'برو به تنظیمات'].map((example) => (
              <button key={example} type="button" onClick={() => submit(example)} className="rounded-full border border-[var(--accent-fg)]/25 px-2.5 py-1 text-[10.5px] text-[var(--accent-fg)]">{example}</button>
            ))}
            {QUICK_COMMANDS.map((command) => (
              <button
                key={command}
                type="button"
                onClick={() => submit(command)}
                className="rounded-full border border-[var(--border)] bg-[var(--surface-1)] px-2.5 py-1 text-[10.5px] text-[var(--text-2)] transition-colors active:border-[var(--accent-fg)]/45 active:text-[var(--accent-fg)]"
              >
                {command}
              </button>
            ))}
          </div>

          {pendingHistory.some((item) => item.failed) && <div role="alert" className="rounded-xl border border-[var(--danger)]/40 p-3 text-xs leading-6 text-[var(--text)]">
            بخشی از گفت‌وگو ذخیره نشده و با بستن صفحه از دست می‌رود.
            <button type="button" className="ms-2 underline" onClick={() => {
              const failed = pendingHistory.filter((item) => item.failed);
              setPendingHistory((rows) => rows.map((row) => row.failed ? { ...row, failed: false } : row));
              failed.forEach(({ entry }) => persistHistory(entry));
            }}>تلاش دوباره برای ذخیره</button>
          </div>}
          {savedHistory.length > 0 && <div className="text-xs text-[var(--text-2)]">
            {clearHistorySnapshot ? <div role="region" aria-label="تأیید پاک‌کردن گفت‌وگو" className="space-y-2">
              <p>پاک‌کردن {clearHistorySnapshot.length} گفت‌وگوی ذخیره‌شده، شامل موارد نمونهٔ پنهان؟ فایل‌های پشتیبان قبلی پاک نمی‌شوند.</p>
              <button type="button" disabled={saving || pendingHistory.length > 0} onClick={() => void clearSavedHistory()} className="btn-royal rounded-xl px-3 py-2">تأیید پاک‌کردن گفت‌وگو</button>
              <button type="button" disabled={saving} onClick={() => setClearHistorySnapshot(null)} className="ms-2 rounded-xl border border-[var(--border)] px-3 py-2">انصراف</button>
            </div> : <button type="button" disabled={saving || pendingHistory.length > 0} onClick={() => setClearHistorySnapshot([...savedHistory])} className="rounded-xl border border-[var(--border)] px-3 py-2 disabled:opacity-50">پاک‌کردن تاریخچهٔ گفت‌وگو</button>}
          </div>}
          {history.length === 0 ? (
            <div className="rounded-[var(--r-rail)] border border-[var(--border)] bg-[var(--surface-1)] p-3">
              <p className="text-[11.5px] font-semibold text-[var(--text)]">
                <Brain className="me-1 inline h-3.5 w-3.5 text-[var(--accent-fg)]" />
                فقط چیزهایی را می‌گویم که در داده‌ات هست
              </p>
              <p className="mt-1 text-[10.5px] leading-5 text-[var(--text-3)]">
                اگر جمله‌ای را نفهمم، صریح می‌گویم. عدد از خودم نمی‌سازم و هیچ‌چیز از گوشی بیرون نمی‌رود.
              </p>
            </div>
          ) : (
            history.slice(0, showHistoryCount).map((answer) => (
              <motion.div
                key={answer.id}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                className={`rounded-[var(--r-rail)] border p-3 ${
                  answer.unknown
                    ? 'border-[var(--danger)]/40 bg-[color-mix(in_srgb,var(--danger)_10%,transparent)]'
                    : 'border-[var(--border)] bg-[var(--surface-1)]'
                }`}
              >
                {answer.prompt && <p className="mb-2 text-[10.5px] text-[var(--text-3)]">تو: {answer.prompt}</p>}
                <p className="text-[11.5px] font-semibold text-[var(--text)]">{answer.title}</p>
                <ul className="mt-1.5 space-y-1">
                  {answer.lines.map((line) => (
                    <li key={line} className="text-[10.5px] leading-5 text-[var(--text-2)]">
                      {line}
                    </li>
                  ))}
                </ul>
                {answer.action && (
                  <button
                    type="button"
                    onClick={() => {
                      setActiveTab(answer.action!.tab);
                      }}
                    className="mt-2 text-[10.5px] font-medium text-[var(--accent-fg)]"
                  >
                    {answer.action.label} ←
                  </button>
                )}
              </motion.div>
            ))
          )}
          {history.length > showHistoryCount && <button type="button" onClick={() => setShowHistoryCount((count) => count + 30)} className="rounded-xl border border-[var(--border)] px-3 py-2 text-xs text-[var(--accent-fg)]">نمایش گفت‌وگوهای قدیمی‌تر</button>}
        </div>
    </section>
  );
}
