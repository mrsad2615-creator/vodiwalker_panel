import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pause, Play, Volume2, Waves } from '@/components/ui/icons';
import { Numeric } from '@/components/ui/Numeric';
import { soundStudio } from '@/lib/audio/engine';
import {
  LAYERS,
  SLEEP_TIMER_PRESETS,
  SOUND_PROFILES,
  activeLayerCount,
  formatSleepRemaining,
  normalizeMix,
  type LayerKind,
  type LayerId,
  type SoundMix,
} from '@/lib/audio/layers';
import { SOUND_COMMAND_EVENT, type SoundCommand } from '@/lib/audio/commands';
import {
  BREATH_PATTERNS,
  PHASE_LABEL,
  breathStateAt,
  patternDuration,
  type BreathState,
} from '@/lib/audio/breathing';

const STORAGE_KEY = 'kairos:sound:v1';

interface PersistedSound {
  mix: SoundMix;
  master: number;
  patternId: string;
}

function loadPersisted(): PersistedSound {
  const fallback: PersistedSound = { mix: {}, master: 0.8, patternId: BREATH_PATTERNS[0].id };
  if (typeof window === 'undefined') return fallback;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw) as Partial<PersistedSound>;
    return {
      mix: normalizeMix(parsed.mix),
      master:
        typeof parsed.master === 'number' ? Math.min(1, Math.max(0, parsed.master)) : fallback.master,
      patternId:
        BREATH_PATTERNS.some((p) => p.id === parsed.patternId) && parsed.patternId
          ? parsed.patternId
          : fallback.patternId,
    };
  } catch {
    return fallback;
  }
}

const KIND_LABEL: Record<LayerKind, string> = {
  nature: 'طبیعت',
  noise: 'نویز',
  tone: 'تُن',
};

/**
 * Sound Studio.
 *
 * Eleven real layers, four profiles, a sleep timer and a breathing pacer. Every
 * layer is generated in the Web Audio graph (see lib/audio/engine.ts), so there
 * is no licensed audio in the APK and no `false` feature: if it plays, it is
 * synthesised; if headphones are required, the card says so.
 */
