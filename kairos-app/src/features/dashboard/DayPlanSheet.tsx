import { useRef, useState } from 'react';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { db } from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import { applyDayPlanPreview, previewDayPlan, type DayPlanPreview } from '@/lib/dayPlanApply';
import type { DayPlan } from '@/lib/planner';

function nextStart(): string {
  const now = new Date();
  const next = Math.ceil((now.getHours() * 60 + now.getMinutes()) / 5) * 5;
  return next < 1440 ? `${String(Math.floor(next / 60)).padStart(2, '0')}:${String(next % 60).padStart(2, '0')}` : '';
}

export function DayPlanSheet({ open, onClose, plan, onApplied }: {
  open: boolean; onClose: () => void; plan: DayPlan; onApplied: (count: number) => void;
}) {
  const [start, setStart] = useState(nextStart);
  const [preview, setPreview] = useState<DayPlanPreview | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);

  const close = () => {
    if (busyRef.current) return;
    setPreview(null); setError(''); setStart(nextStart()); onClose();
  };
  const prepare = async () => {
    if (busyRef.current) return;
    busyRef.current = true; setBusy(true); setError('');
    try {
      const { snapshot, settings } = await db.transaction('r', [db.tasks, db.settings], async () => ({
        snapshot: await db.tasks.toArray(), settings: await db.settings.get('default'),
      }));
      const visibleIds = new Set(snapshot.filter((task) => !task.sample || settings?.sampleVisible === true).map((task) => task.id));
      const safePlan = { ...plan, blocks: plan.blocks.filter((block) => visibleIds.has(block.taskId)) };
      const next = previewDayPlan(safePlan, snapshot, getTodayKey(), start);
      setPreview(next);
    } catch {
      setPreview(null);
      setError('زمان شروع باید امروز و از همین لحظه به بعد باشد.');
    } finally { busyRef.current = false; setBusy(false); }
  };
  const apply = async () => {
    if (!preview || busyRef.current) return;
    busyRef.current = true; setBusy(true); setError('');
    try {
      const count = await applyDayPlanPreview(preview, true);
      setPreview(null); setStart(nextStart()); onClose(); onApplied(count);
    } catch {
      setPreview(null);
      setError('برنامه یا زمان تغییر کرده است؛ هیچ تغییری ثبت نشد. دوباره پیش‌نمایش بگیر.');
    } finally { busyRef.current = false; setBusy(false); }
  };

  return <ModalSheet open={open} onClose={close} title="زمان‌بندی برنامهٔ امروز" subtitle="ابتدا پیش‌نمایش، بعد ثبت با تأیید تو">
    <div className="space-y-4 text-sm leading-6 text-[var(--text)]">
      <p className="text-[var(--text-2)]">در هر نوبت حداکثر ۵۰ کار بی‌ساعت با موعد امروز زمان می‌گیرند؛ موعد کارهای عقب‌افتاده یا بدون تاریخ تغییر نمی‌کند.</p>
      <label className="block">شروع از
        <input type="time" value={start} onChange={(event) => { setStart(event.target.value); setPreview(null); }} className="luxury-input mt-1 block w-full" />
      </label>
      <p className="text-xs leading-6 text-[var(--text-2)]">زمان‌های دیگر وظایف با مدت تخمینی بررسی می‌شوند (اگر تخمین نداشته باشند ۳۰ دقیقه). رویدادهای بیرون از وظایف بررسی نمی‌شوند؛ تداخل واقعی را خودت بررسی کن.</p>
      <button type="button" disabled={busy || !start} onClick={() => void prepare()} className="btn-royal min-h-11 w-full px-4 py-2 disabled:opacity-50">پیش‌نمایش زمان‌ها</button>
      {error && <p role="alert" className="text-[var(--danger-fg)]">{error}</p>}
      {preview && <section aria-label="پیش‌نمایش برنامه" className="space-y-3 rounded-2xl border border-[var(--border-strong)] bg-[var(--surface-2)] p-4">
        {preview.suggested.length ? <ol className="space-y-2">{preview.suggested.map((item) => <li key={item.taskId} className="flex justify-between gap-3"><span>{item.title}</span><span className="shrink-0"><bdi dir="ltr">{item.time.replace(/\d/g, (digit) => '۰۱۲۳۴۵۶۷۸۹'[Number(digit)])}</bdi> · {item.minutes.toLocaleString('fa-IR')} دقیقه</span></li>)}</ol> : <p>کار واجد شرایطی برای زمان‌بندی پیدا نشد.</p>}
        {preview.skipped > 0 && <p className="text-xs text-[var(--text-2)]">{preview.skipped} مورد زمان نگرفت؛ ممکن است تاریخ یا ساعت داشته باشد یا تا پایان روز جا نشود.</p>}
        {preview.suggested.length > 0 && <button type="button" disabled={busy} onClick={() => void apply()} className="btn-royal min-h-11 w-full px-4 py-2 disabled:opacity-50">{busy ? 'در حال ثبت…' : 'تأیید و ثبت زمان‌ها'}</button>}
      </section>}
    </div>
  </ModalSheet>;
}
