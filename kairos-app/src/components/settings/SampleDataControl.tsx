import { useState } from 'react';
import { useApp } from '@/context/AppContext';
import { chooseSampleVisibility } from '@/lib/sampleVisibility';

/** Sample rows are never erased by this control. */
export function SampleDataControl() {
  const { settings, pushToast } = useApp();
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const visible = settings.sampleVisible !== false;

  const setVisible = async (next: boolean) => {
    if (busy) return;
    setBusy(true);
    try {
      await chooseSampleVisibility(next);
      setConfirm(false);
      pushToast({ title: next ? 'نمونه‌ها نمایش داده می‌شوند' : 'نمونه‌ها پنهان شدند', description: next ? 'آمار می‌تواند ترکیبی باشد.' : 'داده‌ها و تغییراتتان حذف نشده‌اند.', tone: 'success' });
    } catch {
      pushToast({ title: 'تغییر انجام نشد', description: 'هیچ داده‌ای جایگزین نشد. دوباره تلاش کنید.', tone: 'warning' });
    } finally { setBusy(false); }
  };

  if (settings.hasSampleData) return (
    <div className="space-y-2 rounded-xl border border-[var(--accent-fg)]/20 bg-[var(--surface-1)] p-3 text-[11px] leading-6 text-[var(--text-2)]">
      <div className="flex items-center justify-between gap-3">
        <span className="font-semibold text-[var(--text)]">نمایش داده‌های نمونه</span>
        <button type="button" role="switch" aria-label="نمایش داده‌های نمونه" aria-checked={visible} disabled={busy} onClick={() => void setVisible(!visible)} className={`relative h-7 w-12 rounded-full border ${visible ? 'border-[var(--accent)] bg-[var(--accent)]' : 'border-[var(--border)] bg-[var(--surface-2)]'} disabled:opacity-50`}>
          <span aria-hidden="true" className={`absolute top-1 h-4.5 w-4.5 rounded-full bg-white transition-all ${visible ? 'left-1' : 'left-6'}`} />
        </button>
      </div>
      <p>{visible ? 'آمار می‌تواند ترکیبی از دادهٔ واقعی و ساختگی باشد.' : 'نمونه‌های نشان‌دار پنهان‌اند؛ داده‌ها و تغییراتتان حفظ شده‌اند.'}</p>
      <p>نمونه‌های قدیمیِ بدون نشان ممکن است پنهان نشوند. پشتیبان همچنان همهٔ رکوردها را نگه می‌دارد.</p>
    </div>
  );
  if (!confirm) return (
    <button type="button" onClick={() => setConfirm(true)} className="btn-ghost-luxury w-full rounded-xl px-3 py-2.5 text-start text-[11px] font-semibold text-[var(--accent-fg)]">افزودن داده‌های نمونه ←</button>
  );
  return (
    <div role="region" aria-label="تأیید داده‌های نمونه" className="space-y-2 rounded-xl border border-[var(--accent-fg)]/25 bg-[var(--surface-1)] p-3 text-[11px] leading-6 text-[var(--text)]">
      <p>وظیفه، عادت، هدف، پروژه با مرحله و کار، و سابقهٔ تمرکز نمونه کنار داده‌های شما اضافه می‌شوند؛ آمار ترکیبی خواهد شد. داده‌های فعلی پاک یا جایگزین نمی‌شوند.</p>
      <div className="flex gap-2">
        <button type="button" disabled={busy} onClick={() => void setVisible(true)} className="btn-royal rounded-xl px-3 py-2 disabled:opacity-50">{busy ? 'در حال افزودن…' : 'تأیید و افزودن نمونه'}</button>
        <button type="button" disabled={busy} onClick={() => setConfirm(false)} className="btn-ghost-luxury rounded-xl px-3 py-2">انصراف</button>
      </div>
    </div>
  );
}
