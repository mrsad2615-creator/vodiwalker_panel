import type { ReactNode } from 'react';
import { ArrowUpLeft, Check, Flame, Timer } from '@/components/ui/icons';
import { Numeric } from '@/components/ui/Numeric';
import { HabitIcon } from '@/components/ui/KairosMark';
import { getJalaaliMonthName, dateKeyToJalaali } from '@/lib/date';
import { formatDurationShort } from '@/lib/format';
import type {
  DailyPoint,
  HabitMatrix,
  TaskBreakdown,
  WeekComparison,
} from '@/lib/homeMetrics';

/* ---------------------------------------------------------------------------
   Showcase shell
   --------------------------------------------------------------------------- */

interface ShowcaseCardProps {
  eyebrow: string;
  title: string;
  meta?: ReactNode;
  action?: ReactNode;
  className?: string;
  dense?: boolean;
  children: ReactNode;
}

function ShowcaseCard({
  eyebrow,
  title,
  meta,
  action,
  className = '',
  dense = false,
  children,
}: ShowcaseCardProps) {
  return (
    <article className={`rail ${dense ? 'p-3' : 'p-3.5'} ${className}`}>
      <header className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <span className="eyebrow block leading-none">{eyebrow}</span>
          <div className="mt-1 flex items-baseline gap-1.5">
            <h2 className="truncate text-[12.5px] font-semibold text-[var(--text)]">{title}</h2>
            {meta && <span className="shrink-0 text-[9.5px] text-[var(--text-3)]">{meta}</span>}
          </div>
        </div>
        {action && <div className="shrink-0">{action}</div>}
      </header>
      <div className="mt-2.5">{children}</div>
    </article>
  );
}

/* ---------------------------------------------------------------------------
   Focus — a ring of today's real minutes and the last seven days behind it
   --------------------------------------------------------------------------- */

export function FocusShowcase({
  minutes,
  goalMinutes,
  series,
  onStart,
  dense = false,
}: {
  minutes: number;
  goalMinutes: number;
  series: DailyPoint[];
  onStart: () => void;
  dense?: boolean;
}) {
  const pct = Math.min(100, Math.round((minutes / Math.max(1, goalMinutes)) * 100));
  const R = 30;
  const C = 2 * Math.PI * R;
  const week = series.slice(-7);
  const max = Math.max(1, ...week.map((d) => d.focusMinutes));

  return (
    <ShowcaseCard
      eyebrow="FOCUS"
      title="تمرکز امروز"
      meta={`هدف ${goalMinutes} دقیقه`}
      dense={dense}
      action={
        <button
          type="button"
          onClick={onStart}
          className="flex items-center gap-1 rounded-full border border-[var(--accent-fg)]/40 px-2.5 py-1 text-[10px] font-semibold text-[var(--accent-fg)]"
        >
          <Timer className="h-3 w-3" />
          شروع
        </button>
      }
    >
      <div className="flex items-center gap-3">
        <div className="relative h-[74px] w-[74px] shrink-0">
          <svg viewBox="0 0 74 74" className="h-full w-full -rotate-90">
            <circle cx="37" cy="37" r={R} fill="none" stroke="var(--border-strong)" strokeWidth="3" />
            <circle
              cx="37"
              cy="37"
              r={R}
              fill="none"
              stroke="var(--accent)"
              strokeWidth="3"
              strokeLinecap="round"
              strokeDasharray={C}
              strokeDashoffset={C * (1 - pct / 100)}
              style={{ transition: 'stroke-dashoffset 600ms cubic-bezier(0.22,1,0.36,1)' }}
            />
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center">
            <Numeric
              value={formatDurationShort(minutes)}
              variant="display"
              className="text-[15px] font-bold text-[var(--text)]"
            />
            <Numeric value={pct} suffix="%" className="text-[8.5px] text-[var(--text-3)]" />
          </div>
        </div>

        <div className="min-w-0 flex-1">
          {dense ? (
            <p className="text-[10px] leading-4 text-[var(--text-3)]">
              <Numeric value={week.filter((d) => d.focusMinutes > 0).length} variant="geo" /> روز
              فعال در هفتهٔ گذشته
            </p>
          ) : (
            <>
              <div className="flex h-[38px] items-end gap-[3px]">
                {week.map((day) => {
                  const h = Math.max(6, Math.round((day.focusMinutes / max) * 100));
                  const isToday = day === week[week.length - 1];
                  return (
                    <span
                      key={day.dateKey}
                      title={`${day.dateKey} · ${day.focusMinutes} دقیقه`}
                      className="flex-1 rounded-[3px]"
                      style={{
                        height: `${day.focusMinutes === 0 ? 6 : h}%`,
                        background:
                          day.focusMinutes === 0
                            ? 'var(--border-strong)'
                            : isToday
                            ? 'var(--accent)'
                            : 'color-mix(in srgb, var(--accent-fg) 45%, transparent)',
                      }}
                    />
                  );
                })}
              </div>
              <p className="mt-1.5 text-[9px] text-[var(--text-3)]">۷ روز گذشته · دقیقه</p>
            </>
          )}
        </div>
      </div>
    </ShowcaseCard>
  );
}

