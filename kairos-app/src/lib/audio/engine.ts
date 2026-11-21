import { getAudioContext } from '../audioContext';
import { fillNoise, type NoiseColor } from './noise';
import { normalizeMix, type LayerId, type SoundMix } from './layers';

/**
 * The Sound Studio engine.
 *
 * One graph, one master gain, one teardown path. Every layer is built from
 * oscillators and generated noise buffers — nothing is downloaded, nothing is
 * licensed, and stopping really stops (sources are stopped and disconnected, so
 * a long session cannot leak nodes into a WebView that only allows a handful of
 * audio contexts).
 */

interface LayerHandle {
  gain: GainNode;
  sources: AudioScheduledSourceNode[];
  timers: number[];
}

function noiseSource(ctx: AudioContext, color: NoiseColor): AudioBufferSourceNode {
  const seconds = 4;
  const buffer = ctx.createBuffer(1, Math.floor(ctx.sampleRate * seconds), ctx.sampleRate);
  fillNoise(buffer.getChannelData(0), color, color === 'brown' ? 7 : color === 'pink' ? 13 : 29);

  const source = ctx.createBufferSource();
  source.buffer = buffer;
  source.loop = true;
  return source;
}

/** A slow sine LFO wired into an AudioParam (gain or filter frequency). */
function lfo(ctx: AudioContext, param: AudioParam, frequency: number, depth: number) {
  const osc = ctx.createOscillator();
  const amount = ctx.createGain();
  osc.frequency.value = frequency;
  osc.type = 'sine';
  amount.gain.value = depth;
  osc.connect(amount).connect(param);
  osc.start();
  return osc;
}

interface ChainParts {
  input: AudioNode[];
  output: AudioNode;
}

