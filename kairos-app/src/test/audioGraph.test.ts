import { beforeEach, describe, expect, it, vi } from 'vitest';

const context = vi.hoisted(() => ({ current: null as unknown }));
vi.mock('@/lib/audioContext', () => ({ getAudioContext: () => context.current }));
import { soundStudio } from '@/lib/audio/engine';

function param() {
  return { value: 0, cancelScheduledValues: vi.fn(), setTargetAtTime: vi.fn(), setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() };
}
function node(kind: string) {
  return { kind, connections: [] as unknown[], connect: vi.fn(function (this: { connections: unknown[] }, target: unknown) { this.connections.push(target); return target; }), disconnect: vi.fn(), start: vi.fn(), stop: vi.fn(), gain: param(), frequency: param(), Q: param(), pan: param(), buffer: null as unknown };
}

describe('web sound graph', () => {
  beforeEach(() => { context.current = null; });
  it('starts the noise source, routes through filters and disposes modulators', () => {
    const source = node('source');
    const filters: ReturnType<typeof node>[] = [];
    const gains: ReturnType<typeof node>[] = [];
    const oscillators: ReturnType<typeof node>[] = [];
    context.current = {
      currentTime: 0, state: 'running', sampleRate: 100,
      destination: node('destination'),
      createBuffer: () => ({ getChannelData: () => new Float32Array(400) }),
      createBufferSource: () => source,
      createBiquadFilter: () => { const n = node('filter'); filters.push(n); return n; },
      createGain: () => { const n = node('gain'); gains.push(n); return n; },
      createOscillator: () => { const n = node('osc'); oscillators.push(n); return n; },
      createDynamicsCompressor: () => ({ ...node('limiter'), threshold: param(), knee: param(), ratio: param(), attack: param(), release: param() }),
    };
    soundStudio.apply({ rain: 0.4 });
    expect(source.start).toHaveBeenCalledOnce();
    expect(source.connections).toContain(filters[0]);
    expect(source.connections).not.toContain(gains[gains.length - 1]);
    expect(filters[0].connections).toContain(filters[1]);
    expect(filters[1].connections).toContain(gains[gains.length - 1]);
    soundStudio.apply({ brown: 0.4 });
    expect(oscillators[0].stop).toHaveBeenCalledOnce();
    soundStudio.stop(0);
  });
});
