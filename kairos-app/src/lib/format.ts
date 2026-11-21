import type { EisenhowerQuadrant, TaskCategory, TaskPriority } from '@/types/domain';
import { diffDays, formatJalaaliShort, getTodayKey } from './date';

/**
 * Guarantees Western/English numerals 0-9 for the KAIROS luxury typography system.
 */
export function toEnDigits(input: string | number): string {
  const str = String(input);
  return str
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));
}

export function formatDurationShort(totalMinutes: number): string {
  const mins = Math.max(0, Math.round(totalMinutes));
  if (mins < 60) return `${mins}m`;
  const h = Math.floor(mins / 60);
  const rem = mins % 60;
  return rem === 0 ? `${h}h` : `${h}h ${rem}m`;
}

export function formatDurationPersian(totalMinutes: number): {
  hours: number;
  minutes: number;
  compactEn: string;
} {
  const mins = Math.max(0, Math.round(totalMinutes));
  const hours = Math.floor(mins / 60);
  const minutes = mins % 60;
  const compactEn = hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
  return { hours, minutes, compactEn };
}

export function formatTimerSeconds(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds));
  const hrs = Math.floor(clamped / 3600);
  const mins = Math.floor((clamped % 3600) / 60);
  const secs = clamped % 60;
  if (hrs > 0) {
    return `${String(hrs).padStart(2, '0')}:${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  }
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function formatRelativeDueLabel(
  dueDate: string | null,
  todayKey: string = getTodayKey()
): { label: string; isOverdue: boolean; isToday: boolean } {
  if (!dueDate) return { label: 'بدون تاریخ', isOverdue: false, isToday: false };
  const d = diffDays(todayKey, dueDate);
  if (d === 0) return { label: 'امروز', isOverdue: false, isToday: true };
  if (d === 1) return { label: 'فردا', isOverdue: false, isToday: false };
  if (d === -1) return { label: 'دیروز', isOverdue: true, isToday: false };
  if (d < -1) {
    return { label: `${Math.abs(d)} روز تأخیر`, isOverdue: true, isToday: false };
  }
  return { label: formatJalaaliShort(dueDate), isOverdue: false, isToday: false };
}

export const CATEGORY_META: Record<
  TaskCategory,
  { label: string; badgeBg: string; badgeText: string }
> = {
  work: {
    label: 'کار و پروژه',
    badgeBg: 'color-mix(in srgb, var(--accent) 16%, transparent)',
    badgeText: 'var(--accent-fg)',
  },
  personal: {
    label: 'شخصی',
    badgeBg: 'var(--cat-soft)',
    badgeText: 'var(--cat-personal)',
  },
  health: {
    label: 'سلامت',
    badgeBg: 'var(--cat-health-soft)',
    badgeText: 'var(--cat-health)',
  },
  learning: {
    label: 'یادگیری',
    badgeBg: 'rgba(110, 74, 189, 0.22)',
    badgeText: 'var(--accent-fg)',
  },
  finance: {
    label: 'مالی',
    badgeBg: 'var(--cat-finance-soft)',
    badgeText: 'var(--cat-finance)',
  },
  creative: {
    label: 'خلاقیت',
    badgeBg: 'var(--cat-creative-soft)',
    badgeText: 'var(--cat-creative)',
  },
};

export const PRIORITY_META: Record<
  TaskPriority,
  { label: string; dotColor: string; textColor: string }
> = {
  high: { label: 'بالا', dotColor: 'var(--danger)', textColor: 'var(--danger-fg)' },
  medium: { label: 'متوسط', dotColor: 'var(--accent)', textColor: 'var(--accent-fg)' },
  low: { label: 'عادی', dotColor: 'var(--text-3)', textColor: 'var(--text-2)' },
};

export const QUADRANT_META: Record<
  EisenhowerQuadrant,
  {
    code: string;
    title: string;
    subtitle: string;
    action: string;
    accent: string;
  }
> = {
  q1: {
    code: 'Q1',
    title: 'مهم و فوری',
    subtitle: 'اولویت مطلق امروز',
    action: 'انجام فوری',
    accent: 'var(--danger)',
  },
  q2: {
    code: 'Q2',
    title: 'مهم و غیرفوری',
    subtitle: 'رشد بلندمدت و استراتژیک',
    action: 'برنامه‌ریزی',
    accent: 'var(--accent)',
  },
  q3: {
    code: 'Q3',
    title: 'غیرمهم و فوری',
    subtitle: 'کارهای سریع یا واگذاری',
    action: 'مدیریت سریع',
    accent: 'var(--warn)',
  },
  q4: {
    code: 'Q4',
    title: 'غیرمهم و غیرفوری',
    subtitle: 'کم‌اولویت یا قابل حذف',
    action: 'ارزیابی مجدد',
    accent: 'var(--text-3)',
  },
};

export const RECURRENCE_LABELS = {
  none: 'بدون تکرار',
  daily: 'روزانه',
  weekdays: 'روزهای کاری (ش تا چ)',
  weekly: 'هفتگی',
  monthly: 'ماهانه (شمسی)',
} as const;

export function createId(prefix: string): string {
  const rand =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID().slice(0, 12)
      : `${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  return `${prefix}_${rand}`;
}
