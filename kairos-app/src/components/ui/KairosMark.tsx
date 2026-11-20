import {
  BookOpen,
  Brain,
  Code2,
  Compass,
  Droplets,
  Dumbbell,
  Flame,
  Heart,
  Leaf,
  Moon,
  Music,
  PenTool,
  Shield,
  Sparkles,
  Sun,
  Target,
} from '@/components/ui/icons';
import type { HabitIconName } from '@/types/domain';

/**
 * The KAIROS mark, traced to vector from the shipped app icon
 * (assets/kairos-mark.png → 4 contours, 172 path characters, IoU 0.93).
 * Filled with `currentColor`, so colour is decided by the caller.
 */
export const KAIROS_MARK_PATHS = [
  'M0,0 21,0 21,194 1,214Z',
  'M256,0 389,2 21,371 21,432 0,432 1,255Z',
  'M264,19 21,263 21,343 343,20Z',
  'M196,239 388,432 249,432 219,402 125,307 139,292 260,413 341,413 185,256 183,251Z',
] as const;

export const KAIROS_MARK_VIEWBOX = { width: 389, height: 432 } as const;

interface KairosLogoMarkProps {
  /** Rendered height in px (width follows the 389:432 ratio). */
  size?: number;
  className?: string;
  /** Provide an accessible name when the mark stands alone. */
  title?: string;
}

export function KairosLogoMark({ size = 28, className = '', title }: KairosLogoMarkProps) {
  const width = (size * KAIROS_MARK_VIEWBOX.width) / KAIROS_MARK_VIEWBOX.height;
  return (
    <svg
      width={width}
      height={size}
      viewBox={`0 0 ${KAIROS_MARK_VIEWBOX.width} ${KAIROS_MARK_VIEWBOX.height}`}
      fill="currentColor"
      fillRule="evenodd"
      xmlns="http://www.w3.org/2000/svg"
      role={title ? 'img' : undefined}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      className={className}
      focusable="false"
    >
      {title ? <title>{title}</title> : null}
      {KAIROS_MARK_PATHS.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}

/** Mark inside the brand tile — used by the app header and the settings hero. */
export function KairosMarkTile({
  size = 26,
  radius,
}: {
  size?: number;
  radius?: number;
}) {
  const r = radius ?? Math.round(size * 0.3);
  return (
    <span
      className="inline-flex shrink-0 items-center justify-center"
      style={{
        width: size,
        height: size,
        borderRadius: r,
        background: 'var(--surface-2)',
        boxShadow: 'inset 0 0 0 1px var(--border-strong)',
        color: 'var(--accent-fg)',
      }}
    >
      <KairosLogoMark size={Math.round(size * 0.58)} />
    </span>
  );
}

export const HABIT_ICON_OPTIONS: { name: HabitIconName; label: string }[] = [
  { name: 'brain', label: 'تمرکز' },
  { name: 'book', label: 'مطالعه' },
  { name: 'dumbbell', label: 'ورزش' },
  { name: 'droplets', label: 'آب' },
  { name: 'moon', label: 'خواب' },
  { name: 'flame', label: 'انرژی' },
  { name: 'code', label: 'کدنویسی' },
  { name: 'pen', label: 'نوشتن' },
  { name: 'leaf', label: 'آرامش' },
  { name: 'music', label: 'موسیقی' },
  { name: 'shield', label: 'انضباط' },
  { name: 'compass', label: 'مسیر' },
  { name: 'sparkles', label: 'خلاقیت' },
  { name: 'heart', label: 'سلامت' },
  { name: 'sun', label: 'سحرخیزی' },
  { name: 'target', label: 'هدف' },
];

export const HABIT_ACCENT_SWATCHES = [
  '#6D4AFF', // Royal Purple
  '#B9A8FF', // Lavender
  '#7C5CFF', // Amethyst
  '#6E45BC', // Deep Violet
  '#C9975C', // Warm Bronze Gold (subtle luxury contrast)
  '#C26D96', // Muted Velvet Rose-Plum
] as const;

export function HabitIcon({
  name,
  className = 'h-4 w-4',
  color,
}: {
  name: HabitIconName;
  className?: string;
  color?: string;
}) {
  const props = { className, style: color ? { color } : undefined, strokeWidth: 1.85 };
  switch (name) {
    case 'book':
      return <BookOpen {...props} />;
    case 'dumbbell':
      return <Dumbbell {...props} />;
    case 'brain':
      return <Brain {...props} />;
    case 'droplets':
      return <Droplets {...props} />;
    case 'moon':
      return <Moon {...props} />;
    case 'flame':
      return <Flame {...props} />;
    case 'code':
      return <Code2 {...props} />;
    case 'pen':
      return <PenTool {...props} />;
    case 'leaf':
      return <Leaf {...props} />;
    case 'music':
      return <Music {...props} />;
    case 'shield':
      return <Shield {...props} />;
    case 'compass':
      return <Compass {...props} />;
    case 'sparkles':
      return <Sparkles {...props} />;
    case 'heart':
      return <Heart {...props} />;
    case 'sun':
      return <Sun {...props} />;
    case 'target':
    default:
      return <Target {...props} />;
  }
}