function buildLayer(ctx: AudioContext, id: LayerId, handleTimers: number[]): ChainParts {
  switch (id) {
    case 'rain': {
      const source = noiseSource(ctx, 'white');
      const band = ctx.createBiquadFilter();
      band.type = 'highpass';
      band.frequency.value = 1200;
      const soft = ctx.createBiquadFilter();
      soft.type = 'lowpass';
      soft.frequency.value = 7800;
      source.connect(band).connect(soft);
      lfo(ctx, soft.frequency, 0.12, 900);
      return { input: [source], output: soft };
    }

    case 'ocean': {
      const source = noiseSource(ctx, 'brown');
      const low = ctx.createBiquadFilter();
      low.type = 'lowpass';
      low.frequency.value = 620;
      const swell = ctx.createGain();
      swell.gain.value = 0.55;
      lfo(ctx, swell.gain, 0.08, 0.4);
      source.connect(low).connect(swell);
      return { input: [source], output: swell };
    }

    case 'wind': {
      const source = noiseSource(ctx, 'pink');
      const band = ctx.createBiquadFilter();
      band.type = 'bandpass';
      band.frequency.value = 500;
      band.Q.value = 0.9;
      lfo(ctx, band.frequency, 0.05, 320);
      source.connect(band);
      return { input: [source], output: band };
    }

    case 'fire': {
      const source = noiseSource(ctx, 'brown');
      const low = ctx.createBiquadFilter();
      low.type = 'lowpass';
      low.frequency.value = 1100;
      source.connect(low);

      // Crackles: short bursts of filtered noise, scheduled at human-random gaps.
      const crackle = () => {
        const burst = noiseSource(ctx, 'white');
        const gain = ctx.createGain();
        const band = ctx.createBiquadFilter();
        band.type = 'bandpass';
        band.frequency.value = 2400 + Math.random() * 1800;
        band.Q.value = 1.6;
        burst.connect(band).connect(gain);
        gain.connect(low);
        const now = ctx.currentTime;
        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.exponentialRampToValueAtTime(0.6 + Math.random() * 0.5, now + 0.01);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.06 + Math.random() * 0.09);
        burst.start(now);
        burst.stop(now + 0.2);
        handleTimers.push(
          window.setTimeout(crackle, 180 + Math.random() * 900) as unknown as number
        );
      };
      handleTimers.push(window.setTimeout(crackle, 400) as unknown as number);

      return { input: [source], output: low };
    }

    case 'forest': {
      const source = noiseSource(ctx, 'pink');
      const leaves = ctx.createBiquadFilter();
      leaves.type = 'bandpass';
      leaves.frequency.value = 3200;
      leaves.Q.value = 0.7;
      source.connect(leaves);
      lfo(ctx, leaves.frequency, 0.09, 700);

      // Bird calls: two-note FM blips, quiet and occasional.
      const bird = () => {
        const osc = ctx.createOscillator();
        const gain = ctx.createGain();
        osc.type = 'sine';
        const now = ctx.currentTime;
        const base = 1800 + Math.random() * 900;
        osc.frequency.setValueAtTime(base, now);
        osc.frequency.exponentialRampToValueAtTime(base * 1.35, now + 0.08);
        osc.frequency.exponentialRampToValueAtTime(base * 0.9, now + 0.18);
        gain.gain.setValueAtTime(0.0001, now);
        gain.gain.exponentialRampToValueAtTime(0.16, now + 0.03);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.26);
        osc.connect(gain).connect(leaves);
        osc.start(now);
        osc.stop(now + 0.3);
        handleTimers.push(
          window.setTimeout(bird, 4200 + Math.random() * 9000) as unknown as number
        );
      };
      handleTimers.push(window.setTimeout(bird, 2600) as unknown as number);

      return { input: [source], output: leaves };
    }

    case 'cafe': {
      const source = noiseSource(ctx, 'pink');
      const low = ctx.createBiquadFilter();
      low.type = 'lowpass';
      low.frequency.value = 780;
      const murmur = ctx.createGain();
      murmur.gain.value = 0.7;
      lfo(ctx, murmur.gain, 0.21, 0.25);
      source.connect(low).connect(murmur);
      return { input: [source], output: murmur };
    }

    case 'brown':
    case 'pink':
    case 'white': {
      const source = noiseSource(ctx, id);
      return { input: [source], output: source };
    }

    case 'drone': {
      const a = ctx.createOscillator();
      const b = ctx.createOscillator();
      a.type = 'sine';
      b.type = 'triangle';
      a.frequency.value = 110;
      b.frequency.value = 110.7;
      const low = ctx.createBiquadFilter();
      low.type = 'lowpass';
      low.frequency.value = 420;
      a.connect(low);
      b.connect(low);
      a.start();
      b.start();
      return { input: [a, b], output: low };
    }

    case 'binaural': {
      // Honest implementation: two pure tones, one per ear. The beat frequency
      // is the difference (10 Hz). Without headphones each ear hears both, so
      // the effect does not exist — the UI says so.
      const left = ctx.createOscillator();
      const right = ctx.createOscillator();
      left.type = 'sine';
      right.type = 'sine';
      left.frequency.value = 200;
      right.frequency.value = 210;

      const panL = ctx.createStereoPanner();
      const panR = ctx.createStereoPanner();
      panL.pan.value = -1;
      panR.pan.value = 1;

      left.connect(panL);
      right.connect(panR);
      left.start();
      right.start();

      const merge = ctx.createGain();
      panL.connect(merge);
      panR.connect(merge);
      return { input: [left, right], output: merge };
    }

    default:
      throw new Error(`unknown layer: ${id}`);
  }
}

class SoundStudioEngine {
  private ctx: AudioContext | null = null;
  private master: GainNode | null = null;
  private layers = new Map<LayerId, LayerHandle>();
  private masterLevel = 0.8;

  /** True when the platform can actually make sound. */
  isSupported(): boolean {
    return typeof window !== 'undefined' && Boolean(getAudioContext());
  }

