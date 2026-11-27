/**
 * KAIROS theme engine.
 *
 * One accent enters, a coherent, contrast-checked palette comes out. All of the
 * colour maths is done in OKLCH (perceptually uniform), so a lighter or darker
 * derivation never shifts hue the way naive HSL lightening does. Everything the
 * UI paints is driven by CSS custom properties, which means changing the accent
 * re-colours the whole app without touching a single layout class.
 *
 * The engine is pure (no DOM) except for `applyAppearance`, which writes the
 * tokens to :root. That keeps the maths testable in jsdom.
 */

export type MotionLevel = 'standard' | 'cinematic' | 'ultra';

export type BackgroundId = 'obsidian' | 'nebula' | 'aurora' | 'alpine' | 'ember' | 'graphite';

export interface AccentPreset {
  id: string;
  label: string;
  hex: string;
}

/** Curated starting points. The user can still pick any colour. */
export const ACCENT_PRESETS: AccentPreset[] = [
  { id: 'kairos', label: 'بنفش کایروس', hex: '#6D4AFF' },
  { id: 'indigo', label: 'نیلی الکتریک', hex: '#4B6BFF' },
  { id: 'aurora', label: 'فیروزه‌ای', hex: '#0FB5A6' },
  { id: 'jade', label: 'یشمی', hex: '#2FA36B' },
  { id: 'ember', label: 'کهربایی', hex: '#D08A3E' },
  { id: 'crimson', label: 'سرخ', hex: '#E0505B' },
  { id: 'rose', label: 'گلبهی', hex: '#D2548F' },
  { id: 'arctic', label: 'یخی', hex: '#58A6D8' },
];

export const DEFAULT_ACCENT = ACCENT_PRESETS[0].hex;
/** Native widget surface and safe fallback text; kept aligned with its drawable. */
export const WIDGET_SURFACE = '#101018';
export const WIDGET_TEXT_FALLBACK = '#FFFFFF';

export interface BackgroundPreset {
  id: BackgroundId;
  label: string;
  hint: string;
}

export const BACKGROUND_PRESETS: BackgroundPreset[] = [
  { id: 'obsidian', label: 'اُبسیدیَن', hint: 'مشکی خنثی، تمرکز محض' },
  { id: 'nebula', label: 'سحابی', hint: 'بنفش و نیلی عمیق' },
  { id: 'aurora', label: 'شفق', hint: 'سبز-فیروزه‌ای سرد' },
  { id: 'alpine', label: 'کوهستان', hint: 'سرمای آبی غروب' },
  { id: 'ember', label: 'اخگر', hint: 'گرمای زغال و کهربا' },
  { id: 'graphite', label: 'گرافیت', hint: 'خاکستری مات با بافت' },
];

export const MOTION_LABELS: Record<MotionLevel, { label: string; hint: string }> = {
  standard: { label: 'STANDARD', hint: 'سبک — باتری و روانی روی دستگاه ضعیف' },
  cinematic: { label: 'CINEMATIC', hint: 'پیش‌فرض — موشن کامل و متعادل' },
  ultra: { label: 'ULTRA', hint: 'حداکثر جزئیات — برای دستگاه‌های قوی' },
};

/* ---------------------------------------------------------------------------
   Colour maths
   --------------------------------------------------------------------------- */

interface Rgb {
  r: number;
  g: number;
  b: number;
}
interface Oklch {
  l: number;
  c: number;
  h: number;
}

