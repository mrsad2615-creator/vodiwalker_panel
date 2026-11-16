import {describe,it,expect} from 'vitest';
import {cityClock,formatDuration,gregorianToJalali,jalaliMonthDays,jalaliToGregorian,lapDelta,nextAlarmDate,offsetLabel,timerRemaining} from './timeEngine';
describe('MESAD precision time engine',()=>{
 it('converts Nowruz 2025 and leap day correctly',()=>{expect(gregorianToJalali(new Date('2025-03-21T12:00:00Z'))).toEqual({year:1404,month:1,day:1});expect(gregorianToJalali(new Date('2024-02-29T12:00:00Z'))).toEqual({year:1402,month:12,day:10});});
 it('round-trips Jalali dates against Gregorian dates',()=>{for(const value of ['2000-01-01','2024-03-20','2026-11-16','2030-12-31']){const greg=new Date(`${value}T12:00:00Z`);const j=gregorianToJalali(greg);expect(jalaliToGregorian(j).toISOString().slice(0,10)).toBe(value);}});
 it('returns valid Jalali month lengths',()=>{expect(jalaliMonthDays(1404,1)).toBe(31);expect(jalaliMonthDays(1404,7)).toBe(30);expect(jalaliMonthDays(1404,12)).toBe(29);expect(()=>jalaliMonthDays(1404,13)).toThrow();});
 it('uses deadline math for timers without counter drift',()=>{expect(timerRemaining(15_000,10_000,9_000)).toBe(6_000);expect(timerRemaining(1_000,0,1_300)).toBe(0);expect(timerRemaining(undefined,500,0)).toBe(500);});
 it('formats elapsed intervals at minute and hour boundaries',()=>{expect(formatDuration(65_999)).toBe('01:05');expect(formatDuration(3_661_000)).toBe('01:01:01');expect(formatDuration(-1)).toBe('00:00');});
 it('calculates weekday and one-time alarm recurrence',()=>{const now=new Date('2026-11-16T08:30:00');expect(nextAlarmDate({hour:7,minute:0,weekdays:[]},now)?.getDate()).toBe(17);const daily=nextAlarmDate({hour:9,minute:0,weekdays:[1,3]},now)!;expect(daily.getDay()).toBe(1);expect(daily.getHours()).toBe(9);});
 it('computes lap deltas and IANA city offsets',()=>{expect(lapDelta(12_400,25_000)).toBe(12_600);expect(cityClock('Asia/Tehran',new Date('2025-01-01T12:00:00Z'))).toMatch(/15:30/);expect(offsetLabel('America/New_York',new Date('2025-01-01T12:00:00Z'))).toBe('GMT-5');});
});
