import { CHRONO_MODES, buildPlan, type ChronoConfig, type ChronoMode } from '@/lib/chrono/plan';
import type { UserSettings } from '@/types/domain';

/** Settings → plan configuration. One adapter, so the clock never reads settings. */
export function chronoConfigFromSettings(settings: UserSettings): ChronoConfig {
  return {
    focusMinutes: settings.pomodoroFocusMin,
    shortBreakMinutes: settings.pomodoroShortBreakMin,
    longBreakMinutes: settings.pomodoroLongBreakMin,
    cyclesBeforeLong: settings.pomodoroCyclesBeforeLong,
    blocks: settings.intervalBlocks,
    blockMinutes: settings.intervalBlockMin,
    restMinutes: settings.intervalRestMin,
    countdownMinutes: settings.countdownMin,
  };
}

export function planForMode(mode: ChronoMode, config: ChronoConfig) {
  return buildPlan(mode, config);
}

/** Persian name of a mode, for history rows and the notification. */
export function modeLabel(mode: ChronoMode): string {
  return CHRONO_MODES.find((entry) => entry.id === mode)?.label ?? 'تمرکز';
}

/** Compact `۲۵/۵` style hint for the mode tabs. */
export function modeHint(mode: ChronoMode, config: ChronoConfig): string {
  if (mode === 'pomodoro') return `${config.focusMinutes}/${config.shortBreakMinutes}`;
  if (mode === 'interval') return `${config.blocks}×${config.blockMinutes}`;
  if (mode === 'countdown') return `${config.countdownMinutes}`;
  return 'بی‌پایان';
}

/** Minutes that make a sensible default length for a mode. */
export function defaultMinutesFor(mode: ChronoMode, config: ChronoConfig): number {
  if (mode === 'countdown') return config.countdownMinutes;
  if (mode === 'interval') return config.blockMinutes;
  if (mode === 'free') return 0;
  return config.focusMinutes;
}
