/**
 * Icon layer.
 *
 * The app used Lucide's thin 2px outlines; we moved to Phosphor (regular weight)
 * which has a warmer, more precise optical grid and a matching fill weight for
 * active states. Everything is re-exported under the previous names so feature
 * files stay readable and unchanged.
 *
 * Usage: `import { Plus, Check } from '@/components/ui/icons'`
 */
export {
  // alerts / status
  WarningCircle as AlertCircle,
  Warning as AlertTriangle,
  CheckCircle as CheckCircle2,
  Info,
  // navigation / structure
  Archive,
  ArrowDownRight,
  ArrowsLeftRight as ArrowLeftRight,
  ArrowRight,
  ArrowsDownUp as ArrowUpDown,
  ArrowUpLeft,
  ArrowUpRight,
  SquaresFour as Grid2x2,
  SquaresFour as LayoutDashboard,
  GridFour as LayoutGrid,
  ListDashes as LayoutList,
  Columns as Columns3,
  Rows as Rows3,
  List,
  ListChecks,
  CaretDown as ChevronDown,
  CaretLeft as ChevronLeft,
  CaretRight as ChevronRight,
  CaretUp as ChevronUp,
  DotsThree as MoreHorizontal,
  GearSix as Settings,
  MagnifyingGlass as Search,
  ArrowElbowDownLeft as CornerDownLeft,
  Export as Share2,
  Copy,
  // actions
  Check,
  CheckSquare,
  Minus,
  Plus,
  Trash as Trash2,
  PencilSimple as Edit3,
  DownloadSimple as Download,
  UploadSimple as Upload,
  Table as TableIcon,
  ArrowCounterClockwise as RotateCcw,
  ArrowsClockwise as RefreshCw,
  ArrowCounterClockwise as ArchiveRestore,
  CornersOut as Maximize2,
  CornersIn as Minimize2,
  Broadcast as Radio,
  Sliders,
  SlidersHorizontal,
  Palette,
  PaintBrush,
  CheckCircle,
  // media / time
  Play,
  Pause,
  SkipForward,
  Repeat,
  Timer,
  Clock,
  CalendarBlank as Calendar,
  CalendarDots as CalendarDays,
  // data / analytics
  ChartBar as BarChart3,
  TrendUp as TrendingUp,
  Medal as Award,
  Trophy,
  Target,
  // habits & lifestyle
  Brain,
  BookOpen,
  Barbell as Dumbbell,
  Drop as Droplets,
  Moon,
  Flame,
  Code as Code2,
  PenNib as PenTool,
  Leaf,
  MusicNotes as Music,
  Shield,
  ShieldCheck,
  Compass,
  Sparkle as Sparkles,
  Heart,
  Sun,
  Tag,
  Waves,
  CloudRain,
  // system
  Bell,
  Database,
  DeviceMobile as Smartphone,
  User,
  SpeakerHigh as Volume2,
  SpeakerSlash as VolumeX,
  X,
} from '@phosphor-icons/react';

export { IconContext } from '@phosphor-icons/react';
export type { IconProps, IconWeight, Icon as PhosphorIcon } from '@phosphor-icons/react';