/* ---------------------------------------------------------------------------
   Tasks — one honest stacked bar plus the three next things
   --------------------------------------------------------------------------- */

export function TasksShowcase({
  breakdown,
  nextTasks,
  onToggle,
  onOpenTask,
  onOpenAll,
  dense = false,
}: {
  breakdown: TaskBreakdown;
  nextTasks: { id: string; title: string; dueTime: string | null }[];
  onToggle: (id: string) => void;
  onOpenTask: (id: string) => void;
  onOpenAll: () => void;
  dense?: boolean;
}) {
  const total = Math.max(1, breakdown.total);
  const segments = [
    { id: 'done', value: breakdown.completed, color: 'var(--accent)' },
    { id: 'doing', value: breakdown.inProgress, color: 'color-mix(in srgb, var(--accent) 55%, transparent)' },
    { id: 'todo', value: breakdown.todo, color: 'var(--border-strong)' },
  ];

  return (
    <ShowcaseCard
      eyebrow="PLANNER"
      title="وظایف"
      meta={`${breakdown.total} فعال`}
      dense={dense}
      action={
        <button
          type="button"
          onClick={onOpenAll}
          aria-label="همهٔ وظایف"
          className="text-[10px] font-medium text-[var(--accent-fg)]"
        >
          همه
        </button>
      }
    >
      <div className="flex h-1.5 w-full overflow-hidden rounded-full bg-[var(--surface-input)]">
        {segments.map((s) => (
          <span
            key={s.id}
            style={{ width: `${(s.value / total) * 100}%`, background: s.color }}
            aria-hidden="true"
          />
        ))}
      </div>

      <div className="mt-2 flex items-center justify-between text-[9.5px] text-[var(--text-3)]">
        <span className="flex items-center gap-1">
          <span className="h-1.5 w-1.5 rounded-full bg-[var(--accent)]" />
          <Numeric value={breakdown.completed} variant="geo" className="text-[var(--text-2)]" />
          انجام‌شده
        </span>
        <span className="flex items-center gap-1">
          <span className="h-1.5 w-1.5 rounded-full bg-[color-mix(in_srgb,var(--accent)_55%,transparent)]" />
          <Numeric value={breakdown.inProgress} variant="geo" className="text-[var(--text-2)]" />
          در جریان
        </span>
        <span className="flex items-center gap-1">
          <span className="h-1.5 w-1.5 rounded-full bg-[var(--border-strong)]" />
          <Numeric value={breakdown.todo} variant="geo" className="text-[var(--text-2)]" />
          باقی
        </span>
      </div>

      {!dense && nextTasks.length > 0 && (
        <ul className="mt-2.5 space-y-1.5 border-t border-[var(--border)] pt-2">
          {nextTasks.slice(0, 3).map((task) => (
            <li key={task.id} className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => onToggle(task.id)}
                aria-label={`تکمیل ${task.title}`}
                className="grid h-4 w-4 shrink-0 place-items-center rounded-[4px] border border-white/20 text-transparent hover:border-[var(--accent-fg)]"
              >
                <Check className="h-2.5 w-2.5" strokeWidth={3} />
              </button>
              <button
                type="button"
                onClick={() => onOpenTask(task.id)}
                className="min-w-0 flex-1 truncate text-start text-[11px] text-[var(--text)]"
              >
                {task.title}
              </button>
              {task.dueTime && (
                <Numeric value={task.dueTime} variant="geo" className="text-[9px] text-[var(--text-3)]" />
              )}
            </li>
          ))}
        </ul>
      )}
    </ShowcaseCard>
  );
}

