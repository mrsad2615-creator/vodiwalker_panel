import { Numeric } from '@/components/ui/Numeric';

export interface ComparisonDatum { label: string; value: number }

/** A truthful zero-baseline comparison with readable labels and exact values. */
export function ComparisonChart({ title, subtitle, data, unit = '' }: { title: string; subtitle: string; data: ComparisonDatum[]; unit?: string }) {
  const values = data.map((item) => Number.isFinite(item.value) ? Math.max(0, item.value) : 0);
  const max = Math.max(0, ...values);
  return (
    <figure aria-label={title} className="comparison-card">
      <figcaption className="comparison-card__heading">
        <span className="comparison-card__marker" aria-hidden="true" />
        <div className="min-w-0"><h2 className="text-sm font-bold leading-6 text-[var(--text)]">{title}</h2><p className="mt-1 text-xs leading-6 text-[var(--text-2)]">{subtitle}</p></div>
      </figcaption>
      {max === 0 ? <p className="comparison-card__empty">برای این بازه هنوز داده‌ای ثبت نشده است.</p> : (
        <div className="comparison-card__rows" role="list">
          {data.map((item, index) => (
            <div key={item.label} role="listitem" aria-label={`${item.label}: ${values[index]} ${unit}`} className="comparison-card__row">
              <span className="comparison-card__label">{item.label}</span>
              <span aria-hidden="true" className="comparison-card__track" dir="rtl"><span className="comparison-card__fill" style={{ width: `${values[index] / max * 100}%` }} /></span>
              <span className="comparison-card__value"><Numeric value={values[index]} />{unit && <span className="ms-1 text-[11px] font-normal text-[var(--text-2)]">{unit}</span>}</span>
            </div>
          ))}
        </div>
      )}
      {max > 0 && <p className="comparison-card__note">طول میله‌ها از صفر تا بزرگ‌ترین مقدار مقایسه می‌شود.</p>}
    </figure>
  );
}
