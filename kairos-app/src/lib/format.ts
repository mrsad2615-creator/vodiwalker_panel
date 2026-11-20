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
    badgeBg: 'rgba(109, 74, 255, 0.16)',
    badgeText: '#B9A8FF',
  },
  personal: {
    label: 'شخصی',
    badgeBg: 'rgba(163, 140, 255, 0.13)',
    badgeText: '#DCD0FF',
  },
  health: {
    label: 'سلامت',
    badgeBg: 'rgba(155, 118, 224, 0.16)',
    badgeText: '#C5B0F3',
  },
  learning: {
    label: 'یادگیری',
    badgeBg: 'rgba(110, 74, 189, 0.22)',
    badgeText: '#B9A8FF',
  },
  finance: {
    label: 'مالی',
    badgeBg: 'rgba(196, 139, 92, 0.14)',
    badgeText: '#DFB08A',
  },
  creative: {
    label: 'خلاقیت',
    badgeBg: 'rgba(186, 108, 168, 0.16)',
    badgeText: '#E2A6D5',
  },
};

export const PRIORITY_META: Record<
  TaskPriority,
  { label: string; dotColor: string; textColor: string }
> = {
  high: { label: 'بالا', dotColor: '#E5484D', textColor: '#EF9095' },
  medium: { label: 'متوسط', dotColor: '#6D4AFF', textColor: '#B9A8FF' },
  low: { label: 'عادی', dotColor: '#6B6B75', textColor: '#A69DB8' },
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
    accent: '#E5484D',
  },
  q2: {
    code: 'Q2',
    title: 'مهم و غیرفوری',
    subtitle: 'رشد بلندمدت و استراتژیک',
    action: 'برنامه‌ریزی',
    accent: '#6D4AFF',
  },
  q3: {
    code: 'Q3',
    title: 'غیرمهم و فوری',
    subtitle: 'کارهای سریع یا واگذاری',
    action: 'مدیریت سریع',
    accent: '#C9975C',
  },
  q4: {
    code: 'Q4',
    title: 'غیرمهم و غیرفوری',
    subtitle: 'کم‌اولویت یا قابل حذف',
    action: 'ارزیابی مجدد',
    accent: '#6B6B75',
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
