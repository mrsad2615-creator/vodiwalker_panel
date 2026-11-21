import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';
import type { FocusSession, HabitLog, Task } from '@/types/domain';
import { buildDailySeries, compareWindows, type DailyPoint } from '@/lib/homeMetrics';
import { addDays, formatJalaaliShort, getTodayKey } from '@/lib/date';

/**
 * Shareable report.
 *
 * A productivity app that only shows you numbers inside itself is a mirror; the
 * point of a weekly report is that you can send it to a coach, a partner or your
 * own notes app. So the report is a *pure string* first — provable, diffable,
 * copyable — and the sharing is a thin platform adapter on top.
 *
 * Nothing here invents data. If a week has no focus sessions, the report says so
 * instead of printing a courteous zero trend.
 */

export interface WeeklyReport {
  windowDays: number;
  /** Oldest day of the window. */
  startKey: string;
  endKey: string;
  series: DailyPoint[];
  focusMinutes: number;
  focusSessions: number;
  tasksDone: number;
  habitsDone: number;
  activeDays: number;
  /** Percentage change of the composite activity vs the previous window. */
  deltaPercent: number | null;
  bestDay: DailyPoint | null;
  hasData: boolean;
}

export interface ReportInput {
  tasks: Task[];
  habitLogs: HabitLog[];
  focusSessions: FocusSession[];
}

const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];

/** Persian digits, because this text is going to be read by a person, not parsed. */
export function faDigits(value: string | number): string {
  return String(value).replace(/\d/g, (digit) => FA_DIGITS[Number(digit)]);
}

export function buildWeeklyReport(
  input: ReportInput,
  todayKey: string = getTodayKey(),
  windowDays = 7
): WeeklyReport {
  // Two windows so the comparison has real numbers behind it.
  const series = buildDailySeries(input, todayKey, windowDays * 2);
  const comparison = compareWindows(series, windowDays);
  const current = series.slice(-windowDays);

  const focusSessions = input.focusSessions.filter((session) =>
    current.some((point) => point.dateKey === session.dateKey)
  );

  const activeDays = current.filter(
    (point) => point.focusMinutes > 0 || point.tasksDone > 0 || point.habitsDone > 0
  ).length;

  const bestDay = current.reduce<DailyPoint | null>(
    (best, point) => (best === null || point.activity > best.activity ? point : best),
    null
  );

  return {
    windowDays,
    startKey: addDays(todayKey, -(windowDays - 1)),
    endKey: todayKey,
    series: current,
    focusMinutes: Math.round(comparison.focusMinutes),
    focusSessions: focusSessions.length,
    tasksDone: comparison.tasksDone,
    habitsDone: comparison.habitsDone,
    activeDays,
    deltaPercent: comparison.deltaPercent,
    bestDay: bestDay && bestDay.activity > 0 ? bestDay : null,
    hasData: activeDays > 0,
  };
}

/** The shareable text. Plain, honest, and readable in any messenger. */
export function reportToText(report: WeeklyReport): string {
  // Dates go through the same digit conversion as the numbers: a Persian
  // sentence that ends in "28 آبان" looks machine-made.
  const label = (key: string) => faDigits(formatJalaaliShort(key));
  const range = `${label(report.startKey)} تا ${label(report.endKey)}`;
  const lines: string[] = [`کایروس · گزارش ${faDigits(report.windowDays)} روزه`, range, ''];

  if (!report.hasData) {
    lines.push('در این بازه هیچ فعالیتی ثبت نشده است.');
  } else {
    lines.push(`تمرکز: ${faDigits(report.focusMinutes)} دقیقه در ${faDigits(report.focusSessions)} جلسه`);
    lines.push(`وظایف انجام‌شده: ${faDigits(report.tasksDone)}`);
    lines.push(`عادت‌های ثبت‌شده: ${faDigits(report.habitsDone)}`);
    lines.push(`روزهای فعال: ${faDigits(report.activeDays)} از ${faDigits(report.windowDays)}`);
    if (report.bestDay) {
      lines.push(
        `بهترین روز: ${label(report.bestDay.dateKey)} با شاخص ${faDigits(report.bestDay.activity)}`
      );
    }
    if (report.deltaPercent !== null) {
      const sign = report.deltaPercent > 0 ? '+' : '';
      lines.push(`روند نسبت به دورهٔ قبل: ${sign}${faDigits(report.deltaPercent)}٪`);
    }
  }

  lines.push('', 'ساخته‌شده با KAIROS');
  return lines.join('\n');
}

export type ShareOutcome = 'share' | 'clipboard' | 'error';

/**
 * Shares the text: native sheet on Android, clipboard on the web.
 * Returns where it actually went, so the UI never claims a share that did not
 * happen.
 */
export async function shareReportText(text: string, title = 'گزارش کایروس'): Promise<ShareOutcome> {
  if (Capacitor.isNativePlatform()) {
    try {
      await Share.share({ title, text, dialogTitle: 'اشتراک‌گذاری گزارش' });
      return 'share';
    } catch {
      // fall through to the clipboard
    }
  }
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return 'clipboard';
    }
  } catch {
    return 'error';
  }
  return 'error';
}
