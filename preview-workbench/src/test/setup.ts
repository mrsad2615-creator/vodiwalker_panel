import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
});

// jsdom lacks the Pointer Events constructor used by the canvas hook.
class MockPointerEvent extends Event {
  readonly clientX: number;
  readonly clientY: number;
  readonly pointerId: number;

  constructor(type: string, init: PointerEventInit = {}) {
    super(type, init);
    this.clientX = init.clientX ?? 0;
    this.clientY = init.clientY ?? 0;
    this.pointerId = init.pointerId ?? 1;
  }
}

if (!('PointerEvent' in globalThis)) {
  Object.defineProperty(globalThis, 'PointerEvent', { value: MockPointerEvent, writable: true });
}

// SVG setPointerCapture is not implemented by jsdom.
Object.defineProperty(Element.prototype, 'setPointerCapture', { value: () => undefined, writable: true });
Object.defineProperty(Element.prototype, 'releasePointerCapture', { value: () => undefined, writable: true });

// Stable perf clock for deterministic latency assertions.
if (!globalThis.performance?.now) {
  Object.defineProperty(globalThis, 'performance', { value: { now: () => Date.now() } });
}

// matchMedia is queried by reduced-motion aware components.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});
