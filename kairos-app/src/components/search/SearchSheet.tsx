import { useEffect, useMemo, useRef, useState } from 'react';
import { Search, CornerDownLeft, X } from '@/components/ui/icons';
import { useGoals, useHabits, useTasks } from '@/lib/queries';
import { buildSearchIndex, searchEverything, type SearchResult } from '@/lib/search';
import { useApp } from '@/context/AppContext';

const KIND_LABEL: Record<SearchResult['kind'], string> = {
  section: 'بخش',
  task: 'وظیفه',
  habit: 'عادت',
  goal: 'هدف',
};

interface SearchSheetProps {
  open: boolean;
  onClose: () => void;
}

/**
 * The whole workspace behind one box.
 *
 * Keyboard-first, because that is how a search is actually used: type, arrow
 * down, Enter. On a phone the same list is a thumb list. Results are grouped by
 * nothing but relevance — a section you typed by name sits next to the task that
 * matched it, which is exactly the point of a global box.
 */
export function SearchSheet({ open, onClose }: SearchSheetProps) {
  const { setActiveTab } = useApp();
  const tasks = useTasks();
  const habits = useHabits();
  const goals = useGoals();

  const [query, setQuery] = useState('');
  const [activeIndex, setActiveIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement | null>(null);

  const index = useMemo(
    () => buildSearchIndex({ tasks, habits, goals }),
    [tasks, habits, goals]
  );
  const results = useMemo(() => searchEverything(query, index), [query, index]);

  useEffect(() => {
    if (!open) return;
    setQuery('');
    setActiveIndex(0);
    // Focus after the sheet is painted, otherwise mobile keyboards open and
    // immediately close as the panel is still mounting.
    const id = window.setTimeout(() => inputRef.current?.focus(), 40);
    return () => window.clearTimeout(id);
  }, [open]);

  useEffect(() => {
    setActiveIndex(0);
  }, [query]);

  const choose = (result: SearchResult) => {
    setActiveTab(result.tab);
    onClose();
  };

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      return;
    }
    if (results.length === 0) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActiveIndex((i) => (i + 1) % results.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActiveIndex((i) => (i - 1 + results.length) % results.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      const picked = results[activeIndex];
      if (picked) choose(picked);
    }
  };

  if (!open) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="جست‌وجوی سراسری"
      className="fixed inset-0 z-[90] flex flex-col bg-[color-mix(in_srgb,var(--bg)_88%,transparent)] px-4 pt-[calc(var(--safe-top)+1rem)] backdrop-blur-md"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="mx-auto w-full max-w-[440px]">
        <div className="flex items-center gap-2 rounded-2xl border border-[var(--border-strong)] bg-[var(--surface-1)] px-3 py-2">
          <Search className="h-4 w-4 shrink-0 text-[var(--text-3)]" />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={onKeyDown}
            aria-label="جست‌وجو در وظایف، عادت‌ها، اهداف و بخش‌ها"
            placeholder="وظیفه، عادت، هدف یا بخش…"
            className="min-w-0 flex-1 bg-transparent text-[13px] text-[var(--text)] outline-none placeholder:text-[var(--text-3)]"
          />
          <button
            type="button"
            onClick={onClose}
            aria-label="بستن جست‌وجو"
            className="grid h-6 w-6 shrink-0 place-items-center rounded-full text-[var(--text-3)] hover:text-[var(--text)]"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>

        {query.trim() === '' ? (
          <p className="mt-3 px-1 text-[10.5px] leading-relaxed text-[var(--text-3)]">
            همه‌چیز را در یک جا جست‌وجو کنید — نام بخش‌ها هم نتیجه می‌دهند. با
            <span className="mx-1 num-geo rounded border border-[var(--border)] px-1 text-[9.5px]">
              ↑ ↓
            </span>
            حرکت کنید و با
            <span className="mx-1 num-geo rounded border border-[var(--border)] px-1 text-[9.5px]">
              Enter
            </span>
            بروید.
          </p>
        ) : results.length === 0 ? (
          <p className="mt-4 px-1 text-[11.5px] text-[var(--text-3)]">
            چیزی با «{query}» پیدا نشد.
          </p>
        ) : (
          <ul
            role="listbox"
            aria-label="نتایج جست‌وجو"
            className="mt-2 max-h-[62vh] overflow-y-auto rounded-2xl border border-[var(--border)] bg-[var(--surface-1)]"
          >
            {results.map((result, i) => {
              const active = i === activeIndex;
              return (
                <li key={result.id} role="option" aria-selected={active}>
                  <button
                    type="button"
                    onMouseEnter={() => setActiveIndex(i)}
                    onClick={() => choose(result)}
                    className={`flex w-full items-center gap-3 border-b border-[var(--border)] px-3 py-2.5 text-right transition-colors last:border-0 ${
                      active ? 'bg-[color-mix(in_srgb,var(--accent)_14%,transparent)]' : ''
                    }`}
                  >
                    <span className="flex min-w-0 flex-1 flex-col">
                      <span className="truncate text-[12.5px] text-[var(--text)]">
                        {result.title}
                      </span>
                      {result.subtitle && (
                        <span className="truncate text-[9.5px] text-[var(--text-3)]">
                          {result.subtitle}
                        </span>
                      )}
                    </span>
                    <span className="shrink-0 rounded-full border border-[var(--border)] px-1.5 py-0.5 text-[9px] text-[var(--text-3)]">
                      {KIND_LABEL[result.kind]}
                    </span>
                    {active && <CornerDownLeft className="h-3 w-3 shrink-0 text-[var(--accent-fg)]" />}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
