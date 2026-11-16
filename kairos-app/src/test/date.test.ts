import { describe, expect, it } from 'vitest';
import {
  addDays,
  computeNextRecurrenceDate,
  dateKeyToJalaali,
  diffDays,
  getDateKeysInJalaaliYear,
  getPersianWeekdayIndex,
  getPersianWeekKeys,
  isJalaaliLeap,
  jalaaliToDateKey,
  shiftJalaaliMonth,
} from '@/lib/date';
import { toEnDigits } from '@/lib/format';

describe('Date & Jalali (Shamsi) Calendar Engine', () => {
  it('converts between Gregorian YYYY-MM-DD and Jalali accurately without timezone drift', () => {
    // 2026-03-21 is 1 Farvardin 1405
    const j = dateKeyToJalaali('2026-03-21');
    expect(j).toEqual({ jy: 1405, jm: 1, jd: 1 });
    expect(jalaaliToDateKey(1405, 1, 1)).toBe('2026-03-21');

    // 2026-11-16 -> 25 Aban 1405
    const nov16 = dateKeyToJalaali('2026-11-16');
    expect(nov16.jy).toBe(1405);
    expect(nov16.jm).toBe(8);
    expect(jalaaliToDateKey(nov16.jy, nov16.jm, nov16.jd)).toBe('2026-11-16');
  });

  it('produces exact 365 days for common Jalali years and 366 days for leap years', () => {
    const keys1405 = getDateKeysInJalaaliYear(1405);
    expect(keys1405.length).toBe(isJalaaliLeap(1405) ? 366 : 365);

    const keys1403 = getDateKeysInJalaaliYear(1403);
    expect(isJalaaliLeap(1403)).toBe(true);
    expect(keys1403.length).toBe(366);
  });

  it('aligns Persian weeks from Saturday (0) to Friday (6)', () => {
    const week = getPersianWeekKeys('2026-11-16');
    expect(week).toHaveLength(7);
    expect(getPersianWeekdayIndex(week[0])).toBe(0); // شنبه
    expect(getPersianWeekdayIndex(week[6])).toBe(6); // جمعه
    expect(diffDays(week[0], week[6])).toBe(6);
  });

  it('shifts Jalali months across year boundaries cleanly', () => {
    expect(shiftJalaaliMonth(1405, 1, -1)).toEqual({ jy: 1404, jm: 12 });
    expect(shiftJalaaliMonth(1405, 12, 1)).toEqual({ jy: 1406, jm: 1 });
  });

  it('computes recurring dates including Persian workdays and Jalali monthly rollover', () => {
    expect(computeNextRecurrenceDate('2026-11-16', 'daily')).toBe('2026-11-17');
    expect(computeNextRecurrenceDate('2026-11-16', 'weekly')).toBe('2026-11-23');

    // Wednesday 2026-11-18 -> skips Thursday & Friday -> Saturday 2026-11-21
    const wed = '2026-11-18';
    expect(getPersianWeekdayIndex(wed)).toBe(4);
    const nextWorkday = computeNextRecurrenceDate(wed, 'weekdays');
    expect(getPersianWeekdayIndex(nextWorkday)).toBe(0);
    expect(nextWorkday).toBe(addDays(wed, 3));

    // Monthly from 31 Shahrivar -> clamps safely to 30 Mehr
    const shahrivar31 = jalaaliToDateKey(1405, 6, 31);
    const nextMonth = computeNextRecurrenceDate(shahrivar31, 'monthly');
    expect(dateKeyToJalaali(nextMonth)).toEqual({ jy: 1405, jm: 7, jd: 30 });
  });

  it('normalizes Persian/Arabic numerals strictly to English 0-9', () => {
    expect(toEnDigits('۱۴۰۵/۰۸/۲۵ - ۷۴%')).toBe('1405/08/25 - 74%');
  });
});
