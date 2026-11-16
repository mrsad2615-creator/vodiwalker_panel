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
} from 'lucide-react';
import type { HabitIconName } from '@/types/domain';

export function KairosLogoMark({ size = 28 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <rect
        x="1"
        y="1"
        width="38"
        height="38"
        rx="11"
        fill="#15141B"
        stroke="#B69AEF"
        strokeOpacity="0.24"
      />
      <circle
        cx="20"
        cy="20"
        r="12.5"
        stroke="url(#kairosGrad)"
        strokeWidth="2.1"
        strokeLinecap="round"
        strokeDasharray="60 19"
      />
      <path
        d="M13 26.5V15L20 22.5L27 15V26.5"
        stroke="#F4F1F8"
        strokeWidth="2.3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="20" cy="11.8" r="1.6" fill="#B69AEF" />
      <defs>
        <linearGradient id="kairosGrad" x1="8" y1="8" x2="32" y2="32" gradientUnits="userSpaceOnUse">
          <stop stopColor="#B69AEF" />
          <stop offset="1" stopColor="#8052D6" />
        </linearGradient>
      </defs>
    </svg>
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
  '#8052D6', // Royal Purple
  '#B69AEF', // Lavender
  '#9A6CE8', // Amethyst
  '#6E45BC', // Deep Violet
  '#C48B5C', // Warm Bronze Gold (subtle luxury contrast)
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
