import type { UserSettings } from '@/types/domain';
import { isHexColor } from '@/lib/theme';
import { SHORTCUTS } from '@/lib/navigation';

/**
 * Only ordinary, non-secret preferences can cross devices. Never restore device
 * grants, consent, credentials or sample-provenance flags from an untrusted file.
 * Missing fields in older backups leave the current value unchanged.
 */
export function restorableSettings(input: unknown): Partial<UserSettings> {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('invalid-backup-settings');
  const source = input as Record<string, unknown>;
  const output: Record<string, unknown> = {};
  function copy(key: string, valid: (value: unknown) => boolean) {
    if (!Object.prototype.hasOwnProperty.call(source, key)) return;
    if (!valid(source[key])) throw new Error('invalid-backup-settings');
    output[key] = source[key];
  }
  const choice = (values: string[]) => (value: unknown) => typeof value === 'string' && values.includes(value);
  const count = (min: number, max: number) => (value: unknown) => typeof value === 'number' && Number.isInteger(value) && value >= min && value <= max;
  const boolean = (value: unknown) => typeof value === 'boolean';
  copy('displayName', (value) => typeof value === 'string' && value.trim().length > 0 && value.length <= 200);
  copy('accentColor', (value) => typeof value === 'string' && isHexColor(value));
  copy('backgroundId', choice(['obsidian', 'nebula', 'aurora', 'alpine', 'ember', 'graphite']));
  copy('motionLevel', choice(['standard', 'cinematic', 'ultra']));
  copy('homeMode', choice(['cinematic', 'compact', 'detailed']));
  copy('dailyFocusGoalMinutes', count(1, 1440));
  copy('dailyTaskGoal', count(1, 1000));
  for (const key of ['pomodoroFocusMin', 'pomodoroShortBreakMin', 'pomodoroLongBreakMin', 'intervalBlockMin', 'intervalRestMin', 'countdownMin']) copy(key, count(1, 600));
  copy('pomodoroCyclesBeforeLong', count(1, 12));
  copy('intervalBlocks', count(1, 24));
  for (const key of ['autoAdvance', 'soundEnabled', 'hapticsEnabled', 'sampleVisible', 'widgetShowTitles']) copy(key, boolean);
  copy('widgetTaskFilter', choice(['all', 'open']));
  copy('navShortcuts', (value) => Array.isArray(value) && value.length === 4 && new Set(value).size === 4 && value.every((tab) => SHORTCUTS.includes(tab)));
  copy('motivationFavorites', (value) => Array.isArray(value) && value.length <= 10000 && value.every((id) => typeof id === 'string' && id.length <= 200));
  return output as Partial<UserSettings>;
}
