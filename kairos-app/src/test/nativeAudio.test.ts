import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it, vi } from 'vitest';

/**
 * The native audio stack cannot be unit-tested for sound, but it can be tested
 * for the mistakes that make it silently disappear: an unregistered plugin, a
 * service missing from the manifest, a missing foreground-service type, or a
 * timer that never arms its own end-of-session alarm.
 */

const ANDROID = join(process.cwd(), 'android');
const read = (path: string) => readFileSync(join(ANDROID, path), 'utf8');

describe('native audio — wiring', () => {
  const manifest = read('app/src/main/AndroidManifest.xml');
  const mainActivity = read('app/src/main/java/app/kairos/productivity/MainActivity.java');
  const chrono = read('app/src/main/java/app/kairos/productivity/ChronoService.java');
  const engine = read('app/src/main/java/app/kairos/productivity/NativeSoundscape.java');

  it('declares the soundscape service as a media-playback foreground service', () => {
    expect(manifest).toMatch(/android:name="\.SoundscapeService"/);
    expect(manifest).toMatch(/foregroundServiceType="mediaPlayback"/);
    expect(manifest).toContain('android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK');
  });

  it('registers the audio plugin with the bridge', () => {
    expect(mainActivity).toContain('registerPlugin(KairosAudioPlugin.class)');
    // Registration has to happen before super.onCreate or Capacitor misses it.
    // (lastIndexOf: the class doc mentions registration too.)
    expect(mainActivity.lastIndexOf('registerPlugin(KairosAudioPlugin.class)')).toBeLessThan(
      mainActivity.lastIndexOf('super.onCreate(savedInstanceState)')
    );
  });

  it('streams 48 kHz stereo from a dedicated audio thread', () => {
    expect(engine).toContain('SAMPLE_RATE = 48000');
    expect(engine).toContain('CHANNEL_OUT_STEREO');
    expect(engine).toContain('MODE_STREAM');
    expect(engine).toMatch(/Thread\(new Runnable/);
    // The bell must be synthable without any bundled file.
    expect(engine).toContain('public static void playBell()');
  });

  it('arms the end-of-session alarm inside the timer service', () => {
    // The shade's chronometer is drawn by the system and tells the app nothing,
    // so the service has to hold its own timer for the bell.
    expect(chrono).toContain('scheduleEnd()');
    expect(chrono).toContain('SoundscapeBus.playBell()');
    expect(chrono).toContain('announceCompletion()');
    expect(chrono).toMatch(/endHandler\.postDelayed\(endRunnable/);
  });
});

describe('audio controller — routing', () => {
  it('falls back to the Web Audio engine outside Android', async () => {
    vi.resetModules();
    const { audioController } = await import('@/lib/audio/controller');
    expect(audioController.engine).toBe('web');
    await expect(audioController.start({ brown: 0.5 }, 0.8)).resolves.toBeUndefined();
    await expect(audioController.update({ rain: 0.4 }, 0.6)).resolves.toBeUndefined();
    await expect(audioController.stop()).resolves.toBeUndefined();
    await expect(audioController.pause()).resolves.toBeUndefined();
  });
});
