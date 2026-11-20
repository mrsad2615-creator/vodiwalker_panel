import { useEffect, useState } from 'react';
import {
  getManualInsets,
  getSafeAreaReport,
  setManualInsets,
  syncSafeArea,
  type SafeAreaReport,
} from '@/lib/safeArea';

/**
 * System-bar panel: a live read-out of the notch insets plus a manual
 * calibration. The app now insets itself natively (MainActivity), so the
 * automatic path is the normal one — the sliders exist so a user on an unusual
 * ROM can still make the layout sit exactly right without waiting for a build.
 */
export function SafeAreaDiagnostics() {
  const [report, setReport] = useState<SafeAreaReport | null>(null);
  const [manual, setManual] = useState(getManualInsets());

  useEffect(() => {
    syncSafeArea();
    setReport(getSafeAreaReport());
    setManual(getManualInsets());
  }, []);

  const apply = (pair: { top: number; bottom: number }) => {
    setManual(pair);
    setManualInsets(pair);
    setReport(getSafeAreaReport());
  };

  if (!report) return null;
  const { applied, env, capacitor, platform, webViewMajor } = report;

  return (
    <details className="rounded-[var(--r-card)] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3">
      <summary className="cursor-pointer text-[11.5px] font-medium text-[var(--text-2)]">
        نوار سیستم و ناچ
      </summary>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1.5 text-[10.5px] text-[var(--text-3)]">
        <dt>فاصلهٔ بالا</dt>
        <dd className="num-geo text-end text-[var(--text-2)]">{applied.top}px</dd>
        <dt>فاصلهٔ پایین</dt>
        <dd className="num-geo text-end text-[var(--text-2)]">{applied.bottom}px</dd>
        <dt>گزارش env()</dt>
        <dd className="num-geo text-end">{env.top} · {env.bottom}</dd>
        <dt>گزارش Capacitor</dt>
        <dd className="num-geo text-end">{capacitor.top} · {capacitor.bottom}</dd>
        <dt>پلتفرم</dt>
        <dd className="num-geo text-end">
          {platform}
          {webViewMajor ? ` · WebView ${webViewMajor}` : ''}
        </dd>
      </dl>

      <div className="mt-4 space-y-3">
        <p className="text-[10.5px] leading-5 text-[var(--text-3)]">
          اگر محتوا هنوز زیر نوار وضعیت یا نوار ژست می‌رود، این دو مقدار را زیاد کنید تا
          فاصله دقیقاً درست شود.
        </p>

        <label className="block">
          <span className="flex items-center justify-between text-[10.5px] text-[var(--text-2)]">
            حاشیهٔ بالا
            <span className="num-geo text-[var(--text-3)]">{manual.top}px</span>
          </span>
          <input
            type="range"
            min={0}
            max={64}
            step={1}
            value={manual.top}
            onChange={(event) => apply({ ...manual, top: Number(event.target.value) })}
            className="mt-1 h-1.5 w-full accent-[var(--accent)]"
            aria-label="حاشیهٔ بالای دستی"
          />
        </label>

        <label className="block">
          <span className="flex items-center justify-between text-[10.5px] text-[var(--text-2)]">
            حاشیهٔ پایین
            <span className="num-geo text-[var(--text-3)]">{manual.bottom}px</span>
          </span>
          <input
            type="range"
            min={0}
            max={64}
            step={1}
            value={manual.bottom}
            onChange={(event) => apply({ ...manual, bottom: Number(event.target.value) })}
            className="mt-1 h-1.5 w-full accent-[var(--accent)]"
            aria-label="حاشیهٔ پایین دستی"
          />
        </label>

        <button
          type="button"
          onClick={() => apply({ top: 0, bottom: 0 })}
          className="w-full rounded-[10px] border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-[11px] text-[var(--text-2)]"
        >
          بازگشت به حالت خودکار
        </button>
      </div>
    </details>
  );
}
