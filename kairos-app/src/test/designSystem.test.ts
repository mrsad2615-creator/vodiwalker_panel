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

/* =============================================================================
   KAIROS Paper — the 3.0 rules, held by tests
   -----------------------------------------------------------------------------
   A visual language that only lives in a document drifts with the next feature.
   These assertions are the language: the six type steps, the two-tone group, the
   single wash, and the home screen having no gradient, no glow and no text below
   11px. If a future change wants to break one of them, it has to delete a test
   and say why — which is exactly the conversation we want to have.
   ========================================================================== */

describe('KAIROS Paper — visual rules', () => {
  const HOME = readFileSync(join(ROOT, 'features/dashboard/DashboardPage.tsx'), 'utf8');

  it('defines exactly six type steps and no seventh', () => {
    const steps = [...CSS.matchAll(/--t-(\d):\s*(\d+)px/g)].map((m) => Number(m[2]));
    expect(steps).toEqual([11, 13, 15, 17, 22, 30]);
  });

  it('keeps every home-screen text size at 11px or larger', () => {
    // `text-[9.5px]` and friends were the single biggest reason the old board
    // read as a dashboard instead of a page.
    const sizes = [...HOME.matchAll(/text-\[(\d+(?:\.\d+)?)px\]/g)].map((m) => Number(m[1]));
    expect(sizes.length).toBeGreaterThan(0);
    for (const size of sizes) expect(size).toBeGreaterThanOrEqual(11);
  });

  it('keeps the home screen free of gradients and glows', () => {
    expect(HOME).not.toMatch(/linear-gradient|radial-gradient/i);
    expect(HOME).not.toMatch(/--accent-glow/);
    expect(HOME).not.toMatch(/backdrop-blur/);
  });

  it('uses tone, not borders, for groups', () => {
    expect(CSS).toMatch(/\.k-group\s*\{[^}]*background:\s*var\(--surface-1\)/);
    expect(CSS).toMatch(/\.k-row \+ \.k-row::before\s*\{[^}]*background:\s*var\(--rule\)/);
    // A rail is a plate: no outer border.
    expect(CSS).toMatch(/\.rail\s*\{[^}]*border:\s*0/);
  });

  it('keeps the texture quiet: one wash, grain effectively invisible', () => {
    const grains = [...CSS.matchAll(/--atmosphere-grain:\s*([\d.]+)/g)].map((m) => Number(m[1]));
    expect(grains.length).toBeGreaterThan(0);
    for (const value of grains) expect(value).toBeLessThanOrEqual(0.01);
  });

  it('announces a room in Persian, without Latin tracking', () => {
    // The last `.eyebrow` block is the one that wins in the cascade.
    const blocks = [...CSS.matchAll(/\.eyebrow\s*\{([^}]*)\}/g)].map((m) => m[1]);
    const active = blocks[blocks.length - 1];
    expect(active).toMatch(/letter-spacing:\s*0/);
    expect(active).toMatch(/text-transform:\s*none/);
    expect(active).not.toMatch(/uppercase/);
  });
});