  /**
   * Applies a mix: builds layers that are new, fades layers that changed,
   * tears down layers that were removed. Safe to call on every slider move.
   */
  apply(mix: SoundMix, master = this.masterLevel): void {
    const wanted = normalizeMix(mix);
    this.masterLevel = Math.min(1, Math.max(0, master));

    if (Object.keys(wanted).length === 0) {
      this.stop();
      return;
    }

    const ctx = this.ensureContext();
    if (!ctx || !this.master) return;

    const now = ctx.currentTime;
    this.master.gain.cancelScheduledValues(now);
    this.master.gain.setTargetAtTime(this.masterLevel * 0.9, now, 0.05);

    // Remove layers that are no longer wanted.
    for (const [id, handle] of [...this.layers.entries()]) {
      if (!(id in wanted)) {
        this.disposeLayer(id, handle);
      }
    }

    // Build or update the rest.
    for (const [id, level] of Object.entries(wanted) as [LayerId, number][]) {
      let handle = this.layers.get(id);
      if (!handle) {
        const timers: number[] = [];
        const parts = buildLayer(ctx, id, timers);
        const gain = ctx.createGain();
        gain.gain.value = 0.0001;
        parts.input.forEach((node) => node.connect(gain));
        gain.connect(this.master);
        handle = { gain, sources: parts.input.filter((n) => 'start' in n) as AudioScheduledSourceNode[], timers };
        this.layers.set(id, handle);
      }
      handle.gain.gain.cancelScheduledValues(now);
      handle.gain.gain.setTargetAtTime(level * 0.55, now, 0.08);
    }
  }

  /** Fades out and tears everything down. */
  stop(fadeSeconds = 0.45): void {
    const ctx = this.ctx;
    const layers = [...this.layers.entries()];
    this.layers.clear();

    if (!ctx) return;
    const now = ctx.currentTime;

    for (const [, handle] of layers) {
      try {
        handle.gain.gain.cancelScheduledValues(now);
        handle.gain.gain.setValueAtTime(Math.max(0.0001, handle.gain.gain.value), now);
        handle.gain.gain.exponentialRampToValueAtTime(0.0001, now + fadeSeconds);
      } catch {
        // already stopped
      }
    }

    window.setTimeout(() => {
      for (const [, handle] of layers) {
        handle.timers.forEach((timer) => window.clearTimeout(timer));
        handle.sources.forEach((source) => {
          try {
            source.stop();
          } catch {
            // already stopped
          }
          try {
            source.disconnect();
          } catch {
            // already disconnected
          }
        });
        try {
          handle.gain.disconnect();
        } catch {
          // already disconnected
        }
      }
    }, Math.round(fadeSeconds * 1000) + 40);
  }

  /** Short, gentle cue used by the breathing pacer and phase changes. */
  cue(frequency = 440, seconds = 0.5, level = 0.12): void {
    const ctx = this.ensureContext();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    const now = ctx.currentTime;
    osc.type = 'sine';
    osc.frequency.setValueAtTime(frequency, now);
    gain.gain.setValueAtTime(0.0001, now);
    gain.gain.exponentialRampToValueAtTime(level, now + 0.06);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + seconds);
    osc.connect(gain).connect(ctx.destination);
    osc.start(now);
    osc.stop(now + seconds + 0.05);
  }

  private disposeLayer(id: LayerId, handle: LayerHandle): void {
    this.layers.delete(id);
    handle.timers.forEach((timer) => window.clearTimeout(timer));
    handle.sources.forEach((source) => {
      try {
        source.stop();
      } catch {
        // already stopped
      }
      try {
        source.disconnect();
      } catch {
        // already disconnected
      }
    });
    try {
      handle.gain.disconnect();
    } catch {
      // already disconnected
    }
  }

  private ensureContext(): AudioContext | null {
    if (!this.ctx) {
      this.ctx = getAudioContext();
      if (this.ctx && !this.master) {
        this.master = this.ctx.createGain();
        this.master.gain.value = 0.0001;
        this.master.connect(this.ctx.destination);
      }
    }
    if (this.ctx?.state === 'suspended') void this.ctx.resume();
    return this.ctx;
  }
}

export const soundStudio = new SoundStudioEngine();
