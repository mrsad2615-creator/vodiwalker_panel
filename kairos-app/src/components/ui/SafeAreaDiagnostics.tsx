import { useEffect, useState } from 'react';
import { getSafeAreaReport, syncSafeArea, type SafeAreaReport } from '@/lib/safeArea';

/**
 * Collapsed technical read-out of the system-bar insets.
 * It exists so a notch / status-bar problem can be diagnosed on a real device
 * without a cable: the numbers show which source (env / Capacitor) reported them.
 */
export function SafeAreaDiagnostics() {
  const [report, setReport] = useState<SafeAreaReport | null>(null);

  useEffect(() => {
    syncSafeArea();
    setReport(getSafeAreaReport());
  }, []);

  if (!report) return null;
  const { applied, env, capacitor, platform, webViewMajor } = report;

  return (
    <details className="rounded-[var(--r-card)] border border-[var(--border)] bg-[var(--surface-1)] px-4 py-3">
      <summary className="cursor-pointer text-[11.5px] font-medium text-[var(--text-2)]">
        اطلاعات فنی نوار سیستم
      </summary>
      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1.5 text-[10.5px] text-[var(--text-3)]">
        <dt>ناچ بالا</dt>
        <dd className="num-geo text-end text-[var(--text-2)]">{applied.top}px</dd>
        <dt>ناچ پایین</dt>
        <dd className="num-geo text-end text-[var(--text-2)]">{applied.bottom}px</dd>
        <dt>چپ / راست</dt>
        <dd className="num-geo text-end text-[var(--text-2)]">
          {applied.left} / {applied.right}px
        </dd>
        <dt>گزارش env()</dt>
        <dd className="num-geo text-end text-[var(--text-3)]">
          {env.top} · {env.bottom}
        </dd>
        <dt>گزارش Capacitor</dt>
        <dd className="num-geo text-end text-[var(--text-3)]">
          {capacitor.top} · {capacitor.bottom}
        </dd>
        <dt>حالت چیدمان</dt>
        <dd className="text-end text-[var(--text-3)]">
          {applied.top + applied.bottom > 0 ? 'CSS (edge-to-edge)' : 'بومی (پدینگ WebView)'}
        </dd>
        <dt>پلتفرم</dt>
        <dd className="num-geo text-end text-[var(--text-3)]">
          {platform}
          {webViewMajor ? ` · WebView ${webViewMajor}` : ''}
        </dd>
      </dl>
    </details>
  );
}
