import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const ROOT = join(process.cwd(), 'src');
const CSS = readFileSync(join(ROOT, 'styles/design-system.css'), 'utf8');

/**
 * Files allowed to hold literal colours, each for a stated reason:
 *  - KairosMark: the approved logo artwork + the habit colour swatches (data).
 *  - LaunchScreen: the monochrome wordmark of the splash.
 *  - lib/theme.ts: ACCENT_PRESETS must be literal hex — deriveAccent() parses it.
 *  - lib/seed.ts: sample habit rows carry real colour values, like user data.
 */
const ALLOWED = [
  'components/ui/KairosMark.tsx',
  'components/ui/LaunchScreen.tsx',
  'lib/theme.ts',
  'lib/seed.ts',
];

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry);
    return statSync(full).isDirectory() ? walk(full) : [full];
  });
}

const sources = walk(ROOT)
  .filter((file) => /\.tsx?$/.test(file) && !file.includes('/test/'))
  .map((file) => ({ path: relative(ROOT, file), code: readFileSync(file, 'utf8') }));

describe('design system standards', () => {
  it('has no literal colours outside the logo and splash', () => {
    const offenders = sources
      .filter((file) => !ALLOWED.includes(file.path))
      .map((file) => ({
        path: file.path,
        hexes: [...file.code.matchAll(/#[0-9A-Fa-f]{6}\b/g)].map((m) => m[0]),
      }))
      .filter((file) => file.hexes.length > 0);

    expect(offenders).toEqual([]);
  });

  it('defines the rail primitive and the eyebrow, and keeps one radius scale', () => {
    expect(CSS).toMatch(/\.rail\s*\{[^}]*border-radius: var\(--r-rail\)/);
    expect(CSS).toContain('.rail-row');
    expect(CSS).toContain('.rail-head');
    expect(CSS).toMatch(/\.eyebrow\s*\{/);
    // only two surface radii for containers
    expect(CSS).toMatch(/--r-rail: 16px;/);
    expect(CSS).toMatch(/--r-card: 16px;/);
  });

  it('keeps one accent and no per-section palette', () => {
    const sections = readFileSync(join(ROOT, 'lib/sections.ts'), 'utf8');
    expect(sections).not.toMatch(/hueShift/);
    expect(sections).not.toMatch(/atmosphere/);
    expect(sections).not.toMatch(/rotateHue/);
  });

  it('keeps the three motion durations and one easing', () => {
    expect(CSS).toMatch(/--dur-fast: 120ms/);
    expect(CSS).toMatch(/--dur-base: 200ms/);
    expect(CSS).toMatch(/--dur-slow: 320ms/);
    expect(CSS).toMatch(/--ease-out: cubic-bezier/);
  });

  it('never lets the grain layer become full-screen static', () => {
    const grains = [...CSS.matchAll(/--atmosphere-grain:\s*([\d.]+)/g)].map((m) => Number(m[1]));
    expect(grains.length).toBeGreaterThan(4);
    for (const value of grains) expect(value).toBeLessThanOrEqual(0.03);
  });
});
