import { useState } from 'react';
import { useApp } from '@/context/AppContext';
import { populateStarterWorkspace } from '@/lib/seed';

/** Explicit consent before mixing fictional events into personal analytics. */
export function SampleDataControl() {
  const { settings, pushToast } = useApp();
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);

  const add = async () => {
    if (busy) return;
    setBusy(true);
    try {
      await populateStarterWorkspace();
      setConfirm(false);
      pushToast({ title: 'داده‌های نمونه اضافه شدند', description: 'سابقهٔ ساختگی در تحلیل‌ها با داده‌های واقعی ترکیب می‌شود.', tone: 'success' });
    } catch (error) {
      const alreadyLoaded = error instanceof Error && error.message === 'sample-already-loaded';
      pushToast({
        title: alreadyLoaded ? 'دادهٔ نمونه از قبل وجود دارد' : 'افزودن نمونه انجام نشد',
        description: alreadyLoaded ? 'برای جلوگیری از تکرار، نمونه دوباره اضافه نشد.' : 'هیچ رکوردی جایگزین نشد. دوباره تلاش کنید.',
        tone: 'warning',
      });
    } finally {
      setBusy(false);
    }
  };

  if (settings.hasSampleData) return (
    <p className="rounded-xl border border-[var(--accent-fg)]/20 bg-[var(--surface-1)] p-3 text-[11px] leading-6 text-[var(--text-2)]">
      داده‌های نمونه در فضای شما هستند. نتایج و تحلیل‌ها ترکیبی از دادهٔ واقعی و ساختگی‌اند.
    </p>
  );
  if (!confirm) return (
    <button type="button" onClick={() => setConfirm(true)} className="btn-ghost-luxury w-full rounded-xl px-3 py-2.5 text-start text-[11px] font-semibold text-[var(--accent-fg)]">
      افزودن داده‌های نمونه ←
    </button>
  );
  return (
    <div role="region" aria-label="تأیید داده‌های نمونه" className="space-y-2 rounded-xl border border-[var(--accent-fg)]/25 bg-[var(--surface-1)] p-3 text-[11px] leading-6 text-[var(--text)]">
      <p>وظیفه، عادت، هدف و سابقهٔ تمرکز نمونه کنار داده‌های شما اضافه می‌شوند؛ آمار ترکیبی خواهد شد. داده‌های فعلی پاک یا جایگزین نمی‌شوند.</p>
      <div className="flex gap-2">
        <button type="button" disabled={busy} onClick={() => void add()} className="btn-royal rounded-xl px-3 py-2 disabled:opacity-50">{busy ? 'در حال افزودن…' : 'تأیید و افزودن نمونه'}</button>
        <button type="button" disabled={busy} onClick={() => setConfirm(false)} className="btn-ghost-luxury rounded-xl px-3 py-2">انصراف</button>
      </div>
    </div>
  );
}
