import { normalizeMix, SOUND_PROFILES } from './layers';
import { audioController } from './controller';

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
  // A mounted studio owns playback; never start the browser engine in parallel
  // with its Android service. preventDefault means the command was handled.
  const unhandled = window.dispatchEvent(new CustomEvent<SoundCommand>(SOUND_COMMAND_EVENT, { detail: command, cancelable: true }));
  if (!unhandled) return;

  // Fallback: nothing is listening, so drive the engine here.
  if (command.type === 'stop') {
    void audioController.stop();
    return;
  }
  const profile = SOUND_PROFILES.find((p) => p.id === command.profileId);
  if (profile) void audioController.start(normalizeMix(profile.mix), 0.8).catch(() => undefined);
}
