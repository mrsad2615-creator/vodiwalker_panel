import type { BackgroundId, MotionLevel } from '@/lib/theme';

export type HomeMode = 'cinematic' | 'compact' | 'detailed';

export type NavTab =
  | 'dashboard'
  | 'tasks'
  | 'habits'
  | 'calendar'
  | 'focus'
  | 'analytics'
  | 'settings';

export type TaskStatus = 'todo' | 'in_progress' | 'completed';

/**
 * Maps cleanly to the 4 Eisenhower Matrix quadrants:
 * - q1: مهم و فوری (Urgent & Important — Do First)
 * - q2: مهم و غیرفوری (Important, Not Urgent — Schedule)
 * - q3: غیرمهم و فوری (Urgent, Not Important — Delegate / Quick)
 * - q4: غیرمهم و غیرفوری (Neither — Eliminate / Later)
 */
export type EisenhowerQuadrant = 'q1' | 'q2' | 'q3' | 'q4';

export type TaskPriority = 'high' | 'medium' | 'low';

export type TaskCategory =
  | 'work'
  | 'personal'
  | 'health'
  | 'learning'
  | 'finance'
  | 'creative';

export type RecurrenceRule = 'none' | 'daily' | 'weekdays' | 'weekly' | 'monthly';

export interface Subtask {
  id: string;
  title: string;
  completed: boolean;
}

export interface Task {
  id: string;
  title: string;
  description: string;
  /** Local calendar date YYYY-MM-DD (never UTC-shifted), or null if unscheduled */
  dueDate: string | null;
  /** Optional 24h time string HH:MM */
  dueTime: string | null;
  priority: TaskPriority;
  quadrant: EisenhowerQuadrant;
  category: TaskCategory;
  tags: string[];
  status: TaskStatus;
  subtasks: Subtask[];
  recurrence: RecurrenceRule;
  /** Optional reminder enabled */
  reminderEnabled: boolean;
  /** Reminder lead time in minutes before dueTime (0 = exact time) */
  reminderOffsetMin: number;
  /** Archived flag for completed tasks moved to archive */
  archived: boolean;
  /** Number of times this task was rescheduled/postponed (feeds Smart Insights!) */
  postponeCount: number;
  createdAt: string;
  updatedAt: string;
  /** Local YYYY-MM-DD date when completed */
  completedDate: string | null;
  completedAt: string | null;
}

export type HabitIconName =
  | 'book'
  | 'dumbbell'
  | 'brain'
  | 'droplets'
  | 'moon'
  | 'flame'
  | 'code'
  | 'pen'
  | 'leaf'
  | 'music'
  | 'shield'
  | 'compass'
  | 'sparkles'
  | 'heart'
  | 'sun'
  | 'target';

export type HabitScheduleType = 'daily' | 'specific_days' | 'weekly_target';

export type HabitTrackingMode = 'boolean' | 'quantitative';

export interface Habit {
  id: string;
  name: string;
  description: string;
  icon: HabitIconName;
  /** Controlled luxury purple/plum/lavender/warm-plum accent hex */
  accentColor: string;
  scheduleType: HabitScheduleType;
  /** Persian weekday indices: 0 = شنبه (Sat) .. 6 = جمعه (Fri) */
  scheduledDays: number[];
  /** Target times per week if scheduleType === 'weekly_target' */
  weeklyTarget: number;
  trackingMode: HabitTrackingMode;
  targetValue: number;
  unit: string;
  reminderTime: string | null;
  archived: boolean;
  /** Local YYYY-MM-DD date habit began */
  startDate: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Crucial distinction per product specification:
 * - 'completed': Achieved target for the day
 * - 'partial': Logged partial progress (> 0 and < targetValue)
 * - 'missed': Explicitly marked as NOT done / skipped
 * - Absence of a log record means 'unlogged' (ثبت‌نشده) — NEVER conflated with 'missed'!
 */
export type HabitLogStatus = 'completed' | 'partial' | 'missed';

export interface HabitLog {
  /** Deterministic composite key `${habitId}::${dateKey}` to prevent duplicates */
  id: string;
  habitId: string;
  /** Local YYYY-MM-DD */
  dateKey: string;
  status: HabitLogStatus;
  value: number;
  note?: string;
  updatedAt: string;
}

/**
 * Chrono Core modes. `pomodoro` and `free` are the 1.x pair; `interval` (deep-work
 * blocks) and `countdown` (one fixed stretch) arrived in 1.8.0. Records written by
 * older builds stay readable because the union only grew.
 */
export type FocusModeType = 'pomodoro' | 'free' | 'interval' | 'countdown';

export interface FocusSession {
  id: string;
  mode: FocusModeType;
  /** Actual focused duration in minutes (rounded to 1 decimal or integer >= 1) */
  durationMinutes: number;
  /** Actual focused seconds for precision */
  durationSeconds: number;
  /** Local YYYY-MM-DD */
  dateKey: string;
  taskId: string | null;
  taskTitleSnapshot: string | null;
  completedNaturally: boolean;
  startedAt: string;
  endedAt: string;
}

export interface Goal {
  id: string;
  title: string;
  description: string;
  category: TaskCategory;
  currentValue: number;
  targetValue: number;
  unit: string;
  /** Optional target deadline YYYY-MM-DD */
  deadline: string | null;
  completed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserSettings {
  id: 'default';
  /** Personalisation — the accent the whole product is painted with. */
  accentColor: string;
  backgroundId: BackgroundId;
  motionLevel: MotionLevel;
  /** Home board layout: cinematic (default), compact grid, detailed. */
  homeMode: HomeMode;
  /** Each section derives its own hue + environment from the accent. */
  displayName: string;
  dailyFocusGoalMinutes: number;
  dailyTaskGoal: number;
  pomodoroFocusMin: number;
  pomodoroShortBreakMin: number;
  pomodoroLongBreakMin: number;
  pomodoroCyclesBeforeLong: number;
  /** Chrono Core: interval-mode defaults. */
  intervalBlocks: number;
  intervalBlockMin: number;
  intervalRestMin: number;
  /** Chrono Core: countdown-mode default. */
  countdownMin: number;
  /** Chrono Core: walk into the next segment without a tap. */
  autoAdvance: boolean;
  /** Chrono Core: silence interruptions while a session runs (Android DND). */
  dndInFocus: boolean;
  notificationsEnabled: boolean;
  soundEnabled: boolean;
  hapticsEnabled: boolean;
  /** Tracks whether starter sample dataset is currently active */
  hasSampleData: boolean;
  sampleBannerDismissed: boolean;
  updatedAt: string;
}

export interface ToastMessage {
  id: string;
  title: string;
  description?: string;
  tone: 'default' | 'success' | 'warning' | 'reminder';
}

export interface KairosBackupPayload {
  version: number;
  exportedAt: string;
  app: 'KAIROS';
  tasks: Task[];
  habits: Habit[];
  habitLogs: HabitLog[];
  focusSessions: FocusSession[];
  goals: Goal[];
  settings: UserSettings;
}
