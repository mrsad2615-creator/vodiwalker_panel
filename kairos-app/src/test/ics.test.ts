import { describe, expect, it } from 'vitest';
import { parseIcs, parseIcsDate, parseContentLine, unfoldIcs } from '@/lib/ics';

/**
 * The ICS reader is the one place where a real Google Calendar export enters the
 * app. These fixtures are shaped exactly like Google's output: CRLF line endings,
 * folded long lines, escaped text, UTC stamps, all-day dates and cancelled events.
 */

const GOOGLE_FIXTURE = [
  'BEGIN:VCALENDAR',
  'VERSION:2.0',
  'PRODID:-//Google Inc//Google Calendar 70.9054//EN',
  'BEGIN:VEVENT',
  'DTSTART:20261120T063000Z',
  'DTEND:20261120T073000Z',
  'SUMMARY:جلسهٔ تیم محصول',
  'LOCATION:Google Meet',
  'DESCRIPTION:مرور نقشهٔ راه\\nبا تیم طراحی\\, و مهندسی',
  'UID:abc-123@google.com',
  'END:VEVENT',
  'BEGIN:VEVENT',
  'DTSTART;VALUE=DATE:20261122',
  'SUMMARY:تولد مادربزرگ',
  'UID:def-456@google.com',
  'END:VEVENT',
  'BEGIN:VEVENT',
  'DTSTART:20261121T180000',
  'SUMMARY:بدن‌سازی',
  'UID:ghi-789@google.com',
  'END:VEVENT',
  'BEGIN:VEVENT',
  'DTSTART:20261123T100000',
  'SUMMARY:جلسهٔ لغو‌شده',
  'STATUS:CANCELLED',
  'UID:cancelled-1@google.com',
  'END:VEVENT',
  'END:VCALENDAR',
].join('\r\n');

describe('ICS — خواندن فایل تقویم', () => {
  it('unfolds folded lines and keeps the content intact', () => {
    const lines = unfoldIcs('SUMMARY:یک خط\r\n  ادامه‌دار\r\nDTSTART:20261120');
    expect(lines).toEqual(['SUMMARY:یک خط ادامه‌دار', 'DTSTART:20261120']);
  });

  it('splits a content line into name, parameters and value', () => {
    const parsed = parseContentLine('DTSTART;TZID=Asia/Tehran:20261120T093000');
    expect(parsed?.name).toBe('DTSTART');
    expect(parsed?.params.TZID).toBe('Asia/Tehran');
    expect(parsed?.value).toBe('20261120T093000');
  });

  it('reads all three date shapes', () => {
    expect(parseIcsDate('20261122')).toEqual({
      dateKey: '2026-11-22',
      time: null,
      allDay: true,
    });
    expect(parseIcsDate('20261121T180000')).toEqual({
      dateKey: '2026-11-21',
      time: '18:00',
      allDay: false,
    });
    // A UTC stamp must land on the *local* day — CI runs in UTC, so both agree.
    expect(parseIcsDate('20261120T063000Z')?.dateKey).toBe('2026-11-20');
    expect(parseIcsDate('nonsense')).toBeNull();
  });

  it('parses a Google export into events, skipping cancelled ones', () => {
    const events = parseIcs(GOOGLE_FIXTURE);
    expect(events).toHaveLength(3);
    expect(events.map((event) => event.title)).toEqual([
      'جلسهٔ تیم محصول',
      'بدن‌سازی',
      'تولد مادربزرگ',
    ]);

    const meeting = events[0];
    expect(meeting.allDay).toBe(false);
    expect(meeting.dateKey).toBe('2026-11-20');
    expect(meeting.location).toBe('Google Meet');
    // Escaped newline and comma come back as real characters.
    expect(meeting.description).toContain('\n');
    expect(meeting.description).toContain('طراحی, و مهندسی');

    const birthday = events[2];
    expect(birthday.allDay).toBe(true);
    expect(birthday.startTime).toBeNull();
  });

  it('sorts by day and puts all-day events first', () => {
    const events = parseIcs(GOOGLE_FIXTURE);
    const keys = events.map((event) => event.dateKey);
    expect([...keys].sort()).toEqual(keys);
    expect(events[0].allDay).toBe(false);
    expect(events[2].allDay).toBe(true);
  });

  it('returns nothing for a file without events instead of throwing', () => {
    expect(parseIcs('BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR')).toEqual([]);
    expect(parseIcs('')).toEqual([]);
  });
});
