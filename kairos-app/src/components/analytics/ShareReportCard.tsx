import { useMemo, useState } from 'react';
import { Share2, Copy } from '@/components/ui/icons';
import { Numeric } from '@/components/ui/Numeric';
import { useApp } from '@/context/AppContext';
import type { FocusSession, HabitLog, Task } from '@/types/domain';
import { buildWeeklyReport, reportToText, shareReportText } from '@/lib/report';

interface ShareReportCardProps {
  tasks: Task[];
  habitLogs: HabitLog[];
  focusSessions: FocusSession[];
}

/**
 * Weekly report you can actually send somewhere.
 *
 * The text is built by a pure function and shown verbatim before it is shared —
 * no surprise, no cloud, no "your data went somewhere you did not see". On
 * Android the system sheet takes it; on the web it goes to the clipboard.
 */
export function ShareReportCard({ tasks, habitLogs, focusSessions }: ShareReportCardProps) {
  const { pushToast } = useApp();
  const [busy, setBusy] = useState(false);

  const report = useMemo(
    () => buildWeeklyReport({ tasks, habitLogs, focusSessions }),
    [tasks, habitLogs, focusSessions]
  );
  const text = useMemo(() => reportToText(report), [report]);

  const share = async () => {
    setBusy(true);
    const outcome = await shareReportText(text);
    setBusy(false);
    if (outcome === 'share') {
      pushToast({ title: 'گزارش آمادهٔ اشتراک شد', tone: 'success' });
    } else if (outcome === 'clipboard') {
      pushToast({ title: 'متن گزارش کپی شد', description: 'هرجا می‌خواهید بچسبانید.', tone: 'success' });
    } else {
      pushToast({ title: 'اشتراک‌گذاری ممکن نشد', description: 'متن را از همین صفحه انتخاب و کپی کنید.', tone: 'warning' });
    }
  };

  return (
    <section aria-label="گزارش قابل اشتراک" className="rail p-4">
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-xs font-bold text-[var(--text)]">گزارش این هفته</h2>
          <p className="mt-0.5 text-[10.5px] leading-relaxed text-[var(--text-2)]">
            همان عددهایی که در تحلیل می‌بینید، در یک متن که می‌توانید بفرستید.
          </p>
        </div>
        <button
          type="button"
          onClick={() => void share()}
          disabled={busy}
          className="btn-royal flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-[11px] font-bold disabled:opacity-60"
        >
          <Share2 className="h-3.5 w-3.5" />
          <span>{busy ? 'در حال آماده‌سازی…' : 'اشتراک‌گذاری'}</span>
        </button>
      </header>

      <dl className="mt-3 grid grid-cols-4 gap-2 border-y border-[var(--border)] py-2.5">
        {[
          { label: 'تمرکز', value: report.focusMinutes, suffix: 'د' },
          { label: 'وظیفه', value: report.tasksDone },
          { label: 'عادت', value: report.habitsDone },
          { label: 'روز فعال', value: report.activeDays },
        ].map((metric) => (
          <div key={metric.label} className="text-center">
            <Numeric
              value={metric.value}
              suffix={metric.suffix}
              className="num-geo text-[13px] font-semibold text-[var(--text)]"
            />
            <dt className="mt-0.5 text-[9px] text-[var(--text-3)]">{metric.label}</dt>
          </div>
        ))}
      </dl>

      <pre
        dir="rtl"
        className="mt-3 max-h-40 overflow-y-auto whitespace-pre-wrap rounded-xl border border-[var(--border)] bg-[var(--surface-2)]/60 p-3 font-sans text-[10.5px] leading-relaxed text-[var(--text-2)]"
      >
        {text}
      </pre>

      <button
        type="button"
        onClick={() => void share()}
        className="mt-2 flex items-center gap-1.5 text-[10px] text-[var(--text-3)] hover:text-[var(--text-2)]"
      >
        <Copy className="h-3 w-3" />
        <span>کپی متن گزارش</span>
      </button>
    </section>
  );
}
