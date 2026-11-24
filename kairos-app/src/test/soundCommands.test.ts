import { describe, expect, it, vi, afterEach } from 'vitest';
import { SOUND_COMMAND_EVENT, requestSound } from '@/lib/audio/commands';
import { soundStudio } from '@/lib/audio/engine';

afterEach(() => vi.restoreAllMocks());

describe('sound commands', () => {
  it('does not start a second engine when the mounted studio handles a command', () => {
    const apply = vi.spyOn(soundStudio, 'apply').mockImplementation(() => undefined);
    const handle = (event: Event) => event.preventDefault();
    window.addEventListener(SOUND_COMMAND_EVENT, handle);
    try { requestSound({ type: 'profile', profileId: 'deep' }); expect(apply).not.toHaveBeenCalled(); }
    finally { window.removeEventListener(SOUND_COMMAND_EVENT, handle); }
  });
  it('still falls back when no studio is mounted', () => {
    const apply = vi.spyOn(soundStudio, 'apply').mockImplementation(() => undefined);
    requestSound({ type: 'profile', profileId: 'deep' });
    expect(apply).toHaveBeenCalledOnce();
  });
});
