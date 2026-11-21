import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Check, Palette, RotateCcw, Sparkles } from '@/components/ui/icons';
import { useApp } from '@/context/AppContext';
import { updateSettings } from '@/lib/db';
import { triggerHaptic } from '@/lib/native';
import {
  ACCENT_PRESETS,
  BACKGROUND_PRESETS,
  DEFAULT_ACCENT,
  DEFAULT_APPEARANCE,
  MOTION_LABELS,
  applyAppearance,
  cacheAppearance,
  contrastRatio,
  deriveAccent,
  isHexColor,
  normalizeAppearance,
  normalizeHex,
  readCachedAppearance,
  type MotionLevel,
} from '@/lib/theme';

/**
 * Appearance Studio — the only place in the app that writes the accent.
 *
 * Everything here is real: the swatch recolours the whole product instantly
 * (CSS custom properties on :root), the preview card is painted with the same
 * tokens as the live UI (so it can never lie), the contrast line is computed
 * from the actual WCAG formula, and the choice is persisted to Dexie + a
 * synchronous cache so the next cold start paints with it immediately.
 */
export function AppearanceStudio() {
  const { settings, pushToast } = useApp();
  const persisted = useMemo(
    () =>
      normalizeAppearance({
        accent: settings.accentColor,
        backgroundId: settings.backgroundId,
        motionLevel: settings.motionLevel,
      }),
    [settings.accentColor, settings.backgroundId, settings.motionLevel]
  );

  const [accent, setAccent] = useState(persisted.accent);
  const [hexDraft, setHexDraft] = useState(persisted.accent);
  const [motion, setMotion] = useState<MotionLevel>(persisted.motionLevel);
  const [backgroundId, setBackgroundId] = useState(persisted.backgroundId);
  const saveTimer = useRef<number | null>(null);

  // Keep local controls in sync when the DB row changes (other device/tab).
  useEffect(() => {
    setAccent(persisted.accent);
    setHexDraft(persisted.accent);
    setMotion(persisted.motionLevel);
    setBackgroundId(persisted.backgroundId);
  }, [persisted.accent, persisted.motionLevel, persisted.backgroundId]);

  const derived = useMemo(() => deriveAccent(accent), [accent]);
  const onInk = contrastRatio(derived.fg, 'var(--bg)');
  const onFill = contrastRatio(derived.on, derived.accent);
  const isBrand = accent.toUpperCase() === DEFAULT_ACCENT.toUpperCase();

  const persist = useCallback(
    (next: { accent: string; backgroundId: typeof backgroundId; motionLevel: MotionLevel }) => {
      const appearance = normalizeAppearance(next);
      applyAppearance(appearance);
      cacheAppearance(appearance);
      if (saveTimer.current) window.clearTimeout(saveTimer.current);
      saveTimer.current = window.setTimeout(() => {
        void updateSettings({
          accentColor: appearance.accent,
          backgroundId: appearance.backgroundId,
          motionLevel: appearance.motionLevel,
        });
      }, 240);
    },
    []
  );

  useEffect(() => {
    return () => {
      if (saveTimer.current) window.clearTimeout(saveTimer.current);
    };
  }, []);

  const chooseAccent = (hex: string, silent = false) => {
    const normalized = normalizeHex(hex);
    if (!normalized) return;
    setAccent(normalized);
    setHexDraft(normalized);
    persist({ accent: normalized, backgroundId, motionLevel: motion });
    if (!silent) void triggerHaptic('light', settings.hapticsEnabled);
  };

  const chooseBackground = (id: typeof backgroundId) => {
    setBackgroundId(id);
    persist({ accent, backgroundId: id, motionLevel: motion });
    void triggerHaptic('light', settings.hapticsEnabled);
  };

  const chooseMotion = (level: MotionLevel) => {
    setMotion(level);
    persist({ accent, backgroundId, motionLevel: level });
    void triggerHaptic('light', settings.hapticsEnabled);
  };

  const resetAll = () => {
    setAccent(DEFAULT_APPEARANCE.accent);
    setHexDraft(DEFAULT_APPEARANCE.accent);
    setBackgroundId(DEFAULT_APPEARANCE.backgroundId);
    setMotion(DEFAULT_APPEARANCE.motionLevel);
    persist(DEFAULT_APPEARANCE);
    void triggerHaptic('success', settings.hapticsEnabled);
    pushToast({ title: 'ظاهر به برند اصلی کایروس برگشت', tone: 'default' });
  };

  // If the cache disagrees with the DB (fresh install), trust the DB row.
  useEffect(() => {
    const cached = readCachedAppearance();
    if (cached.accent !== persisted.accent || cached.backgroundId !== persisted.backgroundId) {
      applyAppearance(persisted);
      cacheAppearance(persisted);
    }
  }, [persisted]);

  const previewSurface = 'var(--surface-2)';

  return (
    <section aria-labelledby="appearance-title" className="space-y-3">
      <header className="flex items-baseline justify-between gap-2">
        <h2 id="appearance-title" className="flex items-center gap-1.5 text-[13px] font-bold text-[var(--text)]">
          <Palette className="h-4 w-4 text-[var(--accent-fg)]" strokeWidth={1.9} />
          استودیوی ظاهر
        </h2>
        <span className="font-display-caps text-[9px] text-[var(--text-3)]">THEME</span>
      </header>

      {/* ---------- Live preview: same tokens as the product ---------- */}
      <div
        className="overflow-hidden rounded-[var(--r-hero)] border border-[var(--border-strong)]"
        style={{ background: previewSurface }}
      >
        <div className="flex items-center justify-between gap-3 px-3.5 pb-2.5 pt-3">
          <div>
            <p className="text-[10px] font-medium text-[var(--text-2)]">پیش‌نمایش زنده</p>
            <p className="font-display mt-0.5 text-[19px] font-semibold leading-none text-[var(--text)]">
              {isBrand ? 'KAIROS' : derived.accent}
            </p>
          </div>
          <span
            className="grid h-10 w-10 place-items-center rounded-full text-[11px] font-bold"
            style={{ background: derived.accent, color: derived.on }}
          >
            <Sparkles className="h-4 w-4" strokeWidth={2} />
          </span>
        </div>

        <div className="grid grid-cols-3 gap-1.5 px-3.5">
          {[38, 62, 84].map((h, i) => (
            <div key={h} className="flex h-[54px] items-end">
              <span
                className="w-full rounded-t-[4px]"
                style={{
                  height: `${h}%`,
                  background:
                    i === 2
                      ? `linear-gradient(180deg, ${derived.fg} 0%, ${derived.accent} 100%)`
                      : 'color-mix(in srgb, var(--text) 12%, transparent)',
                }}
              />
            </div>
          ))}
        </div>

        <div className="flex items-center justify-between gap-2 px-3.5 pb-3.5 pt-2.5">
          <button
            type="button"
            tabIndex={-1}
            aria-hidden="true"
            className="pointer-events-none rounded-[var(--r-field)] px-3 py-1.5 text-[11px] font-bold"
            style={{ background: derived.accent, color: derived.on }}
          >
            شروع تمرکز
          </button>
          <span className="text-[9.5px] text-[var(--text-3)]">
            خوانایی متن روی اکسنت: <span className="num-geo">{onFill.toFixed(1)}:1</span>
          </span>
        </div>
      </div>

      {/* ---------- Accent swatches ---------- */}
      <div className="grid grid-cols-8 gap-1.5" role="group" aria-label="رنگ‌های آماده">
        {ACCENT_PRESETS.map((preset) => {
          const active = preset.hex.toUpperCase() === accent.toUpperCase();
          return (
            <button
              key={preset.id}
              type="button"
              onClick={() => chooseAccent(preset.hex)}
              aria-label={preset.label}
              aria-pressed={active}
              className="relative grid aspect-square place-items-center rounded-full transition-transform active:scale-90"
              style={{
                background: preset.hex,
                boxShadow: active
                  ? '0 0 0 2px var(--bg), 0 0 0 3.5px var(--text)'
                  : 'inset 0 0 0 1px rgba(255,255,255,0.14)',
              }}
            >
              {active && <Check className="h-3 w-3 text-white" strokeWidth={3} />}
            </button>
          );
        })}
      </div>

      {/* ---------- Custom colour ---------- */}
      <div className="flex items-center gap-2">
        <label
          className="relative grid h-9 w-9 shrink-0 place-items-center overflow-hidden rounded-[10px] border border-[var(--border-strong)]"
          style={{ background: derived.accent }}
        >
          <input
            type="color"
            value={accent}
            onChange={(event) => chooseAccent(event.target.value, true)}
            aria-label="انتخاب رنگ سفارشی"
            className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
          />
        </label>
        <input
          type="text"
          value={hexDraft}
          onChange={(event) => {
            const next = event.target.value.toUpperCase();
            setHexDraft(next);
            if (isHexColor(next)) chooseAccent(next, true);
          }}
          onBlur={() => setHexDraft(accent)}
          dir="ltr"
          spellCheck={false}
          aria-label="کد رنگ"
          className="num-geo min-w-0 flex-1 rounded-[var(--r-field)] border border-[var(--border)] bg-[var(--surface-input)] px-2.5 py-2 text-[12px] tracking-wider text-[var(--text)] outline-none focus:border-[var(--accent)]"
        />
        <button
          type="button"
          onClick={resetAll}
          className="flex items-center gap-1 rounded-[var(--r-field)] border border-[var(--border)] px-2.5 py-2 text-[10.5px] font-medium text-[var(--text-2)] transition hover:text-[var(--text)]"
        >
          <RotateCcw className="h-3 w-3" strokeWidth={2} />
          برند
        </button>
      </div>
      <p className="text-[9.5px] leading-4 text-[var(--text-3)]">
        رنگ متن روی بنفش/اکسنت به‌صورت خودکار انتخاب می‌شود؛ خوانایی متن‌های روی پس‌زمینه{' '}
        <span className="num-geo">{onInk.toFixed(1)}:1</span> است (حد استاندارد ۴٫۵:۱).
      </p>

      {/* ---------- Background environments ---------- */}
      <div className="space-y-1.5 pt-1">
        <p className="text-[11px] font-semibold text-[var(--text-2)]">محیط پس‌زمینه</p>
        <div className="grid grid-cols-3 gap-1.5">
          {BACKGROUND_PRESETS.map((bg) => {
            const active = bg.id === backgroundId;
            return (
              <button
                key={bg.id}
                type="button"
                onClick={() => chooseBackground(bg.id)}
                aria-pressed={active}
                className="group relative overflow-hidden rounded-[12px] border text-start transition"
                style={{
                  borderColor: active ? 'var(--accent-fg)' : 'var(--border)',
                  boxShadow: active ? '0 0 0 1px var(--accent-fg)' : 'none',
                }}
              >
                <span
                  aria-hidden="true"
                  data-bg={bg.id}
                  className="block h-11 w-full"
                  style={{ background: 'var(--bg)' }}
                >
                  <span
                    className="block h-full w-full"
                    style={{ background: 'var(--atmosphere)', backgroundSize: 'cover' }}
                  />
                </span>
                <span className="block px-2 py-1.5">
                  <span className="block text-[10px] font-semibold text-[var(--text)]">{bg.label}</span>
                  <span className="block truncate text-[8.5px] text-[var(--text-3)]">{bg.hint}</span>
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {/* ---------- Motion level ---------- */}
      <div className="space-y-1.5 pt-1">
        <p className="text-[11px] font-semibold text-[var(--text-2)]">سطح موشن</p>
        <div className="grid grid-cols-3 gap-1.5">
          {(Object.keys(MOTION_LABELS) as MotionLevel[]).map((level) => {
            const active = level === motion;
            return (
              <button
                key={level}
                type="button"
                onClick={() => chooseMotion(level)}
                aria-pressed={active}
                className={`rounded-[12px] border px-2 py-2 text-center transition ${
                  active
                    ? 'border-[var(--accent)] bg-[var(--accent-soft)]'
                    : 'border-[var(--border)] bg-[var(--surface-1)]'
                }`}
              >
                <span
                  className={`font-display-caps block text-[9px] font-bold ${
                    active ? 'text-[var(--accent-fg)]' : 'text-[var(--text-2)]'
                  }`}
                >
                  {MOTION_LABELS[level].label}
                </span>
                <span className="mt-0.5 block text-[8px] leading-3 text-[var(--text-3)]">
                  {MOTION_LABELS[level].hint}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}
