/**
 * One AudioContext for the whole app.
 *
 * The old code created a fresh context for every chime and never closed it.
 * Android's WebView allows only a handful of contexts per page (~6), so after a
 * few focus sessions the completion chime silently stopped working. Every
 * consumer (ambient engine, chime, future sound studio) now shares this one.
 */

type AudioCtor = typeof AudioContext;

let context: AudioContext | null = null;
let unlocked = false;

function ctor(): AudioCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as { AudioContext?: AudioCtor; webkitAudioContext?: AudioCtor };
  return w.AudioContext ?? w.webkitAudioContext ?? null;
}

/** Lazily creates (and resumes) the shared context. Returns null where unsupported. */
export function getAudioContext(): AudioContext | null {
  const AudioCtor = ctor();
  if (!AudioCtor) return null;
  if (!context || context.state === 'closed') {
    try {
      context = new AudioCtor({ latencyHint: 'interactive' });
    } catch {
      return null;
    }
  }
  if (context.state === 'suspended') {
    void context.resume().catch(() => undefined);
  }
  return context;
}

/**
 * Autoplay policies require a user gesture before audio may start. Capacitor's
 * WebView is usually already unlocked, but a cold start straight into the
 * focus page is not — so the first pointer/key event unlocks it once.
 */
export function installAudioUnlock(): void {
  if (typeof window === 'undefined' || unlocked) return;
  const unlock = () => {
    unlocked = true;
    const ctx = getAudioContext();
    if (ctx && ctx.state === 'suspended') void ctx.resume().catch(() => undefined);
    window.removeEventListener('pointerdown', unlock);
    window.removeEventListener('keydown', unlock);
  };
  window.addEventListener('pointerdown', unlock, { passive: true });
  window.addEventListener('keydown', unlock, { passive: true });
}

export function getAudioState(): AudioContextState | 'unsupported' {
  if (!context) return ctor() ? 'suspended' : 'unsupported';
  return context.state;
}