export function SoundStudio() {
  const persisted = useMemo(loadPersisted, []);
  const [mix, setMix] = useState<SoundMix>(persisted.mix);
  const [master, setMaster] = useState(persisted.master);
  const [playing, setPlaying] = useState(false);
  const [sleepMinutes, setSleepMinutes] = useState(0);
  const [sleepLeft, setSleepLeft] = useState(0);

  const [patternId, setPatternId] = useState(persisted.patternId);
  const pattern = BREATH_PATTERNS.find((p) => p.id === patternId) ?? BREATH_PATTERNS[0];
  const [breathing, setBreathing] = useState(false);
  const [breath, setBreath] = useState<BreathState>(() => breathStateAt(pattern, 0));
  const breathStartRef = useRef(0);
  const lastPhaseRef = useRef<string>('');

  const layerCount = activeLayerCount(mix);

  // ── persistence ───────────────────────────────────────────────────────────
  useEffect(() => {
    try {
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ mix, master, patternId } satisfies PersistedSound)
      );
    } catch {
      // storage may be full or blocked; the studio still works for this session
    }
  }, [mix, master, patternId]);

  // ── engine ────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (!playing) return;
    soundStudio.apply(mix, master);
    return () => {
      soundStudio.stop();
    };
  }, [playing, mix, master]);

  // ── sleep timer ───────────────────────────────────────────────────────────
  useEffect(() => {
    if (!playing || sleepMinutes <= 0) {
      setSleepLeft(0);
      return;
    }
    setSleepLeft(sleepMinutes * 60);
    const timer = window.setInterval(() => {
      setSleepLeft((left) => {
        if (left <= 1) {
          window.clearInterval(timer);
          setPlaying(false);
          soundStudio.stop(1.2);
          return 0;
        }
        return left - 1;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [playing, sleepMinutes]);

  // ── breathing pacer (stateless clock, so a slow frame cannot drift) ───────
  useEffect(() => {
    if (!breathing) return;
    breathStartRef.current = Date.now();
    lastPhaseRef.current = '';

    let frame = 0;
    const tick = () => {
      const state = breathStateAt(pattern, Date.now() - breathStartRef.current);
      setBreath(state);
      const key = `${state.cycles}:${state.stepIndex}`;
      if (lastPhaseRef.current !== key) {
        lastPhaseRef.current = key;
        // A soft cue on each phase change; 396 Hz settles, 528 Hz lifts.
        soundStudio.cue(state.phase === 'inhale' ? 528 : state.phase === 'exhale' ? 396 : 330, 0.35, 0.1);
      }
      frame = window.requestAnimationFrame(tick);
    };
    frame = window.requestAnimationFrame(tick);
    return () => window.cancelAnimationFrame(frame);
  }, [breathing, pattern]);

  // ── handlers ──────────────────────────────────────────────────────────────
  const toggleLayer = (id: LayerId) => {
    setMix((prev) => {
      const next = { ...prev };
      if (next[id]) delete next[id];
      else next[id] = 0.4;
      return normalizeMix(next);
    });
  };

  const setLevel = (id: LayerId, level: number) => {
    setMix((prev) => normalizeMix({ ...prev, [id]: level }));
  };

  const applyProfile = (profileIdValue: string) => {
    const profile = SOUND_PROFILES.find((p) => p.id === profileIdValue);
    if (!profile) return;
    setMix(normalizeMix(profile.mix));
    setPlaying(true);
  };

  const clearAll = useCallback(() => {
    setMix({});
    setPlaying(false);
    soundStudio.stop();
  }, []);

  const togglePlay = () => {
    if (playing) {
      setPlaying(false);
      soundStudio.stop();
      return;
    }
    if (layerCount === 0) {
      applyProfile('deep');
      return;
    }
    setPlaying(true);
  };

  // Cinema mode drives the same engine through a window command.
  useEffect(() => {
    const onCommand = (event: Event) => {
      const detail = (event as CustomEvent<SoundCommand>).detail;
      if (!detail) return;
      if (detail.type === 'stop') {
        setMix({});
        setPlaying(false);
        return;
      }
      const profile = SOUND_PROFILES.find((p) => p.id === detail.profileId);
      if (profile) {
        setMix(normalizeMix(profile.mix));
        setPlaying(true);
      }
    };
    window.addEventListener(SOUND_COMMAND_EVENT, onCommand);
    return () => window.removeEventListener(SOUND_COMMAND_EVENT, onCommand);
  }, []);

  const ringRadius = 34;
  const ringCircumference = 2 * Math.PI * ringRadius;

  return (
    <section aria-labelledby="sound-studio-title" className="space-y-3">
      <div className="flex items-center justify-between">
        <span
          id="sound-studio-title"
          className="inline-flex items-center gap-1.5 text-[10.5px] font-medium text-[var(--text-2)]"
        >
          <Volume2 className="h-3 w-3 text-[var(--accent-fg)]" />
          فضای صوتی تمرکز (آفلاین)
        </span>
        <span className="text-[9.5px] text-[var(--text-3)]">
          {layerCount === 0
            ? 'خاموش'
            : !playing
            ? 'آماده'
            : sleepLeft > 0
            ? `پایان تا ${formatSleepRemaining(sleepLeft)}`
            : 'در حال پخش'}
        </span>
      </div>

      {/* Transport + master */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={togglePlay}
          className="btn-royal flex h-10 items-center gap-2 rounded-full px-4 text-[11.5px] font-bold"
        >
          {playing ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
          {playing ? 'توقف صدا' : 'پخش ترکیب'}
        </button>

        <label className="flex min-w-0 flex-1 items-center gap-2">
          <span className="text-[9px] text-[var(--text-3)]">میکس</span>
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={Math.round(master * 100)}
            onChange={(event) => setMaster(Number(event.target.value) / 100)}
            aria-label="صدای کلی"
            className="h-1 min-w-0 flex-1 accent-[var(--accent)]"
          />
          <Numeric
            value={Math.round(master * 100)}
            variant="geo"
            className="w-7 text-[9px] text-[var(--text-3)]"
          />
        </label>

        {layerCount > 0 && (
          <button
            type="button"
            onClick={clearAll}
            className="shrink-0 text-[9.5px] text-[var(--text-3)] underline-offset-4 hover:underline"
          >
            پاک
          </button>
        )}
      </div>

      {/* Profiles */}
      <div className="no-scrollbar -mx-1 flex gap-1.5 overflow-x-auto px-1">
        {SOUND_PROFILES.map((profile) => (
          <button
            key={profile.id}
            type="button"
            onClick={() => applyProfile(profile.id)}
            className="shrink-0 rounded-full border border-[var(--border)] px-2.5 py-1 text-start transition-colors hover:border-[var(--accent-fg)]/50"
          >
            <span className="block text-[10px] font-medium text-[var(--text)]">{profile.label}</span>
            <span className="block text-[8px] text-[var(--text-3)]">{profile.hint}</span>
          </button>
        ))}
      </div>

      {/* Sleep timer */}
      <div className="flex items-center gap-1.5 border-y border-[var(--border)] py-2">
        <span className="text-[9px] text-[var(--text-3)]">خواب</span>
        {SLEEP_TIMER_PRESETS.map((minutes) => (
          <button
            key={minutes}
            type="button"
            onClick={() => setSleepMinutes(minutes)}
            aria-pressed={sleepMinutes === minutes}
            className={`rounded-full px-2 py-0.5 text-[9.5px] font-medium transition-colors ${
              sleepMinutes === minutes
                ? 'bg-[color-mix(in_srgb,var(--accent)_22%,transparent)] text-[var(--text)]'
                : 'text-[var(--text-3)] hover:text-[var(--text-2)]'
            }`}
          >
            {minutes === 0 ? 'بدون' : `${minutes}د`}
          </button>
        ))}
      </div>

      {/* Layer mixer */}
      <div className="space-y-2.5">
        {(['nature', 'noise', 'tone'] as LayerKind[]).map((kind) => (
          <div key={kind}>
            <span className="text-[8.5px] font-semibold tracking-[0.08em] text-[var(--text-3)]">
              {KIND_LABEL[kind]}
            </span>
            <div className="mt-1 space-y-1">
              {LAYERS.filter((layer) => layer.kind === kind).map((layer) => {
                const level = mix[layer.id] ?? 0;
                return (
                  <div key={layer.id} className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => toggleLayer(layer.id)}
                      aria-pressed={level > 0}
                      aria-label={`${layer.label} — ${level > 0 ? 'روشن' : 'خاموش'}`}
                      className={`flex w-[92px] shrink-0 items-center gap-1.5 rounded-md px-1 py-0.5 text-start transition-colors ${
                        level > 0 ? 'text-[var(--text)]' : 'text-[var(--text-3)]'
                      }`}
                    >
                      <span
                        className="h-1.5 w-1.5 shrink-0 rounded-full"
                        style={{
                          background: level > 0 ? 'var(--accent)' : 'var(--border-strong)',
                        }}
                      />
                      <span className="truncate text-[10px]">{layer.label}</span>
                    </button>

                    <input
                      type="range"
                      min={0}
                      max={100}
                      step={5}
                      value={Math.round(level * 100)}
                      onChange={(event) => setLevel(layer.id, Number(event.target.value) / 100)}
                      aria-label={`سطح ${layer.label}`}
                      className="h-1 min-w-0 flex-1 accent-[var(--accent)]"
                    />
                    <Numeric
                      value={Math.round(level * 100)}
                      variant="geo"
                      className="w-6 text-end text-[8.5px] text-[var(--text-3)]"
                    />
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <p className="text-[8.5px] leading-4 text-[var(--text-3)]">
        همهٔ لایه‌ها همین‌جا و در لحظه ساخته می‌شوند (نویز و نوسان‌ساز)؛ هیچ فایل صوتی از منبع
        دیگری در اپ نیست. «بیت دوگوشی» فقط با هدفون اثر دارد — بدون هدفون هر دو گوش هر دو تُن را
        می‌شنوند. پخش صدا با رفتن اپ به پس‌زمینه متوقف می‌شود.
      </p>

      {/* Breathing pacer */}
      <div className="border-t border-[var(--border)] pt-3">
        <div className="flex items-center justify-between">
          <span className="inline-flex items-center gap-1.5 text-[10.5px] font-medium text-[var(--text-2)]">
            <Waves className="h-3 w-3 text-[var(--accent-fg)]" />
            پیسر تنفس (خودآگاهی)
          </span>
          <button
            type="button"
            onClick={() => setBreathing((v) => !v)}
            className="text-[9.5px] font-medium text-[var(--accent-fg)]"
          >
            {breathing ? 'پایان' : 'شروع'}
          </button>
        </div>

        <div className="no-scrollbar mt-1.5 flex gap-1.5 overflow-x-auto">
          {BREATH_PATTERNS.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => {
                setPatternId(p.id);
                setBreath(breathStateAt(p, 0));
              }}
              aria-pressed={p.id === patternId}
              className={`shrink-0 rounded-full border px-2 py-0.5 text-[9px] transition-colors ${
                p.id === patternId
                  ? 'border-[var(--accent)] text-[var(--text)]'
                  : 'border-[var(--border)] text-[var(--text-3)]'
              }`}
            >
              {p.label}
            </button>
          ))}
        </div>

        <div className="mt-2 flex items-center gap-3">
          <div className="relative h-[84px] w-[84px] shrink-0">
            <svg viewBox="0 0 84 84" className="h-full w-full -rotate-90">
              <circle cx="42" cy="42" r={ringRadius} fill="none" stroke="var(--border)" strokeWidth="3" />
              <circle
                cx="42"
                cy="42"
                r={ringRadius}
                fill="none"
                stroke="var(--accent)"
                strokeWidth="3"
                strokeLinecap="round"
                strokeDasharray={ringCircumference}
                strokeDashoffset={ringCircumference * (1 - (breathing ? breath.progress : 0))}
                style={{ transition: 'stroke-dashoffset 120ms linear' }}
              />
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <Numeric
                value={breathing ? breath.secondsLeft : patternDuration(pattern)}
                variant="display"
                className="text-[16px] font-bold text-[var(--text)]"
              />
              <span className="text-[8.5px] text-[var(--text-3)]">
                {breathing ? PHASE_LABEL[breath.phase] : 'ثانیه'}
              </span>
            </div>
          </div>

          <div className="min-w-0 flex-1 text-[9.5px] leading-4 text-[var(--text-3)]">
            <p className="text-[var(--text-2)]">{pattern.hint}</p>
            <p className="mt-0.5">
              چرخهٔ کامل: <Numeric value={patternDuration(pattern)} variant="geo" /> ثانیه
              {breathing && (
                <>
                  {' · '}
                  <Numeric value={breath.cycles} variant="geo" /> چرخه
                </>
              )}
            </p>
            <p className="mt-0.5">
              {breathing
                ? 'با نشانه‌های صوتی نرم همراه است؛ شانه‌ها را رها کن.'
                : 'یک الگو را انتخاب و شروع کن؛ هر مرحله با یک تُن نرم اعلام می‌شود.'}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}
