import { afterEach, describe, expect, it, vi } from 'vitest';
import { RecordedWebMixer, RECORDING_FILES, splitRecordedMix } from '@/lib/audio/recordings';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

class FakeAudio {
  static instances: FakeAudio[] = [];
  loop = false; preload = ''; volume = 1; currentTime = 0;
  pause = vi.fn();
  play = vi.fn(() => Promise.resolve());
  constructor(public src: string) { FakeAudio.instances.push(this); }
}

afterEach(() => { vi.unstubAllGlobals(); FakeAudio.instances = []; });

describe('packaged sound recordings', () => {
  it('bundles all six Ogg files and keeps tones generated', () => {
    const { recorded, generated } = splitRecordedMix({ rain: 0.5, forest: 0.2, brown: 0.3 });
    expect(recorded).toEqual({ rain: 0.5, forest: 0.2 });
    expect(generated).toEqual({ brown: 0.3 });
    for (const file of Object.values(RECORDING_FILES)) {
      const path = resolve('public/sounds', file!);
      expect(existsSync(path)).toBe(true);
      expect(readFileSync(path).subarray(0, 4).toString()).toBe('OggS');
    }
  });

  it('plays only selected recordings, updates levels and stops removed sources', async () => {
    vi.stubGlobal('Audio', FakeAudio);
    const mixer = new RecordedWebMixer();
    await mixer.apply({ rain: 0.5, brown: 0.5 }, 0.8);
    expect(FakeAudio.instances).toHaveLength(1);
    const rain = FakeAudio.instances[0];
    expect(rain.src).toContain('sounds/rain.ogg');
    expect(rain.loop).toBe(true);
    expect(rain.play).toHaveBeenCalledOnce();
    expect(rain.volume).toBeCloseTo(0.28);
    await mixer.apply({ rain: 0.25, forest: 0.5 }, 1);
    expect(FakeAudio.instances).toHaveLength(2);
    expect(rain.play).toHaveBeenCalledOnce();
    expect(rain.volume).toBeCloseTo(0.175);
    await mixer.apply({ forest: 0.5 }, 1);
    expect(rain.pause).toHaveBeenCalledOnce();
    mixer.stop();
    expect(FakeAudio.instances[1].pause).toHaveBeenCalledOnce();
  });

  it('releases every recording if playback is refused', async () => {
    class BlockedAudio extends FakeAudio { override play = vi.fn(() => Promise.reject(new Error('blocked'))); }
    vi.stubGlobal('Audio', BlockedAudio);
    const mixer = new RecordedWebMixer();
    await expect(mixer.apply({ rain: 0.5 }, 1)).rejects.toThrow('blocked');
    expect(FakeAudio.instances[0].pause).toHaveBeenCalled();
  });

  it('reports a blocked start even when a slider update arrives first', async () => {
    let fail!: (error: Error) => void;
    class DelayedFailure extends FakeAudio { override play = vi.fn(() => new Promise<void>((_resolve, reject) => { fail = reject; })); }
    vi.stubGlobal('Audio', DelayedFailure);
    const mixer = new RecordedWebMixer();
    const starting = mixer.apply({ rain: 0.5 }, 1);
    const updating = mixer.apply({ rain: 0.4 }, 1);
    fail(new Error('blocked'));
    await expect(starting).resolves.toBeUndefined();
    await expect(updating).rejects.toThrow('blocked');
    expect(FakeAudio.instances[0].pause).toHaveBeenCalled();
  });

  it('ignores a delayed failure from an obsolete mix', async () => {
    let fail!: (error: Error) => void;
    class DelayedFailure extends FakeAudio { override play = vi.fn(() => new Promise<void>((_resolve, reject) => { fail = reject; })); }
    vi.stubGlobal('Audio', DelayedFailure);
    const mixer = new RecordedWebMixer();
    const starting = mixer.apply({ rain: 0.5 }, 1);
    await mixer.apply({}, 1);
    fail(new Error('outdated'));
    await expect(starting).resolves.toBeUndefined();
  });

  it('does not revive a delayed play after stop', async () => {
    let release!: () => void;
    class DelayedAudio extends FakeAudio { override play = vi.fn(() => new Promise<void>((resolve) => { release = resolve; })); }
    vi.stubGlobal('Audio', DelayedAudio);
    const mixer = new RecordedWebMixer();
    const starting = mixer.apply({ rain: 0.5 }, 1);
    mixer.stop(); release(); await starting;
    expect(FakeAudio.instances[0].pause).toHaveBeenCalledTimes(2);
  });
});
