import {
  toJalaali,
  toGregorian,
  jalaaliMonthLength,
  isLeapJalaaliYear,
  type JalaaliDate,
} from 'jalaali-js';

export const PERSIAN_MONTHS = [
  'فروردین',
  'اردیبهشت',
  'خرداد',
  'تیر',
  'مرداد',
  'شهریور',
  'مهر',
  'آبان',
  'آذر',
  'دی',
  'بهمن',
  'اسفند',
] as const;

/** Persian week starts on Saturday (index 0) and ends on Friday (index 6) */
export const PERSIAN_WEEKDAYS_FULL = [
  'شنبه',
  'یکشنبه',
  'دوشنبه',
  'سه‌شنبه',
  'چهارشنبه',
  'پنجشنبه',
  'جمعه',
] as const;

export const PERSIAN_WEEKDAYS_SHORT = ['ش', 'ی', 'د', 'س', 'چ', 'پ', 'ج'] as const;

/**
 * Formats a local Date into a strict timezone-safe YYYY-MM-DD key.
 * NEVER uses toISOString() so UTC offset cannot shift the day.
 */
export function toDateKey(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function getTodayKey(now: Date = new Date()): string {
  return toDateKey(now);
}

/**
 * Parses a YYYY-MM-DD date key into a local-midnight Date object safely.
 */
export function parseDateKey(dateKey: string): Date {
  const [yStr, mStr, dStr] = dateKey.split('-');
  return new Date(Number(yStr), Number(mStr) - 1, Number(dStr), 0, 0, 0, 0);
}

/**
 * Adds (or subtracts) calendar days safely in local time without DST drift.
 */
export function addDays(dateKey: string, deltaDays: number): string {
  const d = parseDateKey(dateKey);
  d.setDate(d.getDate() + deltaDays);
  return toDateKey(d);
}

/**
 * Exact integer difference in local calendar days (dateKeyB - dateKeyA).
 */
export function diffDays(dateKeyA: string, dateKeyB: string): number {
  const a = parseDateKey(dateKeyA);
  const b = parseDateKey(dateKeyB);
  const utcA = Date.UTC(a.getFullYear(), a.getMonth(), a.getDate());
  const utcB = Date.UTC(b.getFullYear(), b.getMonth(), b.getDate());
  return Math.round((utcB - utcA) / 86_400_000);
}

/**
 * Returns Persian weekday index: 0 = Saturday (شنبه) ... 6 = Friday (جمعه).
 */
export function getPersianWeekdayIndex(dateKey: string): number {
  const jsDay = parseDateKey(dateKey).getDay(); // 0=Sun..6=Sat
  return (jsDay + 1) % 7;
}

export function dateKeyToJalaali(dateKey: string): JalaaliDate {
  const [gy, gm, gd] = dateKey.split('-').map(Number);
  return toJalaali(gy, gm, gd);
}

export function jalaaliToDateKey(jy: number, jm: number, jd: number): string {
  const { gy, gm, gd } = toGregorian(jy, jm, jd);
  return `${gy}-${String(gm).padStart(2, '0')}-${String(gd).padStart(2, '0')}`;
}

export function getJalaaliMonthName(jm: number): string {
  return PERSIAN_MONTHS[Math.max(0, Math.min(11, jm - 1))];
}

export function getJalaaliMonthDaysCount(jy: number, jm: number): number {
  return jalaaliMonthLength(jy, jm);
}

export function isJalaaliLeap(jy: number): boolean {
  return isLeapJalaaliYear(jy);
}

/**
 * Returns all YYYY-MM-DD keys belonging to a specific Jalali month (1..12).
 */
export function getDateKeysInJalaaliMonth(jy: number, jm: number): string[] {
  const count = jalaaliMonthLength(jy, jm);
  const keys: string[] = [];
  for (let jd = 1; jd <= count; jd++) {
    keys.push(jalaaliToDateKey(jy, jm, jd));
  }
  return keys;
}

/**
 * Returns all 365 or 366 YYYY-MM-DD keys for a complete Jalali year (Farvardin 1 .. Esfand 29/30).
 */
export function getDateKeysInJalaaliYear(jy: number): string[] {
  const keys: string[] = [];
  for (let jm = 1; jm <= 12; jm++) {
    const days = jalaaliMonthLength(jy, jm);
    for (let jd = 1; jd <= days; jd++) {
      keys.push(jalaaliToDateKey(jy, jm, jd));
    }
  }
  return keys;
}

/**
 * Returns the 7 date keys (Saturday .. Friday) of the Persian week containing dateKey.
 */
export function getPersianWeekKeys(dateKey: string): string[] {
  const weekdayIdx = getPersianWeekdayIndex(dateKey);
  const saturday = addDays(dateKey, -weekdayIdx);
  return Array.from({ length: 7 }, (_, i) => addDays(saturday, i));
}

/**
 * Shifts a Jalali (jy, jm) pair by deltaMonths (-1 for previous month, +1 for next).
 */
export function shiftJalaaliMonth(
  jy: number,
  jm: number,
  deltaMonths: number
): { jy: number; jm: number } {
  const total = jy * 12 + (jm - 1) + deltaMonths;
  const newJy = Math.floor(total / 12);
  const newJm = ((total % 12) + 12) % 12 + 1;
  return { jy: newJy, jm: newJm };
}

/**
 * Formats a dateKey cleanly in Persian with crisp English numerals for day & year.
 * Example: "دوشنبه، 25 آبان 1405"
 */
export function formatJalaaliFull(dateKey: string): {
  weekday: string;
  day: string;
  month: string;
  year: string;
  compact: string;
} {
  const { jy, jm, jd } = dateKeyToJalaali(dateKey);
  const weekday = PERSIAN_WEEKDAYS_FULL[getPersianWeekdayIndex(dateKey)];
  const month = getJalaaliMonthName(jm);
  return {
    weekday,
    day: String(jd),
    month,
    year: String(jy),
    compact: `${jd} ${month} ${jy}`,
  };
}

export function formatJalaaliShort(dateKey: string): string {
  const { jm, jd } = dateKeyToJalaali(dateKey);
  return `${jd} ${getJalaaliMonthName(jm)}`;
}

export function getPersianGreeting(now: Date = new Date()): string {
  const h = now.getHours();
  if (h >= 5 && h < 12) return 'صبح بخیر';
  if (h >= 12 && h < 17) return 'ظهر بخیر';
  if (h >= 17 && h < 21) return 'عصر بخیر';
  return 'شب بخیر';
}

export function formatClockHHMM(now: Date = new Date()): string {
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

export function computeNextRecurrenceDate(
  currentDueDate: string,
  rule: 'daily' | 'weekdays' | 'weekly' | 'monthly'
): string {
  if (rule === 'daily') return addDays(currentDueDate, 1);
  if (rule === 'weekly') return addDays(currentDueDate, 7);
  if (rule === 'weekdays') {
    // Persian workdays: Saturday (0) through Wednesday (4), skip Thursday(5) & Friday(6)
    let next = addDays(currentDueDate, 1);
    while (getPersianWeekdayIndex(next) >= 5) {
      next = addDays(next, 1);
    }
    return next;
  }
  // Monthly in Jalali calendar!
  const { jy, jm, jd } = dateKeyToJalaali(currentDueDate);
  const nextMonth = shiftJalaaliMonth(jy, jm, 1);
  const maxDay = jalaaliMonthLength(nextMonth.jy, nextMonth.jm);
  return jalaaliToDateKey(nextMonth.jy, nextMonth.jm, Math.min(jd, maxDay));
}
