import { Capacitor, registerPlugin } from '@capacitor/core';
import { soundStudio } from './engine';
import type { SoundMix } from './layers';

/**
 * One interface, two engines.
 *
 * On Android the sound must outlive the WebView — Android suspends a backgrounded
 * page, which is why a locked screen used to kill the soundscape and the bell.
 * So native owns playback there (AudioTrack in a foreground service), and this
 * controller is the only thing the UI talks to.
 *
 * On the web (and in tests) the Web Audio engine still runs; it is the same
 * eleven layers, generated rather than streamed.
 */

interface KairosAudioPlugin {
  start(options: { mix: Record<string, number>; master: number }): Promise<{ playing: boolean }>;
  update(options: { mix: Record<string, number>; master: number }): Promise<{ playing: boolean }>;
  pause(): Promise<{ playing: boolean }>;
  resume(): Promise<{ playing: boolean }>;
  stop(): Promise<{ playing: boolean }>;
  playBell(): Promise<{ ok: boolean }>;
  state(): Promise<{ playing: boolean; supported: boolean }>;
}

const NativeAudio = registerPlugin<KairosAudioPlugin>('KairosAudio');

export type AudioEngine = 'native' | 'web';

function nativeAvailable(): boolean {
  return Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'android';
}

export const audioController = {
  /** Which engine this session is using — shown honestly in the studio. */
  get engine(): AudioEngine {
    return nativeAvailable() ? 'native' : 'web';
  },

  get isNative(): boolean {
    return nativeAvailable();
  },

  async start(mix: SoundMix, master: number): Promise<void> {
    const payload = { mix: mix as Record<string, number>, master };
    if (nativeAvailable()) {
      try {
        await NativeAudio.start(payload);
        return;
      } catch {
        // Fall through to Web Audio: a refused service must not mean silence.
      }
    }
    soundStudio.apply(mix, master);
  },

  async update(mix: SoundMix, master: number): Promise<void> {
    const payload = { mix: mix as Record<string, number>, master };
    if (nativeAvailable()) {
      try {
        await NativeAudio.update(payload);
        return;
      } catch {
        // ignore — the web fallback below still gets the mix
      }
    }
    soundStudio.apply(mix, master);
  },

  async stop(): Promise<void> {
    if (nativeAvailable()) {
      try {
        await NativeAudio.stop();
      } catch {
        // ignore
      }
    }
    soundStudio.stop();
  },

  async pause(): Promise<void> {
    if (nativeAvailable()) {
      try {
        await NativeAudio.pause();
        return;
      } catch {
        // ignore
      }
    }
    soundStudio.stop(0.2);
  },

  async resume(mix: SoundMix, master: number): Promise<void> {
    if (nativeAvailable()) {
      try {
        await NativeAudio.resume();
        await NativeAudio.update({ mix: mix as Record<string, number>, master });
        return;
      } catch {
        // ignore
      }
    }
    soundStudio.apply(mix, master);
  },

  /** The end-of-session bell. Native on Android, Web Audio elsewhere. */
  async playBell(): Promise<void> {
    if (nativeAvailable()) {
      try {
        await NativeAudio.playBell();
        return;
      } catch {
        // fall through
      }
    }
    const { playLuxuryChime } = await import('@/lib/notifications');
    playLuxuryChime(true);
  },

  /** Cue for the breathing pacer and phase changes (foreground only). */
  cue(frequency: number, seconds = 0.4, level = 0.1): void {
    // Native has no cue channel by design: a pacer tick every few seconds from a
    // service would need its own mix, and the visual pacer is the primary signal.
    if (nativeAvailable()) return;
    soundStudio.cue(frequency, seconds, level);
  },
};
