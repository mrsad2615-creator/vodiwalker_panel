import { normalizeMix, SOUND_PROFILES } from './layers';
import { soundStudio } from './engine';

/**
 * Cross-surface sound commands.
 *
 * Cinema mode and the Sound Studio are two surfaces over one engine. Rather than
 * duplicating engine state, the cinema chips ask the studio (which owns the
 * state) to apply a profile; if the studio is not mounted, the engine is driven
 * directly so the sound still starts.
 */
export const SOUND_COMMAND_EVENT = 'kairos:sound-command';

export type SoundCommand =
  | { type: 'profile'; profileId: string }
  | { type: 'stop' };

export function requestSound(command: SoundCommand): void {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent<SoundCommand>(SOUND_COMMAND_EVENT, { detail: command }));

  // Fallback: nothing is listening, so drive the engine here.
  if (command.type === 'stop') {
    soundStudio.stop();
    return;
  }
  const profile = SOUND_PROFILES.find((p) => p.id === command.profileId);
  if (profile) soundStudio.apply(normalizeMix(profile.mix), 0.8);
}
