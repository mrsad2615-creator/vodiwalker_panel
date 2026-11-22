import { useMemo, useState } from 'react';
import { BookOpen, ChevronLeft } from '@/components/ui/icons';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { upsertJournalEntry } from '@/lib/db';
import { useJournal } from '@/lib/queries';
import { MOODS, computeJournalStats } from '@/lib/journal';
import { formatJalaaliFull, getTodayKey } from '@/lib/date';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { JournalSheet } from './JournalSheet';

/**
 * The journal, on the home screen, in the smallest possible footprint.
 *
 * Two paths, both one tap:
 *   · the day was fine, and you only want to record *how* it felt → press a mood;
 *   · something happened and you want it written → open the sheet.
 *
 * The streak line is the reason this card earns its space: it turns a note-taking
 * feature into a habit with a visible chain.
 */
export function JournalCard() {
  const { settings, pushToast } = useApp();
  const entries = useJournal();
  const [sheetOpen, setSheetOpen] = useState(false);
  const todayKey = getTodayKey();

  const todayEntry = entries.find((entry) => entry.dateKey === todayKey) ?? null;
  const stats = useMemo(() => computeJournalStats(entries, todayKey), [entries, todayKey]);
  const dateInfo = formatJalaaliFull(todayKey);

  const quickMood = async (mood: number) => {
    void triggerHaptic('light', settings.hapticsEnabled);
    await upsertJournalEntry(todayKey, { mood });
    setSheetOpen(true);
  };

  return (
    <>
      <LuxuryCard className="space-y-3 p-4">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <BookOpen className="h-4 w-4 text-[var(--accent-fg)]" />
            <div>
              <p className="text-xs font-bold text-[var(--text)]">ژورنال امروز</p>
              <p className="mt-0.5 text-[10px] text-[var(--text-3)]">
                {dateInfo.weekday} · <Numeric value={dateInfo.day} variant="geo" />{' '}
                {dateInfo.month}
              </p>
            </div>
          </div>

          <span className="text-[10px] text-[var(--text-3)]">
            {stats.streak > 0 ? (
              <>
                <Numeric value={stats.streak} variant="geo" /> روز پیوسته
              </>
            ) : (
              'شروع تازه'
            )}
          </span>
        </div>

        {todayEntry ? (
          <button
            type="button"
            onClick={() => setSheetOpen(true)}
            className="w-full rounded-[var(--r-field)] bg-[var(--surface-1)] px-3 py-2.5 text-start transition-colors active:bg-[var(--surface-2)]"
          >
            <span className="flex items-center justify-between gap-2">
              <span className="text-[11px] font-semibold text-[var(--accent-fg)]">
                {MOODS.find((mood) => mood.value === todayEntry.mood)?.label ?? 'ثبت شد'}
              </span>
              <ChevronLeft className="h-3 w-3 text-[var(--text-3)]" />
            </span>
            <span className="mt-1 block truncate text-[11px] text-[var(--text-2)]">
              {todayEntry.highlight || todayEntry.note || 'یادداشت کوتاه امروز…'}
            </span>
          </button>
        ) : (
          <div>
            <p className="mb-2 text-[11px] text-[var(--text-2)]">امروز چطور بود؟</p>
            <div className="flex items-center justify-between gap-1.5">
              {MOODS.map((mood) => (
                <button
                  key={mood.value}
                  type="button"
                  onClick={() => {
                    void quickMood(mood.value);
                    void pushToast({
                      title: 'حال امروز ثبت شد',
                      description: 'اگر خواستی، یک خط هم بنویس.',
                      tone: 'success',
                    });
                  }}
                  aria-label={`حال ${mood.label}`}
                  className="flex-1 rounded-[var(--r-field)] border border-[var(--border)] bg-[var(--surface-1)] px-1 py-2 text-[10px] text-[var(--text-2)] transition-colors active:border-[var(--accent-fg)]/40 active:text-[var(--accent-fg)]"
                >
                  {mood.label}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="flex items-center justify-between gap-2 border-t border-[var(--border)] pt-2.5">
          <span className="text-[10px] text-[var(--text-3)]">
            {stats.weekAverage !== null ? (
              <>
                میانگین هفته: <Numeric value={stats.weekAverage} variant="geo" /> از ۵
              </>
            ) : (
              <>
                <Numeric value={stats.total} variant="geo" /> یادداشت ثبت شده
              </>
            )}
          </span>
          <button
            type="button"
            onClick={() => setSheetOpen(true)}
            className="text-[11px] font-semibold text-[var(--accent-fg)]"
          >
            {todayEntry ? 'ویرایش' : 'نوشتن'}
          </button>
        </div>
      </LuxuryCard>

      <JournalSheet
        open={sheetOpen}
        entry={todayEntry}
        onClose={() => setSheetOpen(false)}
      />
    </>
  );
}
