/**
 * Shared chart geometry.
 *
 * Kept in one place so every chart in the product curves, scales and snaps the
 * same way — a sparkline on the home board and the monthly curve must agree about
 * what "a line through these points" means.
 */

export interface Coord {
  x: number;
  y: number;
}

/** Catmull-Rom-ish cubic path: smooth, but never overshoots a real data point. */
export function buildSmoothPath(coords: Coord[], tension = 0.18): string {
  if (coords.length === 0) return '';
  if (coords.length === 1) return `M ${coords[0].x} ${coords[0].y}`;
  let path = `M ${coords[0].x} ${coords[0].y}`;
  for (let i = 0; i < coords.length - 1; i += 1) {
    const p0 = coords[i - 1] ?? coords[i];
    const p1 = coords[i];
    const p2 = coords[i + 1];
    const p3 = coords[i + 2] ?? p2;
    const c1x = p1.x + (p2.x - p0.x) * tension;
    const c1y = p1.y + (p2.y - p0.y) * tension;
    const c2x = p2.x - (p3.x - p1.x) * tension;
    const c2y = p2.y - (p3.y - p1.y) * tension;
    path += ` C ${c1x} ${c1y}, ${c2x} ${c2y}, ${p2.x} ${p2.y}`;
  }
  return path;
}

/** Axis top and ticks on 1/2/5 × power of ten. */
export function niceAxis(max: number, divisions = 3): { top: number; ticks: number[] } {
  if (max <= 0) return { top: 4, ticks: [0, 2, 4] };
  const rough = max / divisions;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const normalized = rough / magnitude;
  const step = (normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 5 ? 5 : 10) * magnitude;
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let value = 0; value <= top + step * 0.001; value += step) ticks.push(Math.round(value));
  return { top, ticks };
}

/** Persian digits, for every label and readout a chart draws itself. */
export const faDigits = (value: number | string): string =>
  String(value).replace(/[0-9]/g, (digit) => '۰۱۲۳۴۵۶۷۸۹'[Number(digit)]);
