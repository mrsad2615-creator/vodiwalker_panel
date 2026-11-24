import { useMemo, useState } from 'react';
import { Heart, Sparkles, ArrowLeftRight, CheckSquare, Timer } from '@/components/ui/icons';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { SectionHeader } from '@/components/ui/SectionHeader';
import { useApp } from '@/context/AppContext';
import { getTodayKey } from '@/lib/date';
import { updateSettings } from '@/lib/db';
import { dailyLineIndex, MOTIVATION_CATEGORIES, MOTIVATION_LINES, type MotivationCategory } from '@/lib/motivation';
import { sectionEyebrow } from '@/lib/sections';

type Filter = 'all' | 'favorites' | MotivationCategory;

export function MotivationPage() {
  const { settings, setActiveTab, pushToast } = useApp();
  const [filter, setFilter] = useState<Filter>('all');
  const [offset, setOffset] = useState(0);
  const [saving, setSaving] = useState(false);
  const favorites = useMemo(() => Array.isArray(settings.motivationFavorites) ? settings.motivationFavorites : [], [settings.motivationFavorites]);
  const lines = useMemo(() => MOTIVATION_LINES.filter((line) =>
    filter === 'all' || (filter === 'favorites' ? favorites.includes(line.id) : line.category === filter)
  ), [filter, favorites]);
  const line = lines.length ? lines[(dailyLineIndex(getTodayKey(), lines.length) + offset) % lines.length] : undefined;

  const select = (value: Filter) => { setFilter(value); setOffset(0); };
  const toggleFavorite = async () => {
    if (!line || saving) return;
    setSaving(true);
    try {
      const next = favorites.includes(line.id) ? favorites.filter((id) => id !== line.id) : [...favorites, line.id];
      await updateSettings({ motivationFavorites: next });
      pushToast({ title: next.includes(line.id) ? 'به علاقه‌مندی‌ها اضافه شد' : 'از علاقه‌مندی‌ها برداشته شد', tone: 'success' });
    } catch {
      pushToast({ title: 'ذخیره انجام نشد', description: 'دوباره تلاش کنید.', tone: 'warning' });
    } finally { setSaving(false); }
  };

  return (
    <div className="space-y-4 pb-4" data-testid="motivation-page">
      <SectionHeader eyebrow={sectionEyebrow('motivation')} title="نیروی حرکت" meta="کلمات محکم، قدم‌های واقعی" />
      <p className="text-[11px] leading-6 text-[var(--text-2)]">جمله‌هایی برای شروع، ادامه و بازیابی؛ بدون فشار و بدون نیاز به اینترنت.</p>
      <div className="flex flex-wrap gap-1.5" aria-label="دسته‌بندی جمله‌ها">
        {([{ id: 'all' as const, label: 'همه' }, ...MOTIVATION_CATEGORIES, { id: 'favorites' as const, label: 'علاقه‌مندی‌ها' }]).map((item) => (
          <button key={item.id} type="button" aria-pressed={filter === item.id} onClick={() => select(item.id)}
            className={`rounded-full border px-3 py-2 text-[11px] ${filter === item.id ? 'border-[var(--accent-fg)] bg-[var(--accent)] text-[var(--accent-on)]' : 'border-[var(--border)] text-[var(--text-2)]'}`}>
            {item.label}
          </button>
        ))}
      </div>
      {line ? (
        <LuxuryCard className="space-y-5 p-5">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-[10px] font-semibold text-[var(--accent-fg)]"><Sparkles size={16} /> {MOTIVATION_CATEGORIES.find((item) => item.id === line.category)?.label} · الهام امروز</span>
            <button type="button" disabled={saving} onClick={() => void toggleFavorite()} aria-label={favorites.includes(line.id) ? 'حذف از علاقه‌مندی‌ها' : 'افزودن به علاقه‌مندی‌ها'} aria-pressed={favorites.includes(line.id)} className="grid h-10 w-10 place-items-center rounded-full border border-[var(--border)] text-[var(--accent-fg)] disabled:opacity-50">
              <Heart size={19} weight={favorites.includes(line.id) ? 'fill' : 'regular'} />
            </button>
          </div>
          <blockquote className="text-[20px] font-bold leading-[2] text-[var(--text)]">{line.text}</blockquote>
          <div className="rounded-xl border border-[var(--border)] bg-[var(--surface-1)] p-3">
            <p className="text-[10px] font-semibold text-[var(--accent-fg)]">یک قدم عملی</p>
            <p className="mt-1 text-[12px] leading-6 text-[var(--text-2)]">{line.action}</p>
          </div>
          <button type="button" onClick={() => setOffset((value) => value + 1)} className="btn-ghost-luxury flex w-full items-center justify-center gap-2 rounded-xl py-3 text-xs font-semibold text-[var(--text)]">
            <ArrowLeftRight size={16} /> جملهٔ بعدی
          </button>
        </LuxuryCard>
      ) : (
        <LuxuryCard className="space-y-2 p-5"><p className="text-sm font-bold text-[var(--text)]">هنوز جمله‌ای ذخیره نکرده‌ای</p><p className="text-xs leading-6 text-[var(--text-2)]">از دسته‌های دیگر جمله‌ای را با دکمهٔ قلب به علاقه‌مندی‌ها اضافه کن.</p><button type="button" onClick={() => select('all')} className="text-xs text-[var(--accent-fg)]">دیدن همهٔ جمله‌ها ←</button></LuxuryCard>
      )}
      <div className="grid grid-cols-2 gap-2">
        <button type="button" onClick={() => setActiveTab('tasks')} className="rail flex items-center gap-2 p-3 text-start text-xs text-[var(--text)]"><CheckSquare size={18} className="text-[var(--accent-fg)]" /> رفتن به وظایف</button>
        <button type="button" onClick={() => setActiveTab('focus')} className="rail flex items-center gap-2 p-3 text-start text-xs text-[var(--text)]"><Timer size={18} className="text-[var(--accent-fg)]" /> شروع تمرکز</button>
      </div>
    </div>
  );
}
