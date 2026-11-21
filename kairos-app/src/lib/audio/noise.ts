/**
 * Noise generators.
 *
 * Pure sample generation, no Web Audio types: the buffers are handed to an
 * AudioBufferSourceNode by the engine, and the tests can assert their spectra
 * and bounds without an audio device.
 */

export type NoiseColor = 'white' | 'pink' | 'brown';

/**
 * Fills `out` with the requested noise colour.
 *
 * - white: uniform, flat spectrum.
 * - pink: −3 dB/octave (Voss-McCartney style filter bank, seven rows).
 * - brown: −6 dB/octave (leaky integrator), normalised so it cannot clip.
 */
export function fillNoise(out: Float32Array, color: NoiseColor, seed = 1): Float32Array {
  let state = seed >>> 0;

  // xorshift32 — deterministic per seed, so tests and profiles are reproducible.
  const random = () => {
    state ^= state << 13;
    state >>>= 0;
    state ^= state >> 17;
    state ^= state << 5;
    state >>>= 0;
    return state / 0xffffffff;
  };

  if (color === 'white') {
    for (let i = 0; i < out.length; i += 1) out[i] = random() * 2 - 1;
    return scaleToPeak(out, 0.88);
  }

  if (color === 'brown') {
    let last = 0;
    for (let i = 0; i < out.length; i += 1) {
      const white = random() * 2 - 1;
      last = (last + 0.02 * white) / 1.02;
      out[i] = last;
    }
    return scaleToPeak(out, 0.9);
  }

  // Pink: seven octave bands summed and normalised.
  const rows = new Float32Array(7);
  const spread = [0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1];
  for (let i = 0; i < out.length; i += 1) {
    const white = random() * 2 - 1;
    let sum = 0;
    for (let r = 0; r < rows.length; r += 1) {
      if (random() < spread[r]) rows[r] = white;
      sum += rows[r];
    }
    out[i] = sum / rows.length;
  }
  return scaleToPeak(out, 0.9);
}

/** Scales a buffer so its loudest sample sits at `target` (never clips). */
function scaleToPeak(out: Float32Array, target: number): Float32Array {
  const peak = peakOf(out);
  if (peak === 0) return out;
  const gain = target / peak;
  for (let i = 0; i < out.length; i += 1) out[i] *= gain;
  return out;
}

/** Peak absolute sample — used by tests and by the engine's safety clamp. */
export function peakOf(buffer: Float32Array): number {
  let peak = 0;
  for (let i = 0; i < buffer.length; i += 1) peak = Math.max(peak, Math.abs(buffer[i]));
  return peak;
}

/** Rough spectral tilt indicator: ratio of low-band to high-band energy. */
export function lowToHighRatio(buffer: Float32Array): number {
  let low = 0;
  let high = 0;
  for (let i = 1; i < buffer.length; i += 1) {
    const diff = buffer[i] - buffer[i - 1];
    low += buffer[i] * buffer[i];
    high += diff * diff;
  }
  return high === 0 ? Infinity : low / high;
}