export function normalizeHex(input: string): string | null {
  const raw = input.trim().replace(/^#/, '');
  if (/^[0-9a-fA-F]{3}$/.test(raw)) {
    return `#${raw
      .split('')
      .map((ch) => ch + ch)
      .join('')
      .toUpperCase()}`;
  }
  if (/^[0-9a-fA-F]{6}$/.test(raw)) return `#${raw.toUpperCase()}`;
  return null;
}

export function isHexColor(input: string): boolean {
  return normalizeHex(input) !== null;
}

function hexToRgb(hex: string): Rgb {
  const value = normalizeHex(hex) ?? DEFAULT_ACCENT;
  return {
    r: parseInt(value.slice(1, 3), 16) / 255,
    g: parseInt(value.slice(3, 5), 16) / 255,
    b: parseInt(value.slice(5, 7), 16) / 255,
  };
}

const to255 = (v: number) => Math.max(0, Math.min(255, Math.round(v * 255)));

function rgbToHex({ r, g, b }: Rgb): string {
  return `#${[r, g, b]
    .map((v) => to255(v).toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()}`;
}

const gamma = (v: number) => (v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055);
const linear = (v: number) => (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));

export function relativeLuminance(hex: string): number {
  const { r, g, b } = hexToRgb(hex);
  return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b);
}

/** WCAG contrast ratio (1…21). */
export function contrastRatio(a: string, b: string): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const light = Math.max(la, lb);
  const dark = Math.min(la, lb);
  return (light + 0.05) / (dark + 0.05);
}

export function hexToOklch(hex: string): Oklch {
  const { r, g, b } = hexToRgb(hex);
  const lr = linear(r);
  const lg = linear(g);
  const lb = linear(b);

  const l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
  const m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
  const s = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);

  const okL = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
  const okA = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
  const okB = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;

  const chroma = Math.sqrt(okA * okA + okB * okB);
  let hue = (Math.atan2(okB, okA) * 180) / Math.PI;
  if (hue < 0) hue += 360;
  return { l: okL, c: chroma, h: hue };
}

export function oklchToHex({ l, c, h }: Oklch): string {
  const clampedL = Math.max(0, Math.min(1, l));
  const rad = (h * Math.PI) / 180;
  const okA = Math.cos(rad) * c;
  const okB = Math.sin(rad) * c;

  const l_ = clampedL + 0.3963377774 * okA + 0.2158037573 * okB;
  const m_ = clampedL - 0.1055613458 * okA - 0.0638541728 * okB;
  const s_ = clampedL - 0.0894841775 * okA - 1.291485548 * okB;

  const l3 = l_ * l_ * l_;
  const m3 = m_ * m_ * m_;
  const s3 = s_ * s_ * s_;

  const lr = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3;
  const lg = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3;
  const lb = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.707614701 * s3;

  return rgbToHex({ r: gamma(Math.max(0, Math.min(1, lr))), g: gamma(Math.max(0, Math.min(1, lg))), b: gamma(Math.max(0, Math.min(1, lb))) });
}

/* ---------------------------------------------------------------------------
   Derivation
   --------------------------------------------------------------------------- */

export interface DerivedAccent {
  /** The colour the user picked (normalised). */
  accent: string;
  /** Slightly brighter fill for hover / active states. */
  hover: string;
  /** Accent tuned for text and icons on the obsidian surfaces (≥ 4.5:1). */
  fg: string;
  /** Text colour to place *on* the accent fill. */
  on: string;
  /** Ready-to-use translucent tints (CSS colour strings). */
  soft: string;
  glow: string;
  /** Raw OKLCH of the base colour, exposed for gradients. */
  l: number;
  c: number;
  h: number;
}

const INK = '#0A0A0C';
/** WCAG AA for normal text. */
const AA = 4.5;

export function deriveAccent(input: string): DerivedAccent {
  const accent = normalizeHex(input) ?? DEFAULT_ACCENT;
  const base = hexToOklch(accent);

  // Hover: same hue/chroma, a touch lighter — never a different colour.
  const hover = oklchToHex({ l: Math.min(0.92, base.l + 0.07), c: base.c, h: base.h });

  // Foreground: walk lightness up until it is readable on near-black.
  let fgL = Math.max(base.l, 0.68);
  let fg = oklchToHex({ l: fgL, c: base.c * 0.86, h: base.h });
  let guard = 0;
  while (contrastRatio(fg, INK) < AA && fgL < 0.985 && guard < 40) {
    fgL = Math.min(0.985, fgL + 0.012);
    fg = oklchToHex({ l: fgL, c: base.c * 0.86, h: base.h });
    guard += 1;
  }

  const on = contrastRatio('#FFFFFF', accent) >= AA ? '#FFFFFF' : INK;

  return {
    accent,
    hover,
    fg,
    on,
    soft: `color-mix(in srgb, ${accent} 16%, transparent)`,
    glow: `color-mix(in srgb, ${accent} 34%, transparent)`,
    l: base.l,
    c: base.c,
    h: base.h,
  };
}

