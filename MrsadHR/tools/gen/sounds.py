#!/usr/bin/env python3
"""
MrsadHR sound design generator.

Every completion sound shipped with the app is synthesised from scratch here, so the asset set is
original (no sampled or third party material) and fully reproducible:

    python3 tools/gen/sounds.py app/src/main/assets/sounds
"""
import math
import os
import struct
import sys
import wave

import numpy as np

SR = 44100


def t_axis(dur):
    return np.linspace(0.0, dur, int(SR * dur), endpoint=False)


def sine(freq, dur, phase=0.0):
    t = t_axis(dur)
    if np.isscalar(freq):
        return np.sin(2 * np.pi * freq * t + phase)
    freq = np.asarray(freq)
    phase_acc = np.cumsum(freq) / SR
    return np.sin(2 * np.pi * phase_acc + phase)


def saw(freq, dur, harmonic_count=12):
    t = t_axis(dur)
    out = np.zeros_like(t)
    for h in range(1, harmonic_count + 1):
        out += np.sin(2 * np.pi * freq * h * t) / h
    return out * (2 / np.pi)


def noise(dur, seed=1):
    rng = np.random.default_rng(seed)
    t = t_axis(dur)
    return rng.standard_normal(t.size)


def env(dur, attack=0.005, decay=0.0, sustain=1.0, release=0.2):
    t = t_axis(dur)
    n = t.size
    a = max(1, int(attack * SR))
    d = int(decay * SR)
    r = max(1, int(release * SR))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a)
    if d > 0:
        seg = min(d, n - a)
        e[a:a + seg] = np.linspace(1, sustain, seg)
        e[a + seg:] = sustain
    if r < n:
        e[n - r:] *= np.linspace(1, 0, r) ** 1.5
    return e


def exp_decay(dur, tau):
    return np.exp(-t_axis(dur) / tau)


def lowpass(x, cutoff, resonance=0.0):
    a = math.exp(-2 * math.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(x.size):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    if resonance > 0:
        y = y + resonance * (y - lowpass(y, cutoff * 0.5))
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def reverb(x, decay=0.42, mix=0.32, size=1.0):
    """Compact Schroeder reverb: 4 combs + 2 allpass, cheap and musical."""
    comb_delays = [int(d * size) for d in (1557, 1617, 1491, 1422)]
    out = np.zeros(x.size + max(comb_delays) + 1)
    acc = np.zeros_like(out)
    for d in comb_delays:
        buf = np.zeros(out.size)
        idx = 0
        store = np.zeros(d)
        p = 0
        for i in range(x.size):
            y = store[p]
            store[p] = x[i] + y * decay
            buf[i] = y
            p = (p + 1) % d
        acc += buf
    acc /= len(comb_delays)
    for d, g in ((225, 0.5), (556, 0.5)):
        buf = np.zeros_like(acc)
        store = np.zeros(d)
        p = 0
        for i in range(acc.size):
            buf[i] = -g * acc[i] + store[p]
            store[p] = acc[i] + g * buf[i]
            p = (p + 1) % d
        acc = buf
    wet = acc[:x.size]
    return (1 - mix) * x + mix * wet


def normalize(x, peak=0.92):
    m = np.max(np.abs(x))
    if m < 1e-9:
        return x
    return x / m * peak


def fade(x, in_s=0.004, out_s=0.05):
    n = x.size
    a = min(n, int(in_s * SR))
    b = min(n, int(out_s * SR))
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b) ** 1.4
    return x


