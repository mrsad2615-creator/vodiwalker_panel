import { useEffect, useRef, useState } from 'react';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { Numeric } from '@/components/ui/Numeric';
import { upsertJournalEntry } from '@/lib/db';
import { MOODS } from '@/lib/journal';
import { formatJalaaliFull, getTodayKey } from '@/lib/date';
import { useApp } from '@/context/AppContext';
import type { JournalEntry } from '@/types/domain';

interface JournalSheetProps {
  open: boolean;
  dateKey?: string;
  entry?: JournalEntry | null;
  onClose: () => void;
}

/**
 * One day's entry, in the smallest form that still means something:
 * a mood you can answer in a second, one line worth keeping, and room to write
 * if you want it. No tags, no templates, no "rate your productivity 1–10".
 */
export function JournalSheet({ open, dateKey = getTodayKey(), entry, onClose }: JournalSheetProps) {
  const { pushToast } = useApp();
  const [mood, setMood] = useState(entry?.mood ?? 3);
  const [highlight, setHighlight] = useState(entry?.highlight ?? '');
  const [note, setNote] = useState(entry?.note ?? '');
  const [saving, setSaving] = useState(false);
  const [confirmOverwrite, setConfirmOverwrite] = useState(false);

  // Hydrate exactly once per opening — and never again after the reader has
  // touched a field. The live query pushes a new `entry` the instant the mood is
  // stored, so the row can arrive *while* the sentence is being typed: keying the
  // effect on `entry.id` alone re-hydrated `new-<date>` → `journal-<date>` and
  // wiped the input (the "my note disappeared" bug). Dirty wins over fresh data;
  // the sheet is a draft until it is saved or closed.
  const hydratedRef = useRef<string | null>(null);
  const originalRef = useRef<JournalEntry | null>(null);
  const dirtyRef = useRef(false);
  useEffect(() => {
    if (!open) {
      hydratedRef.current = null;
      originalRef.current = null;
      dirtyRef.current = false;
      setConfirmOverwrite(false);
      return;
    }
    if (dirtyRef.current) return;
    const key = entry?.id ?? `new-${dateKey}`;
    if (hydratedRef.current === key) return;
    hydratedRef.current = key;
    originalRef.current = entry ?? null;
    setMood(entry?.mood ?? 3);
    setHighlight(entry?.highlight ?? '');
    setNote(entry?.note ?? '');
  }, [open, entry, dateKey]);

  const touch = () => {
    dirtyRef.current = true;
  };

  const dateInfo = formatJalaaliFull(dateKey);

  const save = async () => {
    if (saving) return;
    setSaving(true);
    try {
      await upsertJournalEntry(dateKey, { mood, highlight: highlight.trim(), note: note.trim() }, originalRef.current);
      pushToast({
        title: 'روز ثبت شد',
        description: 'این یادداشت در ژورنال شما ماند.',
        tone: 'success',
      });
      onClose();
    } catch {
      pushToast({ title: 'ثبت انجام نشد', description: 'نسخهٔ روز ممکن است تغییر کرده باشد؛ متن فعلی باقی مانده است.', tone: 'warning' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <ModalSheet
      open={open}
      onClose={onClose}
      title="ژورنال امروز"
      subtitle={`${dateInfo.weekday} · ${dateInfo.day} ${dateInfo.month}`}
      footer={
        <div className="space-y-3 px-5 pb-1">
          {confirmOverwrite && <div role="region" aria-label="تأیید ویرایش ژورنال" className="space-y-2 rounded-xl border border-[var(--border)] p-3 text-xs leading-6 text-[var(--text)]">
            <p>یادداشت ثبت‌شدهٔ این روز با متن فعلی جایگزین شود؟ اگر نسخهٔ روز در این فاصله تغییر کرده باشد، ذخیره انجام نمی‌شود.</p>
            <div className="flex gap-2">
              <button type="button" disabled={saving} onClick={() => void save()} className="btn-royal min-h-11 rounded-xl px-3">تأیید ویرایش</button>
              <button type="button" disabled={saving} onClick={() => setConfirmOverwrite(false)} className="btn-ghost-luxury min-h-11 rounded-xl px-3">انصراف</button>
            </div>
          </div>}
          <div className="flex items-center justify-between gap-3">
            <span className="text-[11px] text-[var(--text-3)]">{entry ? 'این روز قبلاً نوشته شده؛ ویرایش می‌شود.' : 'یک خط کافی است.'}</span>
            <button type="button" onClick={() => { if (originalRef.current) setConfirmOverwrite(true); else void save(); }} disabled={saving}
              className="rounded-full bg-[var(--accent)] px-5 py-2 text-xs font-bold text-[var(--accent-on)] disabled:opacity-60">
              {saving ? 'در حال ذخیره' : 'ذخیرهٔ روز'}
            </button>
          </div>
        </div>
      }
    >
      <div className="space-y-5 px-5 py-4">
        {/* Mood — five verbs, no emoji soup. */}
        <div>
          <p className="mb-2 text-[11px] font-medium text-[var(--text-2)]">امروز چطور بود؟</p>
          <div className="grid grid-cols-5 gap-2" role="group" aria-label="حال امروز">
            {MOODS.map((option) => {
              const active = mood === option.value;
              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    touch();
                    setMood(option.value);
                  }}
                  aria-pressed={active}
                  aria-label={option.label}
                  className={`flex flex-col items-center gap-1.5 rounded-2xl border px-1 py-2.5 transition-colors ${
                    active
                      ? 'border-[var(--accent-fg)]/40 bg-[var(--accent)]/14 text-[var(--text)]'
                      : 'border-[var(--border)] bg-[var(--surface-1)] text-[var(--text-2)]'
                  }`}
                >
                  <span className={`text-base leading-none ${active ? 'text-[var(--accent-fg)]' : ''}`}>
                    {option.glyph}
                  </span>
                  <span className="text-[10px]">{option.label}</span>
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <label
            htmlFor="journal-highlight"
            className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]"
          >
            بهترین لحظهٔ امروز
          </label>
          <input
            id="journal-highlight"
            type="text"
            dir="auto"
            value={highlight}
            onChange={(event) => {
              touch();
              setHighlight(event.target.value);
            }}
            maxLength={120}
            placeholder="مثلاً: تمام‌کردن بخش سخت گزارش"
            className="luxury-input text-xs"
          />
        </div>

        <div>
          <label
            htmlFor="journal-note"
            className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]"
          >
            یادداشت
          </label>
          <textarea
            id="journal-note"
            dir="auto"
            value={note}
            onChange={(event) => {
              touch();
              setNote(event.target.value);
            }}
            rows={5}
            maxLength={2000}
            placeholder="آنچه ارزش یادآوری دارد…"
            className="luxury-input resize-none text-xs leading-6"
          />
          <p className="mt-1 text-end text-[10px] text-[var(--text-3)]">
            <Numeric value={note.length} variant="geo" /> /{' '}
            <Numeric value={2000} variant="geo" />
          </p>
        </div>
      </div>
    </ModalSheet>
  );
}
