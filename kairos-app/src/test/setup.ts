import 'fake-indexeddb/auto';
import '@testing-library/jest-dom/vitest';

// Mock scrollTo and matchMedia for JSDOM
if (typeof window !== 'undefined') {
  window.scrollTo = () => {};
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// jsdom has no media playback. Tests model a successful browser gesture here;
// codec, audio output, and Android playback must be verified separately.
if (typeof HTMLMediaElement !== 'undefined') {
  HTMLMediaElement.prototype.play = () => Promise.resolve();
  HTMLMediaElement.prototype.pause = () => {};
}
