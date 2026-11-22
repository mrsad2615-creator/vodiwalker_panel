import { useMemo, useState } from 'react';
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
import { QUICK_COMMANDS, runCommand, type CommandAnswer } from '@/lib/commands';
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

export function CommandCenter() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [history, setHistory] = useState<CommandAnswer[]>([]);

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
      lastSuggestionReason: topInsightReason,
    });
    setHistory((current) => [answer, ...current].slice(0, 6));
    setQuery('');
    void triggerHaptic('light');
    if (answer.unknown) {
      pushToast({ title: 'این فرمان را نمی‌شناسم', tone: 'warning' });
    }
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
                if (event.key === 'Enter') ask(query);
              }}
              placeholder="مثلاً: وظایف امروز من چیست؟"
              aria-label="فرمان"
              className="luxury-input flex-1 py-2 text-[11.5px]"
            />
            <button
              type="button"
              onClick={() => ask(query)}
              className="rounded-full bg-[var(--accent)] px-4 py-2 text-[11.5px] font-bold text-[var(--accent-on)]"
            >
              بپرس
            </button>
          </div>
        }
      >
        <div className="space-y-3 px-5 py-4">
          <div className="flex flex-wrap gap-1.5">
            {QUICK_COMMANDS.map((command) => (
              <button
                key={command}
                type="button"
                onClick={() => ask(command)}
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
