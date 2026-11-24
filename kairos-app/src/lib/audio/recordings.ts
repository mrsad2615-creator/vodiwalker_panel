import type { LayerId, SoundMix } from './layers';

/** Packaged recordings, never network streams. The other five layers are synthesised. */
export const RECORDING_FILES: Partial<Record<LayerId, string>> = {
  rain: 'rain.ogg', ocean: 'waves.ogg', wind: 'wind.ogg', fire: 'fireplace.ogg',
  forest: 'birds.ogg', cafe: 'coffee-shop.ogg',
};

export function splitRecordedMix(mix: SoundMix): { recorded: SoundMix; generated: SoundMix } {
  const recorded: SoundMix = {};
  const generated: SoundMix = {};
  for (const [key, value] of Object.entries(mix) as [LayerId, number][]) {
    (RECORDING_FILES[key] ? recorded : generated)[key] = value;
  }
  return { recorded, generated };
}

/** Browser playback; the Android foreground service has its own native players. */
export class RecordedWebMixer {
  private players = new Map<LayerId, HTMLAudioElement>();
  private pending = new Map<LayerId, Promise<void>>();
  private generation = 0;

  async apply(mix: SoundMix, master: number): Promise<void> {
    const currentGeneration = ++this.generation;
    const { recorded } = splitRecordedMix(mix);
    const total = Object.values(mix).reduce<number>((sum, value) => sum + (value ?? 0), 0);
    const divisor = Math.max(1, total);
    for (const [id, player] of this.players) {
      if (!recorded[id]) { player.pause(); this.players.delete(id); this.pending.delete(id); }
    }
    const starts: Promise<void>[] = [];
    for (const [id, level] of Object.entries(recorded) as [LayerId, number][]) {
      let player = this.players.get(id);
      if (!player) {
        player = new Audio(`${import.meta.env.BASE_URL}sounds/${RECORDING_FILES[id]}`);
        player.loop = true;
        player.preload = 'auto';
        this.players.set(id, player);
        const entry = player;
        const start = player.play().then(() => {
          if (this.players.get(id) !== entry) entry.pause();
        });
        this.pending.set(id, start);
        void start.then(() => {
          if (this.pending.get(id) === start) this.pending.delete(id);
        }, () => {
          if (this.pending.get(id) === start) this.pending.delete(id);
        });
      }
      const pending = this.pending.get(id);
      if (pending) starts.push(pending);
      player.volume = Math.max(0, Math.min(1, master * level * 0.7 / divisor));
    }
    try { await Promise.all(starts); }
    catch (error) {
      if (currentGeneration !== this.generation) return;
      this.stop();
      throw error;
    }
  }

  stop(): void {
    this.generation++;
    for (const player of this.players.values()) { player.pause(); player.currentTime = 0; }
    this.players.clear();
    this.pending.clear();
  }
}

export const recordedWebMixer = new RecordedWebMixer();
