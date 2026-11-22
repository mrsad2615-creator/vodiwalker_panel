/**
 * ICS (iCalendar) reader — the offline half of "Google Calendar, in KAIROS".
 *
 * Why in-app and not OAuth: KAIROS is an offline-first app; asking for a Google
 * token would buy the same result as the file Google already gives you in two
 * clicks ("Settings → Import & export → Export"), cost a network permission, and
 * break the moment the token expires. So we read the file people can actually
 * produce on any device.
 *
 * Supported: VEVENT, DTSTART in all three shapes (date-only, local time, UTC),
 * line unfolding, TEXT escaping, multi-day events (collapsed to their start day),
 * and cancellation. Deliberately ignored: RRULE expansion (the event is imported
 * once, at its first occurrence), attachments, alarms, VTODO.
 */

export interface IcsEvent {
  uid: string;
  title: string;
  /** Local calendar day, YYYY-MM-DD (the same key the rest of the app uses). */
  dateKey: string;
  /** HH:MM for timed events, null for all-day ones. */
  startTime: string | null;
  endTime: string | null;
  allDay: boolean;
  location: string;
  description: string;
}

interface ContentLine {
  name: string;
  params: Record<string, string>;
  value: string;
}

/** RFC 5545 line folding: a newline followed by one space or tab continues. */
export function unfoldIcs(raw: string): string[] {
  return raw
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\n[ \t]/g, '')
    .split('\n');
}

export function parseContentLine(line: string): ContentLine | null {
  // NAME;PARAM=1;PARAM=2:VALUE — the colon separator is the first one that is not
  // inside a quoted parameter value.
  let inQuotes = false;
  let colon = -1;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') inQuotes = !inQuotes;
    else if (char === ':' && !inQuotes) {
      colon = i;
      break;
    }
  }
  if (colon === -1) return null;

  const head = line.slice(0, colon);
  const value = line.slice(colon + 1);
  const [rawName, ...rawParams] = head.split(';');
  if (!rawName) return null;

  const params: Record<string, string> = {};
  for (const param of rawParams) {
    const eq = param.indexOf('=');
    if (eq === -1) continue;
    params[param.slice(0, eq).toUpperCase()] = param
      .slice(eq + 1)
      .replace(/^"|"$/g, '');
  }

  return { name: rawName.toUpperCase(), params, value };
}

/** TEXT values escape commas, semicolons, newlines and backslashes. */
export function unescapeIcsText(value: string): string {
  return value
    .replace(/\\n/gi, '\n')
    .replace(/\\,/g, ',')
    .replace(/\\;/g, ';')
    .replace(/\\\\/g, '\\')
    .trim();
}

interface ParsedDate {
  dateKey: string;
  time: string | null;
  allDay: boolean;
}

const pad = (n: number) => String(n).padStart(2, '0');
const toKey = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`;

/**
 * DTSTART:20261120 … date only
 * DTSTART:20261120T093000 … floating local time
 * DTSTART:20261120T093000Z … UTC, converted to the device's local day/time
 */
export function parseIcsDate(
  value: string,
  params: Record<string, string> = {}
): ParsedDate | null {
  const clean = value.trim();
  if (params.VALUE === 'DATE' || /^\d{8}$/.test(clean)) {
    const match = /^(\d{4})(\d{2})(\d{2})$/.exec(clean);
    if (!match) return null;
    return {
      dateKey: toKey(Number(match[1]), Number(match[2]), Number(match[3])),
      time: null,
      allDay: true,
    };
  }

  const match = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})(Z?)$/.exec(clean);
  if (!match) return null;

  const [, y, mo, d, h, mi, , zulu] = match;

  if (zulu === 'Z') {
    // A UTC stamp must become a *local* day and time, or a 00:30 Tehran event
    // would land on the previous day's page.
    const utc = new Date(
      Date.UTC(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi), 0)
    );
    return {
      dateKey: toKey(utc.getFullYear(), utc.getMonth() + 1, utc.getDate()),
      time: `${pad(utc.getHours())}:${pad(utc.getMinutes())}`,
      allDay: false,
    };
  }

  // Floating local time (or TZID, which we deliberately treat as wall time).
  return {
    dateKey: toKey(Number(y), Number(mo), Number(d)),
    time: `${h}:${mi}`,
    allDay: false,
  };
}

export function parseIcs(raw: string): IcsEvent[] {
  const events: IcsEvent[] = [];
  let current: Partial<IcsEvent> & { allDay?: boolean } | null = null;
  let cancelled = false;

  for (const line of unfoldIcs(raw)) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    if (/^BEGIN:VEVENT$/i.test(trimmed)) {
      current = { uid: '', title: '', location: '', description: '', startTime: null, endTime: null };
      cancelled = false;
      continue;
    }

    if (/^END:VEVENT$/i.test(trimmed)) {
      if (current && !cancelled && current.dateKey && current.title) {
        events.push({
          uid: current.uid || `${current.dateKey}-${current.title}`,
          title: current.title,
          dateKey: current.dateKey,
          startTime: current.allDay ? null : (current.startTime ?? null),
          endTime: current.allDay ? null : (current.endTime ?? null),
          allDay: Boolean(current.allDay),
          location: current.location ?? '',
          description: current.description ?? '',
        });
      }
      current = null;
      continue;
    }

    if (!current) continue;

    const parsed = parseContentLine(trimmed);
    if (!parsed) continue;

    switch (parsed.name) {
      case 'UID':
        current.uid = parsed.value.trim();
        break;
      case 'SUMMARY':
        current.title = unescapeIcsText(parsed.value);
        break;
      case 'LOCATION':
        current.location = unescapeIcsText(parsed.value);
        break;
      case 'DESCRIPTION':
        current.description = unescapeIcsText(parsed.value);
        break;
      case 'STATUS':
        cancelled = parsed.value.trim().toUpperCase() === 'CANCELLED';
        break;
      case 'DTSTART': {
        const start = parseIcsDate(parsed.value, parsed.params);
        if (start) {
          current.dateKey = start.dateKey;
          current.startTime = start.time;
          current.allDay = start.allDay;
        }
        break;
      }
      case 'DTEND': {
        const end = parseIcsDate(parsed.value, parsed.params);
        if (end?.time) current.endTime = end.time;
        break;
      }
      default:
        break;
    }
  }

  // Stable order: by day, then by time, with all-day items first.
  return events.sort((a, b) => {
    if (a.dateKey !== b.dateKey) return a.dateKey < b.dateKey ? -1 : 1;
    return (a.startTime ?? '00:00').localeCompare(b.startTime ?? '00:00');
  });
}
