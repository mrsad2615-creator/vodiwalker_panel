export type AmbientSoundType = 'off' | 'brown' | 'rain' | 'drone';

class LuxuryAmbientAudioEngine {
  private ctx: AudioContext | null = null;
  private masterGain: GainNode | null = null;
  private activeNodes: AudioNode[] = [];
  private currentType: AmbientSoundType = 'off';

  private ensureContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return null;
    if (!this.ctx) {
      this.ctx = new AudioCtx();
    }
    if (this.ctx.state === 'suspended') {
      void this.ctx.resume().catch(() => {});
    }
    return this.ctx;
  }

  stop(): void {
    this.currentType = 'off';
    const ctx = this.ctx;
    const gain = this.masterGain;
    const nodes = this.activeNodes;
    this.activeNodes = [];
    this.masterGain = null;

    if (ctx && gain) {
      try {
        const now = ctx.currentTime;
        gain.gain.cancelScheduledValues(now);
        gain.gain.setValueAtTime(Math.max(0.0001, gain.gain.value), now);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.35);
        window.setTimeout(() => {
          nodes.forEach((n) => {
            try {
              if ('stop' in n && typeof (n as OscillatorNode).stop === 'function') {
                (n as OscillatorNode).stop();
              }
              n.disconnect();
            } catch {
              // ignore already stopped
            }
          });
          try {
            gain.disconnect();
          } catch {
            // ignore
          }
        }, 380);
      } catch {
        // ignore audio errors
      }
    }
  }

  start(type: AmbientSoundType): void {
    if (type === 'off') {
      this.stop();
      return;
    }
    if (this.currentType === type && this.activeNodes.length > 0) return;

    this.stop();
    const ctx = this.ensureContext();
    if (!ctx) return;

    try {
      const master = ctx.createGain();
      const now = ctx.currentTime;
      master.gain.setValueAtTime(0.0001, now);
      master.connect(ctx.destination);

      const created: AudioNode[] = [];

      if (type === 'brown' || type === 'rain') {
        const bufferSize = ctx.sampleRate * 3;
        const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
        const data = buffer.getChannelData(0);

        if (type === 'brown') {
          let lastOut = 0;
          for (let i = 0; i < bufferSize; i++) {
            const white = Math.random() * 2 - 1;
            data[i] = (lastOut + 0.02 * white) / 1.02;
            lastOut = data[i];
            data[i] *= 2.4;
          }
        } else {
          // Soft pink-weighted gentle rain texture
          let b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
          for (let i = 0; i < bufferSize; i++) {
            const white = Math.random() * 2 - 1;
            b0 = 0.99886 * b0 + white * 0.0555179;
            b1 = 0.99332 * b1 + white * 0.0750759;
            b2 = 0.96900 * b2 + white * 0.1538520;
            b3 = 0.86650 * b3 + white * 0.3104856;
            b4 = 0.55000 * b4 + white * 0.5329522;
            b5 = -0.7616 * b5 - white * 0.0168980;
            data[i] = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.06;
            b6 = white * 0.115926;
          }
        }

        const source = ctx.createBufferSource();
        source.buffer = buffer;
        source.loop = true;

        const filter = ctx.createBiquadFilter();
        filter.type = type === 'brown' ? 'lowpass' : 'bandpass';
        filter.frequency.setValueAtTime(type === 'brown' ? 320 : 950, now);
        filter.Q.setValueAtTime(type === 'brown' ? 0.7 : 0.45, now);

        source.connect(filter);
        filter.connect(master);
        source.start();
        created.push(source, filter);

        master.gain.exponentialRampToValueAtTime(type === 'brown' ? 0.09 : 0.12, now + 0.7);
      } else if (type === 'drone') {
        // Warm sub-harmonic deep obsidian ambient chord (110Hz + 165Hz + 220Hz)
        const freqs = [110, 164.81, 220];
        const filter = ctx.createBiquadFilter();
        filter.type = 'lowpass';
        filter.frequency.setValueAtTime(420, now);
        filter.connect(master);
        created.push(filter);

        freqs.forEach((f, idx) => {
          const osc = ctx.createOscillator();
          const g = ctx.createGain();
          osc.type = idx === 0 ? 'sine' : 'triangle';
          osc.frequency.setValueAtTime(f, now);
          g.gain.setValueAtTime(idx === 0 ? 0.38 : 0.16, now);
          osc.connect(g);
          g.connect(filter);
          osc.start();
          created.push(osc, g);
        });

        master.gain.exponentialRampToValueAtTime(0.065, now + 0.9);
      }

      this.masterGain = master;
      this.activeNodes = created;
      this.currentType = type;
    } catch {
      this.stop();
    }
  }
}

export const ambientAudio = new LuxuryAmbientAudioEngine();
