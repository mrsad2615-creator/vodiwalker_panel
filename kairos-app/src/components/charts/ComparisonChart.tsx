import { Numeric } from '@/components/ui/Numeric';

export interface ComparisonDatum { label: string; value: number }

/** A truthful zero-baseline comparison with readable labels and exact values. */
export function ComparisonChart({ title, subtitle, data, unit = '' }: { title: string; subtitle: string; data: ComparisonDatum[]; unit?: string }) {
  const values = data.map((item) => Number.isFinite(item.value) ? Math.max(0, item.value) : 0);
  const max = Math.max(0, ...values);
  return (
    <figure aria-label={title} className="space-y-3 rounded-2xl border border-[var(--border)] bg-[var(--surface-1)] p-4">
      <figcaption><h2 className="text-[13px] font-bold text-[var(--text)]">{title}</h2><p className="mt-1 text-[11px] leading-5 text-[var(--text-2)]">{subtitle}</p></figcaption>
      {max === 0 ? <p className="text-[11px] text-[var(--text-3)]">برای این بازه هنوز داده‌ای ثبت نشده است.</p> : (
        <div className="space-y-2.5" role="list">
          {data.map((item, index) => (
            <div key={item.label} role="listitem" aria-label={`${item.label}: ${values[index]} ${unit}`} className="grid grid-cols-[minmax(70px,auto)_1fr_auto] items-center gap-2.5">
              <span className="text-[11px] text-[var(--text-2)]">{item.label}</span>
              <span aria-hidden="true" className="h-2.5 overflow-hidden rounded-full bg-[var(--surface-3)]" dir="rtl"><span className="block h-full rounded-full bg-[var(--accent-fg)]" style={{ width: `${values[index] / max * 100}%` }} /></span>
              <span className="min-w-[2ch] text-end text-[11px] font-semibold text-[var(--text)]"><Numeric value={values[index]} />{unit && <span className="ms-1 text-[9px] font-normal text-[var(--text-2)]">{unit}</span>}</span>
            </div>
          ))}
        </div>
      )}
      {max > 0 && <p className="text-[9px] text-[var(--text-3)]">طول میله‌ها از صفر تا بزرگ‌ترین مقدار مقایسه می‌شود.</p>}
    </figure>
  );
}
