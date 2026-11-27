/**
 * Sound Studio — layer catalogue.
 *
 * Nature and café layers use packaged recordings; noise and tones are generated.
 * Source and licence details are recorded with the files. The catalogue is
 * pure data so the UI, the engine and the tests all agree on one source of truth.
 */

export type LayerId =
  | 'rain'
  | 'ocean'
  | 'wind'
  | 'fire'
  | 'forest'
  | 'cafe'
  | 'brown'
  | 'pink'
  | 'white'
  | 'drone'
  | 'binaural';

export type LayerKind = 'nature' | 'noise' | 'tone';

export interface LayerDefinition {
  id: LayerId;
  /** Persian label shown in the mixer. */
  label: string;
  /** One short, honest description of the synthesis. */
  hint: string;
  kind: LayerKind;
  /** Needs headphones to do what it claims. */
  headphonesOnly?: boolean;
}

export const LAYERS: LayerDefinition[] = [
  { id: 'rain', label: 'باران', hint: 'ضبط باران' , kind: 'nature' },
  { id: 'ocean', label: 'اقیانوس', hint: 'ضبط موج دریا' , kind: 'nature' },
  { id: 'wind', label: 'باد', hint: 'ضبط باد' , kind: 'nature' },
  { id: 'fire', label: 'آتش', hint: 'ضبط آتش' , kind: 'nature' },
  { id: 'forest', label: 'پرندگان', hint: 'ضبط آواز پرندگان' , kind: 'nature' },
  { id: 'cafe', label: 'کافه', hint: 'ضبط فضای رستوران' , kind: 'nature' },
  { id: 'brown', label: 'نویز قهوه‌ای', hint: 'فرکانس‌های پایین، گرم', kind: 'noise' },
  { id: 'pink', label: 'نویز صورتی', hint: 'توازن طبیعی طیف', kind: 'noise' },
  { id: 'white', label: 'نویز سفید', hint: 'طیف یکنواخت', kind: 'noise' },
  { id: 'drone', label: 'درون عمیق', hint: 'دو سینوس با نوسان آرام', kind: 'tone' },
  {
    id: 'binaural',
    label: 'بیت دوگوشی',
    hint: 'دو تُن با اختلاف ۱۰ هرتز؛ فقط با هدفون اثر دارد',
    kind: 'tone',
    headphonesOnly: true,
  },
];

export const LAYER_BY_ID: Record<LayerId, LayerDefinition> = LAYERS.reduce(
  (acc, layer) => ({ ...acc, [layer.id]: layer }),
  {} as Record<LayerId, LayerDefinition>
);

export type SoundMix = Partial<Record<LayerId, number>>;

export interface SoundProfile {
  id: string;
  label: string;
  hint: string;
  mix: SoundMix;
}

/**
 * Profiles are starting points, not locks: every layer stays adjustable and the
 * user's last mix is what actually plays.
 */
export const SOUND_PROFILES: SoundProfile[] = [
  {
    id: 'deep',
    label: 'تمرکز عمیق',
    hint: 'قهوه‌ای + باران',
    mix: { brown: 0.55, rain: 0.3 },
  },
  {
    id: 'calm',
    label: 'آرامش',
    hint: 'اقیانوس + باد',
    mix: { ocean: 0.5, wind: 0.22 },
  },
  {
    id: 'ritual',
    label: 'آیین ذهن',
    hint: 'پرندگان + بیت دوگوشی',
    mix: { forest: 0.35, binaural: 0.2 },
  },
  {
    id: 'rainy-cafe',
    label: 'کافهٔ بارانی',
    hint: 'کافه + باران',
    mix: { cafe: 0.4, rain: 0.3 },
  },
  {
    id: 'fireside',
    label: 'کنار آتش',
    hint: 'آتش + باد',
    mix: { fire: 0.45, wind: 0.18 },
  },
  {
    id: 'forest-air',
    label: 'هوای جنگل',
    hint: 'پرندگان + باد',
    mix: { forest: 0.4, wind: 0.2 },
  },
  {
    id: 'sleep',
    label: 'خواب',
    hint: 'قهوه‌ای + صورتی',
    mix: { brown: 0.45, pink: 0.22 },
  },
];

export const SLEEP_TIMER_PRESETS = [0, 15, 30, 45, 60] as const;

export const MAX_LAYERS = 4;

function clamp01(value: number): number {
  if (!Number.isFinite(value)) return 0;
  return Math.min(1, Math.max(0, value));
}

/**
 * Normalises an untrusted mix (localStorage, older builds) into something the
 * engine can schedule: clamped levels, no near-silent layers, at most
 * `MAX_LAYERS` of the loudest layers so the mix never turns into mud.
 */
export function normalizeMix(input: SoundMix | null | undefined): SoundMix {
  if (!input) return {};
  const entries = Object.entries(input)
    .filter(([id]) => id in LAYER_BY_ID)
    .map(([id, level]) => [id as LayerId, clamp01(Number(level))] as const)
    .filter(([, level]) => level > 0.01)
    .sort((a, b) => b[1] - a[1])
    .slice(0, MAX_LAYERS);

  return entries.reduce<SoundMix>((acc, [id, level]) => {
    acc[id] = Math.round(level * 100) / 100;
    return acc;
  }, {});
}

export function activeLayerCount(mix: SoundMix): number {
  return Object.keys(normalizeMix(mix)).length;
}

/** "۱:۰۵" style label for the sleep timer, in Latin digits. */
export function formatSleepRemaining(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}
