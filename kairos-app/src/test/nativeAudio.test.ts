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

  it('synthesises width, space and air instead of one thin mono stream', () => {
    // Two decorrelated noise generators: a shared stream collapses to mono.
    expect(engine).toMatch(/rngL|randomLeft/);
    expect(engine).toMatch(/rngR|randomRight/);
    expect(engine).not.toMatch(/Math\.random\(\)\s*\*\s*2\s*-\s*1;\s*\n\s*left\[/);
    // A real reverb tail (damped combs + allpass diffusers), not a delay line.
    expect(engine).toContain('class Reverb');
    expect(engine).toMatch(/class Comb/);
    expect(engine).toMatch(/class Allpass/);
    expect(engine).toMatch(/damping/);
    // Transients (droplets, crackle, birds) rather than flat-filtered noise.
    expect(engine).toContain('transientRate');
    expect(engine).toContain('transient');
  });

  it('fades in and out and soft-clips so a session never clicks', () => {
    expect(engine).toMatch(/fade|envelope|FADE/i);
    expect(engine).toMatch(/softClip|tanh/);
    // Three blocks of silence drain the reverb tail instead of cutting it dead.
    expect(engine).toMatch(/drain|DRAIN/i);
    // A generous buffer keeps the stream ahead of the scheduler.
    expect(engine).toContain('minBuffer * 3');
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
