import { useState } from 'react';
import { chooseSampleVisibility } from '@/lib/sampleVisibility';

export function SampleSetup() {
  const [selected, setSelected] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(false);
  const save = async () => {
    if (busy) return;
    setBusy(true); setError(false);
    try { await chooseSampleVisibility(selected, true); }
    catch { setError(true); }
    finally { setBusy(false); }
  };
  return (
    <main className="flex min-h-[100dvh] items-center justify-center bg-[var(--bg)] px-5 py-10" dir="rtl">
      <section aria-labelledby="sample-setup-title" className="w-full max-w-[430px] space-y-5 rounded-3xl border border-[var(--border)] bg-[var(--surface-1)] p-6 shadow-2xl">
        <div><p className="text-xs font-semibold tracking-wide text-[var(--accent-fg)]">KAIROS</p><h1 id="sample-setup-title" className="mt-2 text-xl font-bold text-[var(--text)]">با داده‌های نمونه شروع کنیم؟</h1></div>
        <p className="text-sm leading-7 text-[var(--text-2)]">نمونه‌ها ساختگی‌اند و شامل پروژه، وظیفه، عادت و سابقه می‌شوند. اگر روشن باشند در کنار داده‌های واقعی و در آمار دیده می‌شوند؛ هیچ دادهٔ موجودی جایگزین نمی‌شود.</p>
        <label className="flex items-center justify-between gap-4 rounded-2xl border border-[var(--border)] bg-[var(--surface-2)] p-4 text-sm text-[var(--text)]">
          <span><span className="block font-semibold">نمایش دادهٔ نمونه</span><span className="mt-1 block text-xs leading-6 text-[var(--text-2)]">بعداً در تنظیمات می‌توانید تغییر دهید.</span></span>
          <input type="checkbox" checked={selected} onChange={(event) => setSelected(event.target.checked)} className="h-5 w-5 accent-[var(--accent)]" />
        </label>
        <p className="text-xs leading-6 text-[var(--text-3)]">خاموش‌کردن، نمونه‌های نشان‌دار را پنهان می‌کند، نه حذف. نمونه‌های خیلی قدیمی که نشان ندارند ممکن است پنهان نشوند.</p>
        {error && <p role="alert" className="text-xs leading-6 text-[var(--danger-fg)]">ذخیره انجام نشد؛ اطلاعات موجود تغییر نکرده است. دوباره تلاش کنید یا نمونه را خاموش کنید.</p>}
        <button type="button" onClick={() => void save()} disabled={busy} className="btn-royal w-full rounded-2xl py-3 text-sm font-bold disabled:opacity-50">{busy ? 'در حال آماده‌سازی…' : 'تأیید و ورود'}</button>
      </section>
    </main>
  );
}
