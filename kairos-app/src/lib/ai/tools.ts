import type { Project, Task, TaskCategory, TaskPriority } from '@/types/domain';
import {
  createProject,
  createStage,
  createTask,
  db,
  ensureSettings,
  setTaskStatus,
  toggleStage,
  updateSettings,
  updateTask,
  upsertHabitLog,
} from '@/lib/db';
import { getTodayKey } from '@/lib/date';
import { buildDailyBrief, buildWeeklyReview } from '@/lib/coach';
import { snapshotForManager } from './snapshot';

/**
 * What the cloud manager may do to the app, and nothing else.
 *
 * This file is the whole authority model of the AI feature, in one place:
 *
 *   • Every capability is an explicit tool with a typed schema. There is no
 *     "run this SQL" and no "call any function" — the model can only choose from
 *     this list.
 *   • Every write goes through the same `db.ts` functions the UI uses, so it is
 *     logged in the event ledger and (where applicable) undoable. An AI edit and
 *     a thumb edit are indistinguishable afterwards, which is the point.
 *   • Deletion, wiping data and reading the API key are **not** exposed at all.
 *     A model that cannot ask for a destructive tool cannot be tricked into it by
 *     a clever sentence in the user's own notes.
 *   • Settings are writable only through an allow-list of harmless fields.
 */

export type ToolName =
  | 'get_brief'
  | 'get_weekly_review'
  | 'list_tasks'
  | 'create_task'
  | 'update_task'
  | 'complete_task'
  | 'list_projects'
  | 'create_project'
  | 'add_stage'
  | 'toggle_stage'
  | 'list_habits'
  | 'log_habit'
  | 'update_settings'
  | 'navigate';

/** Writes are the ones that change data; the UI asks before them by default. */
export const WRITE_TOOLS: readonly ToolName[] = [
  'create_task',
  'update_task',
  'complete_task',
  'create_project',
  'add_stage',
  'toggle_stage',
  'log_habit',
  'update_settings',
];

export interface ToolResult {
  ok: boolean;
  /** One Persian line, ready to show in the transcript. */
  summary: string;
  data?: unknown;
}

export interface ToolCall {
  id: string;
  name: ToolName;
  args: Record<string, unknown>;
}

/* ── Schemas ───────────────────────────────────────────────────────────────── */

const str = (description: string) => ({ type: 'string', description });
const num = (description: string) => ({ type: 'number', description });