def write(path, x, peak=0.92):
    x = normalize(fade(x.astype(np.float64)), peak)
    data = np.clip(x, -1.0, 1.0)
    pcm = (data * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    return x.size / SR


# --------------------------------------------------------------------------- sounds


def minimal_tick():
    d = 1.2
    click = highpass(noise(d, seed=3), 2500) * exp_decay(d, 0.008)
    body = sine(2100, d) * exp_decay(d, 0.03) * 0.35
    tail = sine(740, d) * exp_decay(d, 0.12) * 0.18
    return reverb(click * 0.8 + body + tail, 0.25, 0.22)


def minimal_bell():
    d = 2.6
    partials = [(784, 1.0, 0.55), (1176, 0.55, 0.34), (1568, 0.35, 0.22), (2350, 0.22, 0.14), (3136, 0.12, 0.08)]
    out = np.zeros(int(SR * d))
    for freq, amp, tau in partials:
        out += sine(freq, d) * np.exp(-t_axis(d) / tau) * amp
    strike = highpass(noise(d, seed=7), 4000) * exp_decay(d, 0.006) * 0.25
    return reverb(out * 0.7 + strike, 0.5, 0.35)


def minimal_pulse():
    d = 1.8
    out = np.zeros(int(SR * d))
    for start, freq in ((0.0, 392.0), (0.42, 523.25)):
        seg = sine(freq, 0.7) * env(0.7, 0.02, 0.1, 0.55, 0.5) * 0.6
        i = int(start * SR)
        out[i:i + seg.size] += seg[: out.size - i]
    out += sine(196, d) * exp_decay(d, 0.4) * 0.12
    return lowpass(out, 3200)


def digital_beep():
    d = 1.5
    out = np.zeros(int(SR * d))
    for k, start in enumerate((0.0, 0.24, 0.48)):
        seg_d = 0.16
        t = t_axis(seg_d)
        tone = np.sign(np.sin(2 * np.pi * 1046.5 * t)) * 0.35 + sine(1046.5, seg_d) * 0.5
        seg = tone * env(seg_d, 0.004, 0.02, 0.8, 0.05)
        i = int(start * SR)
        out[i:i + seg.size] += seg
    return lowpass(out, 7000, 0.2)


def digital_chime():
    d = 2.4
    out = np.zeros(int(SR * d))
    notes = [523.25, 659.25, 783.99, 1046.5, 1318.5]
    for k, f in enumerate(notes):
        start = k * 0.10
        seg_d = 1.4
        bell = (
            sine(f, seg_d) * 0.6
            + sine(f * 2.01, seg_d) * 0.22
            + sine(f * 3.02, seg_d) * 0.10
        ) * np.exp(-t_axis(seg_d) / 0.5) * env(seg_d, 0.004, 0.0, 1.0, 0.4)
        i = int(start * SR)
        out[i:i + bell.size] += bell * (1.0 - k * 0.10)
    return reverb(out, 0.45, 0.3)


def digital_arcade():
    d = 1.8
    out = np.zeros(int(SR * d))
    notes = [392, 523.25, 659.25, 784, 1046.5, 1318.5]
    for k, f in enumerate(notes):
        start = k * 0.075
        seg_d = 0.22
        t = t_axis(seg_d)
        tone = np.sign(np.sin(2 * np.pi * f * t)) * 0.4
        seg = tone * env(seg_d, 0.002, 0.05, 0.7, 0.08)
        i = int(start * SR)
        out[i:i + seg.size] += seg * (0.7 + 0.3 * (k / len(notes)))
    return highpass(out, 120)


def cinematic_impact():
    d = 3.2
    t = t_axis(d)
    sweep = 62 * np.exp(-t / 0.5) + 34
    sub = sine(sweep, d) * np.exp(-t / 1.1) * 1.0
    thump = sine(90, d) * np.exp(-t / 0.18) * 0.5
    air = lowpass(noise(d, seed=11), 900) * np.exp(-t / 0.25) * 0.45
    tail = reverb(sub * 0.6 + thump, 0.62, 0.42)
    return lowpass(sub + thump + air + tail, 6000)


def cinematic_riser():
    d = 2.6
    t = t_axis(d)
    rise = np.clip(t / d, 0, 1) ** 1.6
    swell = sine(110 * np.exp(t / d * 1.4), d) * 0.4 * rise
    air = highpass(noise(d, seed=13), 700) * 0.35 * rise ** 2
    hit_t = 2.05
    hit = np.zeros_like(t)
    idx = int(hit_t * SR)
    seg_d = d - hit_t
    seg = (sine(70, seg_d) * np.exp(-t_axis(seg_d) / 0.5) + lowpass(noise(seg_d, seed=17), 1200) * np.exp(-t_axis(seg_d) / 0.3) * 0.4)
    hit[idx:idx + seg.size] = seg[: hit.size - idx]
    return reverb(swell + air + hit, 0.55, 0.34)


def cinematic_bloom():
    d = 3.6
    chord = [130.81, 196.0, 261.63, 329.63, 392.0]
    out = np.zeros(int(SR * d))
    for i, f in enumerate(chord):
        voice = saw(f, d, 8) * 0.22
        voice *= env(d, 0.9, 0.4, 0.8, 1.6)
        det = saw(f * 1.004, d, 6) * 0.12
        out += voice + det
    out = lowpass(out, 2400)
    return reverb(out, 0.6, 0.4)


def ambient_ocean():
    d = 4.0
    t = t_axis(d)
    surf = lowpass(noise(d, seed=21), 900) * (0.45 + 0.35 * np.sin(2 * np.pi * 0.22 * t - 1.2))
    wash = lowpass(noise(d, seed=23), 300) * 0.5
    hiss = highpass(noise(d, seed=29), 5000) * 0.05
    return reverb(surf + wash + hiss, 0.5, 0.28)


def ambient_rain():
    d = 4.0
    base = lowpass(noise(d, seed=31), 4200) * 0.35
    drops = np.zeros(int(SR * d))
    rng = np.random.default_rng(37)
    for _ in range(90):
        start = rng.uniform(0, d - 0.12)
        f = rng.uniform(1200, 4200)
        seg_d = 0.07
        seg = sine(f, seg_d) * np.exp(-t_axis(seg_d) / 0.014) * rng.uniform(0.05, 0.16)
        i = int(start * SR)
        drops[i:i + seg.size] += seg
    return reverb(base + drops, 0.4, 0.3)


def ambient_pad():
    d = 4.2
    t = t_axis(d)
    freqs = [174.61, 261.63, 349.23, 523.25]
    out = np.zeros_like(t)
    for i, f in enumerate(freqs):
        breath = 0.6 + 0.4 * np.sin(2 * np.pi * (0.18 + i * 0.03) * t + i)
        out += sine(f, d) * 0.25 * breath
        out += sine(f * 1.003, d) * 0.12 * breath
    out *= env(d, 0.7, 0.3, 0.85, 1.4)
    return lowpass(reverb(out, 0.5, 0.3), 3800)


GENERATORS = {
    "minimal_tick": minimal_tick,
    "minimal_bell": minimal_bell,
    "minimal_pulse": minimal_pulse,
    "digital_beep": digital_beep,
    "digital_chime": digital_chime,
    "digital_arcade": digital_arcade,
    "cinematic_impact": cinematic_impact,
    "cinematic_riser": cinematic_riser,
    "cinematic_bloom_cinematic": cinematic_bloom,
    "cinematic_bloom": cinematic_bloom,
    "ambient_ocean": ambient_ocean,
    "ambient_rain": ambient_rain,
    "ambient_pad": ambient_pad,
}


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/sounds"
    os.makedirs(out_dir, exist_ok=True)
    total = 0
    for name, fn in GENERATORS.items():
        path = os.path.join(out_dir, name + ".wav")
        dur = write(path, fn(), peak=0.88)
        size = os.path.getsize(path)
        total += size
        print(f"{name:22s} {dur:4.2f}s  {size/1024:7.1f} KB")
    print(f"total {total/1024/1024:.2f} MB")


if __name__ == "__main__":
    main()
