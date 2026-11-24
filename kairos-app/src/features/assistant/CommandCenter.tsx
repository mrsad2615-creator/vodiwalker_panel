import { useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Brain, Sparkles } from '@/components/ui/icons';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { useApp } from '@/context/AppContext';
import {
  useGoals,
  useHabitLogs,
  useHabits,
  useJournal,
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
import { pickInsights } from '@/lib/insights';
import { triggerHaptic } from '@/lib/native';

/**
 * The command centre: a Persian question box with no model behind it.
 *
 * Why this is better than a chat window with an LLM: every answer here is
 * produced by `lib/commands.ts` from the user's own rows, so it can always be
 * checked, and a question outside its vocabulary returns «این فرمان را
 * نمیشناسم» with the list of what it does understand. Nothing is invented, and
 * nothing leaves the device.
 */

const CHANGE_LABELS = {
  'task.complete': 'تکمیل وظیفه', 'task.today': 'انتقال موعد به امروز', 'task.tomorrow': 'انتقال موعد به فردا',
  'task.high': 'اولویت بالای وظیفه', 'project.pause': 'مکث پروژه', 'project.resume': 'فعال‌کردن پروژه',
} as const;

export function CommandCenter() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [history, setHistory] = useState<(CommandAnswer & { prompt?: string })[]>([]);
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
  const journal = useJournal();
  const { setActiveTab, pushToast } = useApp();

  const todayKey = getTodayKey();
  const series = useMemo(
    () => buildDailySeries({ focusSessions: sessions, tasks, habitLogs }, todayKey, 14),
    [sessions, tasks, habitLogs, todayKey]
  );

  // The reason of the strongest open insight — what "چرا؟" is allowed to quote.
  const topInsightReason = useMemo(() => {
    const insights = pickInsights({
      sessions,
      tasks,
      habits,
      habitLogs,
      journal,
      goals,
      todayKey,
    });
    return insights[0] ? `${insights[0].title}. ${insights[0].body}` : null;
  }, [sessions, tasks, habits, habitLogs, journal, goals, todayKey]);

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
    setRequest(null); setChangePreview(null); setChangeError(''); setDraftOpen(false);
    const local = interpretAssistantRequest(trimmed, tasks, projects);
    if (local.type === 'blocked') {
      setHistory((current) => [{ intent: 'help' as const, title: 'این کار را اجرا نکردم', lines: [local.message], prompt: trimmed }, ...current].slice(0, 6));
      setQuery(''); return;
    }
    if (local.type === 'navigation') {
      setHistory((current) => [{ intent: 'help' as const, title: `بخش ${local.label} باز شد`, lines: [], prompt: trimmed }, ...current].slice(0, 6));
      setQuery(''); setActiveTab(local.tab); setOpen(false); return;
    }
    if (local.type === 'change') {
      setDraftOpen(false); setRequest(local); setQuery(''); return;
    }
    if (wantsTodayTaskList(trimmed)) {
      setDraftOpen(true);
      setDraftText('');
      setPreview(null);
      setDraftError('');
      setQuery('');
      return;
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
      lastSuggestionReason: topInsightReason,
    });
    setHistory((current) => [{ ...answer, prompt: trimmed }, ...current].slice(0, 6));
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
      setHistory((current) => [{ intent: 'today.tasks', title: 'وظایف ساخته شدند', lines: [`${created.length} وظیفه برای امروز ثبت شد.`, 'بدون زمان و یادآوری؛ اولویت متوسط و دستهٔ شخصی.'], action: { label: 'دیدن وظایف', tab: 'tasks' } } as CommandAnswer, ...current].slice(0, 6));
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
      setHistory((current) => [{ intent: 'help' as const, title: 'تغییر ثبت شد', lines: [changePreview.before.title, changePreview.description] }, ...current].slice(0, 6));
    } catch (error) { setChangePreview(null); setChangeError(error instanceof Error ? error.message : 'تغییر ثبت نشد.'); }
    finally { savingRef.current = false; setSaving(false); }
  };

  const revertChange = async () => {
    if (!lastChange || savingRef.current) return;
    savingRef.current = true; setSaving(true); setChangeError('');
    try { await revertAssistantChange(lastChange); setLastChange(null); setHistory((current) => [{ intent: 'help' as const, title: 'تغییر بازگردانده شد', lines: ['دادهٔ قبلی بازیابی شد.'] }, ...current].slice(0, 6)); }
    catch (error) { setChangeError(error instanceof Error ? error.message : 'بازگشت انجام نشد.'); }
    finally { savingRef.current = false; setSaving(false); }
  };

  const submit = (text: string) => {
    ask(text);
  };

  return (
    <>
      <motion.button
        type="button"
        onClick={() => {
          setOpen(true);
          void triggerHaptic('light');
        }}
        aria-label="مرکز فرمان"
        whileTap={{ scale: 0.94 }}
        className="fixed bottom-[calc(env(safe-area-inset-bottom,0px)+86px)] end-4 z-30 flex items-center gap-1.5 rounded-full border border-[var(--accent-fg)]/35 bg-[color-mix(in_srgb,var(--surface-2)_86%,transparent)] px-3.5 py-2.5 text-[11px] font-semibold text-[var(--accent-fg)] shadow-[0_10px_30px_-14px_rgba(0,0,0,0.9)] backdrop-blur-xl"
      >
        <Sparkles className="h-3.5 w-3.5" />
        بپرس
      </motion.button>

      <ModalSheet
        open={open}
        onClose={() => setOpen(false)}
        title="مرکز فرمان"
        subtitle="بدون اینترنت · بدون مدل زبانی · از دادهٔ خودت"
        footer={
          <div className="flex items-center gap-2 px-5 pb-1">
            <input
              value={query}
              disabled={saving}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') submit(query);
              }}
              placeholder="مثلاً: وظایف امروز من چیست؟"
              aria-label="فرمان"
              className="luxury-input flex-1 py-2 text-[11.5px]"
            />
            <button
              type="button"
              onClick={() => submit(query)}
              disabled={saving}
              className="rounded-full bg-[var(--accent)] px-4 py-2 text-[11.5px] font-bold text-[var(--accent-on)] disabled:opacity-60"
            >
              بپرس
            </button>
          </div>
        }
      >
        <div className="space-y-3 px-5 py-4">
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
          {/* The manager gives a decision and its evidence. */}
          <div
            data-testid="manager-brief"
            className="rounded-[var(--r-rail)] border border-[var(--accent-fg)]/25 bg-[color-mix(in_srgb,var(--accent)_10%,transparent)] p-3"
          >
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
                    setOpen(false);
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
            history.map((answer, index) => (
              <motion.div
                key={`${answer.intent}-${index}-${answer.title}`}
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
                      setOpen(false);
                    }}
                    className="mt-2 text-[10.5px] font-medium text-[var(--accent-fg)]"
                  >
                    {answer.action.label} ←
                  </button>
                )}
              </motion.div>
            ))
          )}
        </div>
      </ModalSheet>
    </>
  );
}
