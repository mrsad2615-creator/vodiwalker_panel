import { describe, expect, it, vi } from 'vitest';
import { BlackHoleExperience } from './black-hole.js';

describe('WebGL graceful fallback', () => {
  it('reports an unavailable rendering context instead of leaving an empty canvas', () => {
    const contextSpy = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const onUnavailable = vi.fn();
    const container = document.createElement('div');
    document.body.append(container);

    try {
      expect(() => new BlackHoleExperience(container, { onUnavailable })).toThrow(/WebGL/i);
      expect(onUnavailable).toHaveBeenCalledOnce();
      expect(container.querySelector('canvas')).toBeNull();
    } finally {
      contextSpy.mockRestore();
      errorSpy.mockRestore();
      container.remove();
    }
  });
});
