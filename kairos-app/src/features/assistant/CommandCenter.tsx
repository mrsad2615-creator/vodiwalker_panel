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
import { getTodayKey } from '@/lib/date';
import { confidenceLabel, QUICK_COMMANDS, runCommand, type CommandAnswer } from '@/lib/commands';
import { buildDailyBrief } from '@/lib/coach';
import { pickInsights } from '@/lib/insights';
import { triggerHaptic } from '@/lib/native';
import { isAiReady, runAiTurn, type ChatMessage } from '@/lib/ai/client';
import type { ToolCall } from '@/lib/ai/tools';

/**
 * The command centre: a Persian question box with no model behind it.
 *
 * Why this is better than a chat window with an LLM: every answer here is
 * produced by `lib/commands.ts` from the user's own rows, so it can always be
 * checked, and a question outside its vocabulary returns «این فرمان را
 * نمیشناسم» with the list of what it does understand. Nothing is invented, and
 * nothing leaves the device.
 */

export function CommandCenter() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [history, setHistory] = useState<CommandAnswer[]>([]);
  /**
   * Two brains, one box. «آفلاین» is the rule engine: instant, provable, and it
   * says "I don't know" out loud. «ابر» is the optional cloud manager that can
   * call the app's own tools. The switch only exists when a key is configured.
   */
  const [mode, setMode] = useState<'local' | 'cloud'>('local');
  const [chat, setChat] = useState<{ role: 'user' | 'assistant'; text: string }[]>([]);
  const [chatHistory, setChatHistory] = useState<ChatMessage[]>([]);
  const [thinking, setThinking] = useState(false);
  const [pendingCall, setPendingCall] = useState<ToolCall | null>(null);
  const pendingResolver = useRef<((allowed: boolean) => void) | null>(null);

  const tasks = useTasks();
  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const goals = useGoals();
  const projects = useProjects();
  const stages = useProjectStages();
  const sessions = useFocusSessions();
  const journal = useJournal();
  const { setActiveTab, pushToast, settings } = useApp();

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
    if (!trimmed) return;
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
    setHistory((current) => [answer, ...current].slice(0, 6));
    setQuery('');
    void triggerHaptic('light');
    if (answer.unknown) {
      pushToast({ title: 'این فرمان را نمی‌شناسم', tone: 'warning' });
    }
  };

  const answerPending = (allowed: boolean) => {
    const resolve = pendingResolver.current;
    pendingResolver.current = null;
    setPendingCall(null);
    resolve?.(allowed);
  };

  const askCloud = async (text: string) => {
    setThinking(true);
    setChat((current) => [...current, { role: 'user', text }]);
    const result = await runAiTurn({
      settings,
      history: chatHistory,
      userText: text,
      confirmWrite: settings.aiAutoApply
        ? undefined
        : (call) =>
            new Promise<boolean>((resolve) => {
              pendingResolver.current = resolve;
              setPendingCall(call);
            }),
    });
    setThinking(false);

    if (!result.ok) {
      const message = result.error ?? 'ارتباط با مدیر ابری برقرار نشد.';
      // Never leave the person with an error alone: the offline engine answers
      // the same question where it can, and says so on the card.
      const localAnswer = runCommand(text, {
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
      const fallback = localAnswer.unknown
        ? 'این را هم نمی‌دانم.'
        : `${localAnswer.title}\n${localAnswer.lines.join('\n')}`;
      setChat((current) => [
        ...current,
        { role: 'assistant', text: `${message}\n\nجواب آفلاین:\n${fallback}` },
      ]);
      pushToast({ title: 'مدیر ابری پاسخ نداد', description: message, tone: 'warning' });
      return;
    }

    setChatHistory((current) => [
      ...current,
      { role: 'user', content: text },
      { role: 'assistant', content: result.reply },
    ]);
    const stepsLine = result.steps.length
      ? `\n${result.steps.map((step) => `${step.ok ? '✓' : '×'} ${step.summary}`).join('\n')}`
      : '';
    setChat((current) => [...current, { role: 'assistant', text: `${result.reply}${stepsLine}` }]);
    if (result.navigateTo) setActiveTab(result.navigateTo as never);
    void triggerHaptic('light');
  };

  const submit = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed || thinking) return;
    if (mode === 'cloud') {
      void askCloud(trimmed);
    } else {
      ask(trimmed);
    }
    setQuery('');
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
              disabled={thinking}
              className="rounded-full bg-[var(--accent)] px-4 py-2 text-[11.5px] font-bold text-[var(--accent-on)] disabled:opacity-60"
            >
              {thinking ? '…' : 'بپرس'}
            </button>
          </div>
        }
      >
        <div className="space-y-3 px-5 py-4">
          {/* The manager speaks first: one decision, its reason, its confidence. */}
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
                  {brief.recommendation.reason} · اطمینان{' '}
                  {confidenceLabel(brief.recommendation.confidence)}
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

          {isAiReady(settings) ? (
            <div className="flex items-center gap-1.5" data-testid="brain-switch">
              {(['local', 'cloud'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  aria-pressed={mode === option}
                  onClick={() => setMode(option)}
                  className={`rounded-full px-3 py-1 text-[10.5px] font-medium transition-colors ${
                    mode === option
                      ? 'bg-[var(--accent)] text-[var(--accent-on)]'
                      : 'border border-[var(--border)] text-[var(--text-2)]'
                  }`}
                >
                  {option === 'local' ? 'آفلاین' : 'ابر'}
                </button>
              ))}
              <span className="text-[9.5px] text-[var(--text-3)]">
                {mode === 'local'
                  ? 'موتور قانون: سریع، از دادهٔ خودت، بدون اینترنت'
                  : 'مدیر ابری: گفت‌وگوی آزاد و فرمان روی برنامه'}
              </span>
            </div>
          ) : settings.aiEnabled ? (
            <p className="rounded-[var(--r-field)] border border-[var(--border)] px-2.5 py-1.5 text-[10px] text-[var(--text-3)]">
              مدیر ابری روشن است ولی کامل نیست؛ کلید و نام مدل را در تنظیمات پر کن.
            </p>
          ) : null}

          <div className="flex flex-wrap gap-1.5">
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

          {mode === 'cloud' ? (
            <div className="space-y-2" data-testid="cloud-chat">
              {chat.length === 0 && (
                <p className="rounded-[var(--r-rail)] border border-[var(--border)] bg-[var(--surface-1)] p-3 text-[10.5px] leading-5 text-[var(--text-3)]">
                  با مدیر حرف بزن؛ می‌تواند کار بسازد، پروژه و مرحله اضافه کند، عادت ثبت کند و تنظیمات را
                  عوض کند — همه از همان مسیر معمولی برنامه، با ثبت در دفتر و برگشت‌پذیر.
                </p>
              )}
              {chat.map((entry, index) => (
                <div
                  key={`${entry.role}-${index}`}
                  className={`w-fit max-w-[92%] whitespace-pre-line rounded-[var(--r-rail)] px-3 py-2 text-[11px] leading-5 ${
                    entry.role === 'user'
                      ? 'ms-auto bg-[var(--accent)] text-[var(--accent-on)]'
                      : 'border border-[var(--border)] bg-[var(--surface-1)] text-[var(--text)]'
                  }`}
                >
                  {entry.text}
                </div>
              ))}
              {thinking && (
                <p className="text-[10px] text-[var(--text-3)]">مدیر دارد فکر می‌کند…</p>
              )}
              {pendingCall && (
                <div className="rounded-[var(--r-rail)] border border-[var(--accent-fg)]/40 bg-[color-mix(in_srgb,var(--accent)_12%,transparent)] p-2.5">
                  <p className="text-[11px] font-semibold text-[var(--text)]">
                    مدیر می‌خواهد این تغییر را بدهد:
                  </p>
                  <p className="num-geo mt-1 text-[10px] leading-4 text-[var(--text-2)]">
                    {pendingCall.name} · {JSON.stringify(pendingCall.args).slice(0, 160)}
                  </p>
                  <div className="mt-2 flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => answerPending(true)}
                      className="rounded-full bg-[var(--accent)] px-3 py-1 text-[10.5px] font-bold text-[var(--accent-on)]"
                    >
                      اجرا کن
                    </button>
                    <button
                      type="button"
                      onClick={() => answerPending(false)}
                      className="rounded-full border border-[var(--border)] px-3 py-1 text-[10.5px] text-[var(--text-2)]"
                    >
                      رد
                    </button>
                  </div>
                </div>
              )}
            </div>
          ) : history.length === 0 ? (
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