export const AI_TOOLS = [
  {
    type: 'function' as const,
    function: {
      name: 'get_brief',
      description:
        'وضعیت امروز کاربر: ظرفیت اندازه‌گیری‌شده، بار کار، ریسک‌ها و یک پیشنهاد با دلیل. قبل از هر توصیه‌ای این را صدا بزن.',
      parameters: { type: 'object', properties: {}, required: [] },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'get_weekly_review',
      description: 'مرور هفت روز اخیر در برابر هفت روز قبل، با بردها و ریسک‌ها.',
      parameters: { type: 'object', properties: {}, required: [] },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'list_tasks',
      description: 'فهرست وظایف کاربر با فیلتر. برای پاسخ دادن به «چی دارم» و پیدا کردن شناسهٔ کار.',
      parameters: {
        type: 'object',
        properties: {
          filter: {
            type: 'string',
            enum: ['today', 'overdue', 'open', 'completed'],
            description: 'today = موعد امروز یا گذشته و باز؛ overdue = فقط گذشته.',
          },
          limit: num('حداکثر تعداد، پیش‌فرض ۲۰'),
        },
        required: ['filter'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'create_task',
      description: 'ساخت وظیفهٔ تازه برای کاربر. عنوان کوتاه و روشن بده.',
      parameters: {
        type: 'object',
        properties: {
          title: str('عنوان وظیفه'),
          dueDate: str('تاریخ موعد به شکل YYYY-MM-DD؛ خالی یعنی بدون تاریخ'),
          dueTime: str('ساعت به شکل HH:MM؛ اختیاری'),
          priority: { type: 'string', enum: ['low', 'medium', 'high'] },
          category: {
            type: 'string',
            enum: ['work', 'personal', 'health', 'learning', 'finance', 'creative'],
          },
          estimateMin: num('تخمین دقیقه'),
          projectId: str('شناسهٔ پروژه؛ اختیاری'),
          description: str('توضیح؛ اختیاری'),
        },
        required: ['title'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'update_task',
      description: 'ویرایش وظیفهٔ موجود. فقط فیلدهایی که باید عوض شود را بفرست.',
      parameters: {
        type: 'object',
        properties: {
          id: str('شناسهٔ وظیفه'),
          title: str('عنوان تازه'),
          dueDate: str('تاریخ موعد تازه YYYY-MM-DD'),
          dueTime: str('ساعت تازه HH:MM'),
          priority: { type: 'string', enum: ['low', 'medium', 'high'] },
          estimateMin: num('تخمین دقیقه'),
        },
        required: ['id'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'complete_task',
      description: 'بستن یک وظیفه. اگر شناسه نداری، با عنوان بگرد.',
      parameters: {
        type: 'object',
        properties: {
          id: str('شناسهٔ وظیفه'),
          titleQuery: str('بخشی از عنوان، اگر شناسه معلوم نیست'),
        },
        required: [],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'list_projects',
      description: 'فهرست پروژه‌ها با درصد پیشرفت و ریسک.',
      parameters: { type: 'object', properties: {}, required: [] },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'create_project',
      description: 'ساخت پروژهٔ تازه. مرحله‌ها بعداً با add_stage اضافه می‌شوند.',
      parameters: {
        type: 'object',
        properties: {
          title: str('نام پروژه'),
          description: str('توضیح؛ اختیاری'),
          dueDate: str('موعد YYYY-MM-DD؛ اختیاری'),
          category: {
            type: 'string',
            enum: ['work', 'personal', 'health', 'learning', 'finance', 'creative'],
          },
        },
        required: ['title'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'add_stage',
      description: 'افزودن مرحله به پروژه.',
      parameters: {
        type: 'object',
        properties: { projectId: str('شناسهٔ پروژه'), title: str('نام مرحله') },
        required: ['projectId', 'title'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'toggle_stage',
      description: 'تیک زدن یا برداشتن تیک یک مرحله.',
      parameters: {
        type: 'object',
        properties: { stageId: str('شناسهٔ مرحله') },
        required: ['stageId'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'list_habits',
      description: 'فهرست عادت‌ها با وضعیت امروز و زنجیره.',
      parameters: { type: 'object', properties: {}, required: [] },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'log_habit',
      description: 'ثبت عادت برای یک روز (پیش‌فرض امروز).',
      parameters: {
        type: 'object',
        properties: {
          habitQuery: str('بخشی از نام عادت'),
          dateKey: str('YYYY-MM-DD؛ خالی یعنی امروز'),
          done: { type: 'boolean', description: 'ثبت یا برداشتن ثبت' },
        },
        required: ['habitQuery'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'update_settings',
      description:
        'تغییر تنظیمات مجاز برنامه. فقط همین کلیدها: dailyFocusGoalMinutes، dailyTaskGoal، homeMode، hapticsEnabled، soundEnabled، notificationsEnabled، aiAutoApply.',
      parameters: {
        type: 'object',
        properties: {
          key: {
            type: 'string',
            enum: [
              'dailyFocusGoalMinutes',
              'dailyTaskGoal',
              'homeMode',
              'hapticsEnabled',
              'soundEnabled',
              'notificationsEnabled',
              'aiAutoApply',
            ],
          },
          value: { type: 'string', description: 'مقدار؛ عدد و بولی به شکل متنی' },
        },
        required: ['key', 'value'],
      },
    },
  },
  {
    type: 'function' as const,
    function: {
      name: 'navigate',
      description: 'بردن کاربر به یک بخش برنامه.',
      parameters: {
        type: 'object',
        properties: {
          tab: {
            type: 'string',
            enum: ['tasks', 'projects', 'habits', 'focus', 'analytics'],
          },
        },
        required: ['tab'],
      },
    },
  },
];

/* ── Execution ─────────────────────────────────────────────────────────────── */

const CATEGORIES: TaskCategory[] = ['work', 'personal', 'health', 'learning', 'finance', 'creative'];
const PRIORITIES: TaskPriority[] = ['low', 'medium', 'high'];

const asString = (value: unknown): string => (typeof value === 'string' ? value.trim() : '');
const asNumber = (value: unknown): number | null => {
  const parsed = typeof value === 'number' ? value : Number(String(value ?? '').trim());
  return Number.isFinite(parsed) && parsed > 0 ? Math.round(parsed) : null;
};
const asDate = (value: unknown): string | null => {
  const text = asString(value);
  return /^\d{4}-\d{2}-\d{2}$/.test(text) ? text : null;
};
const asTime = (value: unknown): string | null => {
  const text = asString(value);
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(text) ? text : null;
};

async function findTask(args: Record<string, unknown>): Promise<Task | null> {
  const id = asString(args.id);
  if (id) return (await db.tasks.get(id)) ?? null;
  const query = asString(args.titleQuery).toLowerCase();
  if (!query) return null;
  const open = (await db.tasks.toArray()).filter((task) => !task.archived);
  return (
    open.find((task) => task.title.toLowerCase().includes(query)) ??
    open.find((task) => query.includes(task.title.toLowerCase())) ??
    null
  );
}

const SETTINGS_ALLOW: Record<string, (raw: string) => string | number | boolean> = {
  dailyFocusGoalMinutes: (raw) => Math.min(720, Math.max(15, Number(raw) || 120)),
  dailyTaskGoal: (raw) => Math.min(50, Math.max(1, Number(raw) || 6)),
  homeMode: (raw) => (['cinematic', 'compact', 'detailed'].includes(raw) ? raw : 'cinematic'),
  hapticsEnabled: (raw) => raw === 'true' || raw === '1',
  soundEnabled: (raw) => raw === 'true' || raw === '1',
  notificationsEnabled: (raw) => raw === 'true' || raw === '1',
  aiAutoApply: (raw) => raw === 'true' || raw === '1',
};

export async function executeAiTool(
  name: string,
  args: Record<string, unknown> = {}
): Promise<ToolResult> {
  const todayKey = getTodayKey();

  switch (name as ToolName) {
    case 'get_brief': {
      const snapshot = await snapshotForManager();
      const brief = buildDailyBrief(snapshot);
      return {
        ok: true,
        summary: brief.headline,
        data: {
          verdict: brief.verdict,
          headline: brief.headline,
          lines: brief.lines,
          recommendation: brief.recommendation,
        },
      };
    }

    case 'get_weekly_review': {
      const snapshot = await snapshotForManager();
      const review = buildWeeklyReview(snapshot);
      return {
        ok: true,
        summary: review.headline,
        data: {
          verdict: review.verdict,
          lines: review.lines,
          wins: review.wins,
          risks: review.risks,
          nextActions: review.nextActions,
        },
      };
    }

    case 'list_tasks': {
      const filter = asString(args.filter) || 'open';
      const limit = asNumber(args.limit) ?? 20;
      const tasks = (await db.tasks.toArray()).filter((task) => !task.archived);
      const open = tasks.filter((task) => task.status !== 'completed');
      const selected =
        filter === 'overdue'
          ? open.filter((task) => task.dueDate !== null && task.dueDate < todayKey)
          : filter === 'today'
            ? open.filter((task) => task.dueDate === null || task.dueDate <= todayKey)
            : filter === 'completed'
              ? tasks.filter((task) => task.status === 'completed')
              : open;
      return {
        ok: true,
        summary: `${selected.length} کار (${filter})`,
        data: selected.slice(0, limit).map((task) => ({
          id: task.id,
          title: task.title,
          dueDate: task.dueDate,
          dueTime: task.dueTime,
          status: task.status,
          priority: task.priority,
          estimateMin: task.estimateMin,
          projectId: task.projectId,
          postponeCount: task.postponeCount,
        })),
      };
    }

    case 'create_task': {
      const title = asString(args.title);
      if (!title) return { ok: false, summary: 'عنوان خالی است؛ وظیفه ساخته نشد.' };
      const priority = PRIORITIES.includes(asString(args.priority) as TaskPriority)
        ? (asString(args.priority) as TaskPriority)
        : 'medium';
      const category = CATEGORIES.includes(asString(args.category) as TaskCategory)
        ? (asString(args.category) as TaskCategory)
        : 'work';
      const dueDate = asDate(args.dueDate);
      const created = await createTask({
        title,
        description: asString(args.description),
        dueDate,
        dueTime: asTime(args.dueTime),
        priority,
        quadrant: priority === 'high' ? 'q1' : 'q2',
        category,
        tags: [],
        status: 'todo',
        subtasks: [],
        recurrence: 'none',
        reminderEnabled: false,
        reminderOffsetMin: 0,
        goalId: null,
        projectId: asString(args.projectId) || null,
        estimateMin: asNumber(args.estimateMin),
      });
      return {
        ok: true,
        summary: `وظیفه «${created.title}» ساخته شد${dueDate ? ` برای ${dueDate}` : ''}.`,
        data: { id: created.id, title: created.title },
      };
    }

    case 'update_task': {
      const task = await findTask(args);
      if (!task) return { ok: false, summary: 'وظیفه پیدا نشد؛ چیزی عوض نشد.' };
      const patch: Partial<Task> = {};
      if (asString(args.title)) patch.title = asString(args.title);
      if (asDate(args.dueDate)) patch.dueDate = asDate(args.dueDate);
      if (asTime(args.dueTime)) patch.dueTime = asTime(args.dueTime);
      if (PRIORITIES.includes(asString(args.priority) as TaskPriority)) {
        patch.priority = asString(args.priority) as TaskPriority;
      }
      if (asNumber(args.estimateMin)) patch.estimateMin = asNumber(args.estimateMin);
      if (Object.keys(patch).length === 0) {
        return { ok: false, summary: 'چیزی برای تغییر داده نشد.' };
      }
      await updateTask(task.id, patch);
      return { ok: true, summary: `«${task.title}» به‌روز شد.`, data: { id: task.id, patch } };
    }

    case 'complete_task': {
      const task = await findTask(args);
      if (!task) return { ok: false, summary: 'وظیفه پیدا نشد.' };
      if (task.status === 'completed') {
        return { ok: true, summary: `«${task.title}» از قبل بسته بود.` };
      }
      await setTaskStatus(task.id, 'completed');
      return { ok: true, summary: `«${task.title}» بسته شد.`, data: { id: task.id } };
    }

    case 'list_projects': {
      const { projectRisks } = await import('@/lib/projects');
      const [projects, stages, tasks] = await Promise.all([
        db.projects.toArray(),
        db.projectStages.toArray(),
        db.tasks.toArray(),
      ]);
      const risks = projectRisks({ projects, stages, tasks, todayKey });
      return {
        ok: true,
        summary: `${projects.length} پروژه`,
        data: projects.map((project) => ({
          id: project.id,
          title: project.title,
          status: project.status,
          dueDate: project.dueDate,
          risk: risks.find((risk) => risk.projectId === project.id)?.reason ?? null,
          stages: stages
            .filter((stage) => stage.projectId === project.id)
            .map((stage) => ({ id: stage.id, title: stage.title, done: stage.done })),
        })),
      };
    }

    case 'create_project': {
      const title = asString(args.title);
      if (!title) return { ok: false, summary: 'نام پروژه خالی است.' };
      const project: Project = await createProject({
        title,
        description: asString(args.description),
        category: CATEGORIES.includes(asString(args.category) as TaskCategory)
          ? (asString(args.category) as TaskCategory)
          : 'work',
        goalId: null,
        startDate: todayKey,
        dueDate: asDate(args.dueDate),
      });
      return { ok: true, summary: `پروژهٔ «${project.title}» ساخته شد.`, data: { id: project.id } };
    }

    case 'add_stage': {
      const projectId = asString(args.projectId);
      const title = asString(args.title);
      if (!projectId || !title) return { ok: false, summary: 'پروژه یا نام مرحله ناقص است.' };
      const existing = await db.projectStages.where('projectId').equals(projectId).toArray();
      const stage = await createStage({
        projectId,
        title,
        kind: 'stage',
        order: existing.length,
        weight: 1,
        dueDate: null,
      });
      return { ok: true, summary: `مرحلهٔ «${stage.title}» اضافه شد.`, data: { id: stage.id } };
    }

    case 'toggle_stage': {
      const stageId = asString(args.stageId);
      if (!stageId) return { ok: false, summary: 'شناسهٔ مرحله داده نشد.' };
      const stage = await toggleStage(stageId);
      if (!stage) return { ok: false, summary: 'مرحله پیدا نشد.' };
      return {
        ok: true,
        summary: `مرحلهٔ «${stage.title}» ${stage.done ? 'تیک خورد' : 'باز شد'}.`,
        data: { id: stage.id, done: stage.done },
      };
    }

    case 'list_habits': {
      const snapshot = await snapshotForManager();
      return {
        ok: true,
        summary: `${snapshot.habits.length} عادت`,
        data: snapshot.habits.map((habit) => ({
          id: habit.id,
          name: habit.name,
          doneToday: snapshot.habitLogs.some(
            (log) => log.habitId === habit.id && log.dateKey === todayKey
          ),
        })),
      };
    }

    case 'log_habit': {
      const query = asString(args.habitQuery).toLowerCase();
      const habits = await db.habits.toArray();
      const habit =
        habits.find((item) => item.name.toLowerCase() === query) ??
        habits.find((item) => item.name.toLowerCase().includes(query));
      if (!habit) return { ok: false, summary: 'عادت با این نام پیدا نشد.' };
      const dateKey = asDate(args.dateKey) ?? todayKey;
      const done = args.done === false ? false : true;
      await upsertHabitLog(habit.id, dateKey, done ? 'completed' : 'missed', done ? 1 : 0);
      return {
        ok: true,
        summary: done
          ? `«${habit.name}» برای ${dateKey} ثبت شد.`
          : `ثبت «${habit.name}» برای ${dateKey} برداشته شد.`,
        data: { id: habit.id, dateKey, done },
      };
    }

    case 'update_settings': {
      const key = asString(args.key);
      const raw = String(args.value ?? '').trim();
      const coerce = SETTINGS_ALLOW[key];
      if (!coerce) {
        return { ok: false, summary: 'این تنظیم قابل تغییر از سوی دستیار نیست.' };
      }
      const value = coerce(raw.toLowerCase());
      await updateSettings({ [key]: value } as never);
      return { ok: true, summary: `تنظیم «${key}» به ${value} تغییر کرد.`, data: { key, value } };
    }

    case 'navigate': {
      const tab = asString(args.tab);
      const allowed = ['tasks', 'projects', 'habits', 'focus', 'analytics'];
      if (!allowed.includes(tab)) return { ok: false, summary: 'این بخش وجود ندارد.' };
      return { ok: true, summary: `رفتن به ${tab}`, data: { tab } };
    }

    default:
      return { ok: false, summary: 'این ابزار وجود ندارد.' };
  }
}

/** Everything the model is told about the app, once per conversation. */
export async function managerSystemPrompt(): Promise<string> {
  const settings = await ensureSettings();
  const snapshot = await snapshotForManager();
  const openTasks = snapshot.tasks.filter((task) => !task.archived && task.status !== 'completed');
  const overdue = openTasks.filter(
    (task) => task.dueDate !== null && task.dueDate < snapshot.todayKey
  );

  return [
    'تو «مدیر» داخل برنامهٔ KAIROS هستی: دستیار فارسی‌زبان بهره‌وری و برنامه‌ریزی.',
    'قواعد سخت:',
    '۱) برای هر عددی که می‌گویی، اول ابزار مناسب را صدا بزن. عدد از خودت نساز.',
    '۲) اگر داده‌ای نیست، صریح بگو داده کافی نیست.',
    '۳) کوتاه و مدیرگونه جواب بده: اول تصمیم، بعد یک یا دو خط دلیل. جملهٔ تشویقی خالی نده.',
    '۴) برای کارهای نوشتنی از ابزارها استفاده کن؛ حذف کردن در دسترس تو نیست و نباید پیشنهاد حذف بدهی.',
    '۵) هرگز کلید API یا محتوای تنظیمات حساس را در جواب تکرار نکن.',
    '۶) همهٔ پاسخ‌ها فارسی و با ارقام فارسی باشد.',
    '',
    `امروز: ${snapshot.todayKey} · نام کاربر: ${settings.displayName} · هدف تمرکز روزانه: ${settings.dailyFocusGoalMinutes} دقیقه`,
    `کارهای باز: ${openTasks.length} · عقب‌افتاده: ${overdue.length} · پروژه‌ها: ${snapshot.projects.length} · عادت‌ها: ${snapshot.habits.length}`,
    'برای هر تغییری، نتیجه را یک خط به کاربر بگو که چه شد.',
  ].join('\n');
}