/* ---------------------------------------------------------------------------
   Appearance state (persisted + cached for a flash-free boot)
   --------------------------------------------------------------------------- */

export interface Appearance {
  accent: string;
  backgroundId: BackgroundId;
  motionLevel: MotionLevel;
}

export const DEFAULT_APPEARANCE: Appearance = {
  accent: DEFAULT_ACCENT,
  backgroundId: 'obsidian',
  motionLevel: 'cinematic',
};

const CACHE_KEY = 'kairos:appearance:v1';

export function normalizeAppearance(input: Partial<Appearance> | null | undefined): Appearance {
  const accent = input?.accent && isHexColor(input.accent) ? normalizeHex(input.accent)! : DEFAULT_APPEARANCE.accent;
  const backgroundId = BACKGROUND_PRESETS.some((b) => b.id === input?.backgroundId)
    ? (input!.backgroundId as BackgroundId)
    : DEFAULT_APPEARANCE.backgroundId;
  const motionLevel =
    input?.motionLevel === 'standard' || input?.motionLevel === 'ultra' || input?.motionLevel === 'cinematic'
      ? input.motionLevel
      : DEFAULT_APPEARANCE.motionLevel;
  return { accent, backgroundId, motionLevel };
}

/**
 * Synchronous boot cache. The Dexie settings row is the source of truth, but it
 * is async — reading it before the first paint would flash the default violet
 * for a frame. The cache is written on every change and only mirrored here.
 */
export function readCachedAppearance(): Appearance {
  if (typeof window === 'undefined') return DEFAULT_APPEARANCE;
  try {
    const raw = window.localStorage.getItem(CACHE_KEY);
    if (!raw) return DEFAULT_APPEARANCE;
    return normalizeAppearance(JSON.parse(raw) as Partial<Appearance>);
  } catch {
    return DEFAULT_APPEARANCE;
  }
}

export function cacheAppearance(appearance: Appearance): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(CACHE_KEY, JSON.stringify(normalizeAppearance(appearance)));
  } catch {
    // Private mode / quota — the engine still works, it only loses the cache.
  }
}

/** Writes the derived tokens + the two data-attributes the CSS keys off. */
export function applyAppearance(appearance: Appearance, root?: HTMLElement): DerivedAccent {
  const safe = normalizeAppearance(appearance);
  const derived = deriveAccent(safe.accent);
  const el = root ?? (typeof document !== 'undefined' ? document.documentElement : undefined);
  if (!el) return derived;

  el.style.setProperty('--accent', derived.accent);
  el.style.setProperty('--accent-hover', derived.hover);
  el.style.setProperty('--accent-fg', derived.fg);
  el.style.setProperty('--accent-on', derived.on);
  el.style.setProperty('--accent-soft', derived.soft);
  el.style.setProperty('--accent-glow', derived.glow);
  el.style.setProperty('--accent-l', derived.l.toFixed(4));
  el.style.setProperty('--accent-c', derived.c.toFixed(4));
  el.style.setProperty('--accent-h', derived.h.toFixed(2));
  el.dataset.bg = safe.backgroundId;
  el.dataset.motion = safe.motionLevel;

  return derived;
}

/** Boot helper: apply the cached appearance immediately and keep it cached. */
export function bootstrapAppearance(): Appearance {
  const cached = readCachedAppearance();
  applyAppearance(cached);
  return cached;
}