/* ---------------------------------------------------------------------------
   Habits — the seven-day matrix (the shape the old app never had)
   --------------------------------------------------------------------------- */

const CELL_STYLE: Record<string, { bg: string; border: string }> = {
  done: { bg: 'var(--accent)', border: 'transparent' },
  partial: { bg: 'color-mix(in srgb, var(--accent) 38%, transparent)', border: 'transparent' },
  missed: { bg: 'transparent', border: 'color-mix(in srgb, var(--danger) 55%, transparent)' },
  empty: { bg: 'var(--surface-input)', border: 'var(--border)' },
};

export function HabitsShowcase({
  matrix,
  onOpenAll,
  onStart,
  dense = false,
}: {
  matrix: HabitMatrix;
  onOpenAll: () => void;
  onStart: () => void;
  dense?: boolean;
}) {
  const rows = matrix.rows.slice(0, dense ? 3 : 5);
  const hidden = matrix.rows.length - rows.length;

  return (
    <ShowcaseCard
      eyebrow="RITUALS"
      title="عادت‌ها"
      meta={`${matrix.density}% این هفته`}
      dense={dense}
      action={
        <button
          type="button"
          onClick={onOpenAll}
          className="text-[10px] font-medium text-[var(--accent-fg)]"
        >
          همه
        </button>
      }
    >
      {matrix.rows.length === 0 ? (
        <button type="button" onClick={onStart} className="text-[10.5px] text-[var(--text-3)]">
          هنوز عادتی نساخته‌ای؛ اولین عادت را بساز.
        </button>
      ) : (
        <>
          <div className="flex items-center gap-2">
            <div className="min-w-0 flex-1">
              {rows.map((row) => (
                <div key={row.habitId} className="flex items-center gap-1.5 py-[3px]">
                  <HabitIcon
                    name={row.icon}
                    className="h-3 w-3 shrink-0 text-[var(--text-3)]"
                    color={row.accentColor}
                  />
                  <span className="min-w-0 flex-1 truncate text-[10px] text-[var(--text-2)]">
                    {row.name}
                  </span>
                  <div className="flex shrink-0 items-center gap-[3px]">
                    {row.cells.map((cell, index) => {
                      const isToday = index === row.cells.length - 1;
                      return (
                        <span
                          key={`${row.habitId}-${index}`}
                          data-testid="habit-cell"
                          className="h-[13px] w-[13px] rounded-[3px] border"
                          style={{
                            background: CELL_STYLE[cell].bg,
                            borderColor: isToday
                              ? 'var(--accent-fg)'
                              : CELL_STYLE[cell].border,
                          }}
                          aria-hidden="true"
                        />
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="mt-2 flex items-center justify-between border-t border-[var(--border)] pt-1.5 text-[9px] text-[var(--text-3)]">
            <span className="flex items-center gap-1">
              <Flame className="h-2.5 w-2.5 text-[var(--accent-fg)]" />
              بهترین زنجیره
              <Numeric value={matrix.bestStreak} variant="geo" className="text-[var(--text-2)]" />
              روز
            </span>
            {hidden > 0 && <span>و {hidden} عادت دیگر</span>}
          </div>
        </>
      )}
    </ShowcaseCard>
  );
}

/* ---------------------------------------------------------------------------
   Analytics — a real sparkline of the activity index + week-over-week delta
   --------------------------------------------------------------------------- */

export function AnalyticsShowcase({
  series,
  comparison,
  onOpen,
  dense = false,
}: {
  series: DailyPoint[];
  comparison: WeekComparison;
  onOpen: () => void;
  dense?: boolean;
}) {
  const points = series.slice(-14);
  const max = Math.max(1, ...points.map((p) => p.activity));
  const w = 100;
  const h = 30;
  const step = points.length > 1 ? w / (points.length - 1) : w;
  const line = points
    .map((p, i) => `${(i * step).toFixed(2)},${(h - (p.activity / max) * (h - 4) - 2).toFixed(2)}`)
    .join(' ');
  const area = `0,${h} ${line} ${w},${h}`;
  const delta = comparison.deltaPercent;

  return (
    <ShowcaseCard
      eyebrow="INSIGHT"
      title="روند دو هفته"
      meta={delta === null ? undefined : `${delta > 0 ? '+' : ''}${delta}%`}
      dense={dense}
      action={
        <button
          type="button"
          onClick={onOpen}
          aria-label="تحلیل کامل"
          className="grid h-6 w-6 place-items-center rounded-full border border-[var(--border)] text-[var(--text-2)]"
        >
          <ArrowUpLeft className="h-3 w-3" />
        </button>
      }
    >
      <svg viewBox={`0 0 ${w} ${h}`} preserveAspectRatio="none" className="h-[46px] w-full">
        <defs>
          <linearGradient id="kairos-spark" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.42" />
            <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
          </linearGradient>
        </defs>
        <polygon points={area} fill="url(#kairos-spark)" />
        <polyline
          points={line}
          fill="none"
          stroke="var(--accent-fg)"
          strokeWidth="1.4"
          strokeLinejoin="round"
          strokeLinecap="round"
          vectorEffect="non-scaling-stroke"
        />
      </svg>

      <div className="mt-1.5 grid grid-cols-3 gap-2 text-center">
        {[
          { label: 'تمرکز', value: formatDurationShort(comparison.focusMinutes) },
          { label: 'وظیفه', value: String(comparison.tasksDone) },
          { label: 'عادت', value: String(comparison.habitsDone) },
        ].map((stat) => (
          <div key={stat.label}>
            <Numeric
              value={stat.value}
              variant="geo"
              className="block text-[12px] font-bold text-[var(--text)]"
            />
            <span className="mt-0.5 block text-[8.5px] text-[var(--text-3)]">{stat.label}</span>
          </div>
        ))}
      </div>
    </ShowcaseCard>
  );
}

/* ---------------------------------------------------------------------------
   Calendar — the next five days, with their real load
   --------------------------------------------------------------------------- */

export function CalendarShowcase({
  upcoming,
  onOpen,
  dense = false,
}: {
  upcoming: { dateKey: string; tasks: { id: string; title: string }[] }[];
  onOpen: (dateKey: string) => void;
  dense?: boolean;
}) {
  return (
    <ShowcaseCard
      eyebrow="CALENDAR"
      title="پنج روز پیش‌رو"
      meta={`${upcoming.reduce((a, d) => a + d.tasks.length, 0)} وظیفه`}
      dense={dense}
    >
      <div className="grid grid-cols-5 gap-1">
        {upcoming.map((day, index) => {
          const j = dateKeyToJalaali(day.dateKey);
          const load = Math.min(3, day.tasks.length);
          return (
            <button
              key={day.dateKey}
              type="button"
              onClick={() => onOpen(day.dateKey)}
              className={`flex flex-col items-center gap-1 rounded-[10px] border py-2 transition-colors ${
                index === 0
                  ? 'border-[var(--accent)] bg-[color-mix(in_srgb,var(--accent)_14%,transparent)]'
                  : 'border-[var(--border)] hover:border-[var(--border-strong)]'
              }`}
            >
              <span className="text-[8.5px] text-[var(--text-3)]">
                {index === 0 ? 'امروز' : getJalaaliMonthName(j.jm).slice(0, 3)}
              </span>
              <Numeric
                value={j.jd}
                variant="display"
                className="text-[14px] font-bold text-[var(--text)]"
              />
              <span className="flex h-[4px] items-center gap-[2px]" aria-hidden="true">
                {Array.from({ length: load }).map((_, i) => (
                  <span key={i} className="h-[3px] w-[3px] rounded-full bg-[var(--accent-fg)]" />
                ))}
              </span>
            </button>
          );
        })}
      </div>
    </ShowcaseCard>
  );
}
