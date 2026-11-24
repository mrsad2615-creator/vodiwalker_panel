import type {
  FocusSession,
  Goal,
  Habit,
  HabitLog,
  Project,
  ProjectStage,
  Task,
} from '@/types/domain';
import type { DailyPoint } from './homeMetrics';
import { formatClockHHMM, formatJalaaliFull, formatJalaaliShort, getTodayKey } from './date';
import { computeProjectProgress, describeBasis, projectRisks, riskSentence } from './projects';
import { buildDailyBrief, buildWeeklyReview, type ManagerInput } from './coach';

/**
 * The Persian command engine — no model, on purpose.
 *
 * A language model would guess. This does not. It recognises a closed set of
 * Persian intents, answers each one from the user's own rows, and says plainly
 * when it does not understand, listing what it does understand. That is the whole
 * contract: a command surface is only trustworthy if a wrong question gets
 * "نمیدانم" instead of a confident paragraph.
 *
 * Normalisation matters here: Persian is typed with and without ZWNJ, with Arabic
 * ي/ك instead of ی/ک, and with arbitrary spacing. Everything is compared after
 * the same normalisation the search box uses.
 */

export type CommandIntent =
  | 'today.tasks'
  | 'overdue.tasks'
  | 'focus.week'
  | 'plan.today'
  | 'projects.risk'
  | 'goals.status'
  | 'habits.status'
  | 'project.status'
  | 'brief.today'
  | 'review.week'
  | 'why.suggestion'
  | 'help'
  | 'smalltalk.greeting'
  | 'smalltalk.date'
  | 'smalltalk.time'
  | 'smalltalk.thanks'
  | 'smalltalk.identity'
  | 'unknown';

export interface CommandAnswer {
  intent: CommandIntent;
  /** Short headline. */
  title: string;
  /** Body lines, already Persian and already carrying their numbers. */
  lines: string[];
  /** Where the person can go next. */
  action?: { label: string; tab: 'tasks' | 'projects' | 'focus' | 'analytics' | 'habits' };
  /** True when the request was not understood at all. */
  unknown?: boolean;
}

export interface CommandContext {
  tasks: Task[];
  habits: Habit[];
  habitLogs: HabitLog[];
  goals: Goal[];
  projects: Project[];
  stages: ProjectStage[];
  series: DailyPoint[];
  todayKey: string;
  /** Optional: the planner and the coach need real sessions to measure capacity. */
  sessions?: FocusSession[];
  goalMinutes?: number;
  /** Optional explanation of the last suggestion shown, for "چرا؟". */
  lastSuggestionReason?: string | null;
}

/**
 * Finds the row a sentence is talking about, by name.
 *
 * "وضعیت پروژه کتاب" has to reach the project called «کتاب چاپ اول». The match
 * is on normalised text and takes the longest name that the sentence contains,
 * so a short project called «کار» cannot steal a question about «کارهای خانه».
 */
function matchByName<T extends { id: string }>(
  query: string,
  items: T[],
  nameOf: (item: T) => string
): T | null {
  const text = ` ${normalizeCommand(query)} `;
  let best: { item: T; length: number } | null = null;
  for (const item of items) {
    const name = normalizeCommand(nameOf(item));
    if (name.length < 2) continue;
    if (!text.includes(name)) continue;
    if (!best || name.length > best.length) best = { item, length: name.length };
  }
  return best?.item ?? null;
}

const FA_DIGITS = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];

/** Numbers in answers are Persian: the rest of the product renders them that way. */
export function fa(value: number | string): string {
  return String(value).replace(/[0-9]/g, (digit) => FA_DIGITS[Number(digit)]);
}

const ZWNJ = /\u200c/g;
const DIACRITICS = /[\u064b-\u0652\u0670\u0640]/g;

/** Same normalisation as search: ي→ی, ك→ک, no ZWNJ, no diacritics, single spaces. */
export function normalizeCommand(input: string): string {
  return input
    .replace(ZWNJ, ' ')
    .replace(DIACRITICS, '')
    .replace(/ي/g, 'ی')
    .replace(/ك/g, 'ک')
    .replace(/[؟?!.،,]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

const RULES: { intent: CommandIntent; patterns: RegExp[] }[] = [
  // Order is significance, not size: the first rule that matches wins, so a
  // sentence about a project is a project question even if it also says overdue.
  { intent: 'brief.today', patterns: [/بریف/, /چی کار کنم/, /چه کار کنم/, /از کجا شروع/, /الان چه/, /اولویت امروز/, /brief/] },
  { intent: 'review.week', patterns: [/مرور هفته/, /هفته چطور/, /هفته گذشته چطور/, /جمع بندی هفته/, /review/] },
  { intent: 'why.suggestion', patterns: [/چرا/, /دلیل/, /why/] },
  {
    intent: 'project.status',
    // A *named* project is a status question, not a risk listing.
    patterns: [/وضعیت پروژه/, /پروژه [^ ]+ چطور/, /پیشرفت پروژه/],
  },
  { intent: 'projects.risk', patterns: [/پروژه/, /project/] },
  { intent: 'overdue.tasks', patterns: [/عقب افتاده/, /دیرکرد/, /گذشته/, /overdue/] },
  { intent: 'plan.today', patterns: [/برنامه/, /plan/, /بچین/, /چیدمان/] },
  { intent: 'focus.week', patterns: [/تمرکز/, /focus/, /پومودورو/] },
  { intent: 'habits.status', patterns: [/عادت/, /habit/, /زنجیره/] },
  { intent: 'goals.status', patterns: [/هدف/, /goal/, /چشم انداز/] },
  {
    intent: 'today.tasks',
    // "امروز" alone is not a task request ("هوا امروز چطور است"), so a task word
    // has to be present too.
    patterns: [/وظیفه/, /وظایف/, /کارها/, /کارهای/, /task/],
  },
  { intent: 'help', patterns: [/کمک/, /help/, /راهنما/, /چه کاری می/, /چه کار می/] },
];

export function detectIntent(raw: string): CommandIntent {
  const text = normalizeCommand(raw);
  if (!text) return 'unknown';
  for (const rule of RULES) {
    if (rule.patterns.some((pattern) => pattern.test(text))) return rule.intent;
  }
  return 'unknown';
}

const HELP_LINES = [
  '«بریف امروز» — مدیر چه می‌گوید',
  '«این هفته چطور بودم؟» — مرور هفته',
  '«وضعیت پروژه کتاب» — با نام پروژه',
  '«وظایف امروز من چیست؟»',
  '«کدام کارها عقب افتاده؟»',
  '«کدام پروژه عقب افتاده؟»',
  '«برای امروز برنامه بساز.»',
  '«گزارش تمرکز این هفته را نشان بده.»',
  '«وضعیت عادت‌ها چطور است؟»',
  '«هدف‌هایم چه شد؟»',
  '«چرا این پیشنهاد را دادی؟»',
];

export function helpAnswer(): CommandAnswer {
  return {
    intent: 'help',
    title: 'این‌ها را می‌فهمم',
    lines: ['با جملهٔ خودت بپرس یا یکی از این‌ها را بزن:', ...HELP_LINES],
  };
}

export function unknownAnswer(): CommandAnswer {
  return {
    intent: 'help',
    title: 'این فرمان را نمی‌شناسم',
    lines: [
      'به‌جای حدس زدن، صریح می‌گویم: این جمله در فهرست فرمان‌های من نیست.',
      'این‌ها را می‌فهمم:',
      ...HELP_LINES,
    ],
    unknown: true,
  };
}

/**
 * Small talk, handled honestly.
 *
 * A rule engine that answers «سلام» with «این فرمان را نمیشناسم» feels broken even
 * though it is technically correct, so greetings, the date and the clock get real
 * answers. They are only tried *after* every real intent fails, so «سلام، وظایف
 * امروز چیه؟» is still a task question.
 */
export function detectSmalltalk(raw: string): CommandIntent | null {
  const text = normalizeCommand(raw);
  if (!text) return null;

  const has = (patterns: RegExp[]) => patterns.some((pattern) => pattern.test(text));

  if (has([/ساعت چند/, /چند ساعت/, /الساعه/, /^ساعت/, /what time/])) return 'smalltalk.time';
  if (
    has([
      /چند شنبه/,
      /چه روزی/,
      /امروز چه روز/,
      /تاریخ امروز/,
      /امروز چندم/,
      /چندمه/,
      /تاریخ چنده/,
      /what day/,
      /today.*date/,
    ])
  ) {
    return 'smalltalk.date';
  }
  if (has([/ممنون/, /مرسی/, /دستت درد نکنه/, /thanks/, /thank you/, /سپاس/])) {
    return 'smalltalk.thanks';
  }
  if (
    has([
      /تو کی هستی/,
      /کی هستی/,
      /اسمت چیه/,
      /چه کاری (می‌)?توانی/,
      /چیکار میتونی/,
      /چه کارهایی میتونی/,
      /who are you/,
    ])
  ) {
    return 'smalltalk.identity';
  }
  // Greetings must be short and must not contain a real request.
  if (
    text.length <= 24 &&
    has([/^سلام/, /^درود/, /^سلام علیکم/, /^صبح بخیر/, /^شب بخیر/, /^عصر بخیر/, /^hi\b/, /^hello/])
  ) {
    return 'smalltalk.greeting';
  }
  return null;
}

function sum(values: number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

/** Runs one command. Pure: everything it needs arrives in the context. */
export function runCommand(raw: string, context: CommandContext): CommandAnswer {
  const detected = detectIntent(raw);
  const intent = detected === 'unknown' ? detectSmalltalk(raw) ?? detected : detected;
  const { tasks, todayKey } = context;

  switch (intent) {
    case 'today.tasks': {
      const open = tasks.filter(
        (task) =>
          !task.archived &&
          task.status !== 'completed' &&
          (task.dueDate === null || task.dueDate <= todayKey)
      );
      const overdue = open.filter((task) => task.dueDate !== null && task.dueDate < todayKey);
      if (open.length === 0) {
        return {
          intent,
          title: 'امروز کار بازی نداری',
          lines: ['همهٔ کارهای امروز بسته شده‌اند. اگر کاری هست، سریع اضافه‌اش کن.'],
          action: { label: 'دیدن وظایف', tab: 'tasks' },
        };
      }
      const sorted = [...open].sort(
        (a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999')
      );
      return {
        intent,
        title: `${fa(open.length)} کار باز برای امروز`,
        lines: [
          ...sorted.slice(0, 5).map((task) => {
            const when = task.dueTime ? ` · ${task.dueTime}` : '';
            const late = task.dueDate && task.dueDate < todayKey ? ' · عقب‌افتاده' : '';
            return `• ${task.title}${when}${late}`;
          }),
          overdue.length > 0
            ? `${fa(overdue.length)} موردش موعدش گذشته است.`
            : 'همه‌شان امروز یا بدون تاریخ‌اند.',
        ],
        action: { label: 'دیدن وظایف', tab: 'tasks' },
      };
    }

    case 'overdue.tasks': {
      const overdue = tasks.filter(
        (task) =>
          !task.archived &&
          task.status !== 'completed' &&
          task.dueDate !== null &&
          task.dueDate < todayKey
      );
      if (overdue.length === 0) {
        return {
          intent,
          title: 'چیزی عقب نیفتاده',
          lines: ['هیچ کار بازی از موعدش نگذشته است.'],
          action: { label: 'دیدن وظایف', tab: 'tasks' },
        };
      }
      const worst = [...overdue].sort((a, b) => b.postponeCount - a.postponeCount)[0];
      return {
        intent,
        title: `${fa(overdue.length)} کار عقب‌افتاده`,
        lines: [
          ...overdue.slice(0, 5).map((task) => `• ${task.title} — موعد ${formatJalaaliShort(task.dueDate!)}`),
          worst.postponeCount > 0
            ? `بیشترین جابه‌جایی: «${worst.title}» با ${fa(worst.postponeCount)} بار.`
            : 'هیچ‌کدام بارها جابه‌جا نشده‌اند؛ فقط از تاریخ گذشته‌اند.',
        ],
        action: { label: 'دیدن وظایف', tab: 'tasks' },
      };
    }

    case 'brief.today': {
      const brief = buildDailyBrief(managerInput(context));
      return {
        intent,
        title: brief.headline,
        lines: [
          ...brief.lines,
          ...(brief.recommendation
            ? [
                `پیشنهاد من: ${brief.recommendation.text}`,
                `دلیل: ${brief.recommendation.reason} (پیشنهاد بر پایهٔ قواعد محلی است.)`,
              ]
            : []),
        ],
        action: brief.recommendation
          ? { label: 'رفتن به کارها', tab: brief.recommendation.tab }
          : { label: 'دیدن وظایف', tab: 'tasks' },
      };
    }

    case 'review.week': {
      const review = buildWeeklyReview(managerInput(context));
      return {
        intent,
        title: review.headline,
        lines: [
          ...review.lines,
          ...(review.risks.length > 0 ? [`ریسک: ${review.risks[0]}`] : []),
          ...review.nextActions.map((action) => `قدم بعد: ${action}`),
        ],
        action: { label: 'تحلیل کامل', tab: 'analytics' },
      };
    }

    case 'project.status': {
      const named = matchByName(raw, context.projects, (project) => project.title);
      if (named) {
        const progress = computeProjectProgress(
          context.stages.filter((stage) => stage.projectId === named.id),
          tasks.filter((task) => task.projectId === named.id)
        );
        const risk = projectRisks({
          projects: [named],
          stages: context.stages,
          tasks,
          todayKey,
        })[0];
        return {
          intent,
          title: `«${named.title}» — ${fa(progress.percent)}٪`,
          lines: [
            describeBasis(progress),
            `${fa(progress.stagesDone)} از ${fa(progress.stagesTotal)} مرحله و ${fa(
              progress.tasksDone
            )} از ${fa(progress.tasksTotal)} کار بسته شده است.`,
            ...(risk ? [riskSentence(risk)] : ['ریسکی برای این پروژه نمی‌بینم.']),
          ],
          action: { label: 'دیدن پروژه‌ها', tab: 'projects' },
        };
      }
      const risks = projectRisks({ projects: context.projects, stages: context.stages, tasks, todayKey });
      if (risks.length === 0) {
        return {
          intent,
          title: 'هیچ پروژه‌ای در خطر نیست',
          lines: [
            context.projects.length === 0
              ? 'هنوز پروژه‌ای نساخته‌ای؛ از «بیشتر» → «پروژه‌ها» شروع کن.'
              : 'همهٔ پروژه‌ها یا روی برنامه‌اند یا داده‌ای برای قضاوت ندارند.',
          ],
          action: { label: 'دیدن پروژه‌ها', tab: 'projects' },
        };
      }
      return {
        intent,
        title: `${fa(risks.length)} پروژه در خطر`,
        lines: risks.slice(0, 4).map((risk) => `• ${risk.title}: ${riskSentence(risk)}`),
        action: { label: 'دیدن پروژه‌ها', tab: 'projects' },
      };
    }

    case 'projects.risk': {
      const risks = projectRisks({
        projects: context.projects,
        stages: context.stages,
        tasks: context.tasks,
        todayKey,
      });
      if (context.projects.length === 0) {
        return {
          intent,
          title: 'هنوز پروژه‌ای نساخته‌ای',
          lines: ['یک پروژه بساز تا بتوانم تأخیرش را تشخیص بدهم.'],
          action: { label: 'دیدن پروژه‌ها', tab: 'projects' },
        };
      }
      if (risks.length === 0) {
        return {
          intent,
          title: 'هیچ پروژه‌ای در معرض تأخیر نیست',
          lines: [`${fa(context.projects.filter((p) => p.status === 'active').length)} پروژهٔ فعال، همه روی ریل.`],
          action: { label: 'دیدن پروژه‌ها', tab: 'projects' },
        };
      }
      return {
        intent,
        title: `${fa(risks.length)} پروژه در معرض تأخیر`,
        lines: risks.slice(0, 4).map((risk) => `• ${risk.title} — ${riskSentence(risk)}`),
        action: { label: 'دیدن پروژه‌ها', tab: 'projects' },
      };
    }

    case 'focus.week': {
      const week = context.series.slice(-7);
      const minutes = sum(week.map((day) => day.focusMinutes));
      const activeDays = week.filter((day) => day.focusMinutes > 0).length;
      const tasksDone = sum(week.map((day) => day.tasksDone));
      if (minutes === 0) {
        return {
          intent,
          title: 'این هفته تمرکزی ثبت نشده',
          lines: ['هیچ جلسه‌ای در هفت روز گذشته ثبت نشده است.'],
          action: { label: 'شروع تمرکز', tab: 'focus' },
        };
      }
      const best = week.reduce((top, day) => (day.focusMinutes > top.focusMinutes ? day : top), week[0]);
      return {
        intent,
        title: `${fa(minutes)} دقیقه تمرکز در هفت روز`,
        lines: [
          `${fa(activeDays)} روز از هفت روز تمرکز داشتند و ${fa(tasksDone)} کار بسته شد.`,
          `بهترین روز: ${formatJalaaliShort(best.dateKey)} با ${fa(best.focusMinutes)} دقیقه.`,
        ],
        action: { label: 'دیدن تحلیل', tab: 'analytics' },
      };
    }

    case 'plan.today':
      return {
        intent,
        title: 'برنامهٔ امروز',
        lines: [
          'برنامهٔ پیشنهادی را در کارت «برنامهٔ امروز» روی خانه ببین.',
          'ظرفیت روز از میانگین روزهای واقعی تمرکزت حساب می‌شود، نه از ۲۴ ساعت.',
        ],
        action: { label: 'دیدن خانه', tab: 'tasks' },
      };

    case 'habits.status': {
      const active = context.habits.filter((habit) => !habit.archived);
      if (active.length === 0) {
        return {
          intent,
          title: 'عادتی تعریف نکرده‌ای',
          lines: ['اولین عادت را بساز تا وضعیتش را بگویم.'],
          action: { label: 'دیدن عادت‌ها', tab: 'habits' },
        };
      }
      const doneToday = new Set(
        context.habitLogs
          .filter((log) => log.dateKey === todayKey && log.status === 'completed')
          .map((log) => log.habitId)
      );
      const done = active.filter((habit) => doneToday.has(habit.id)).length;
      return {
        intent,
        title: `${fa(done)} از ${fa(active.length)} عادت امروز ثبت شده`,
        lines: [
          ...active.slice(0, 5).map((habit) => `• ${habit.name} — ${doneToday.has(habit.id) ? 'ثبت شد' : 'باز'}`),
          'برای بستن زنجیره، روی کارت عادت در خانه بزن.',
        ],
        action: { label: 'دیدن عادت‌ها', tab: 'habits' },
      };
    }

    case 'goals.status': {
      if (context.goals.length === 0) {
        return {
          intent,
          title: 'هدفی ثبت نشده',
          lines: ['یک هدف بساز تا پیشرفتش را از کارهای پیوندشده حساب کنم.'],
          action: { label: 'دیدن خانه', tab: 'tasks' },
        };
      }
      const open = context.goals.filter((goal) => !goal.completed);
      const lines = open.slice(0, 4).map((goal) => {
        const linked = context.tasks.filter(
          (task) => task.goalId === goal.id && !task.archived
        );
        if (linked.length > 0) {
          const done = linked.filter((task) => task.status === 'completed').length;
          return `• ${goal.title} — ${fa(done)} از ${fa(linked.length)} کار`;
        }
        const percent =
          goal.targetValue > 0
            ? Math.min(100, Math.round((goal.currentValue / goal.targetValue) * 100))
            : 0;
        return `• ${goal.title} — ${fa(percent)}٪ (ثبت دستی)`;
      });
      return {
        intent,
        title: open.length === 0 ? 'همهٔ هدف‌ها بسته شده‌اند' : `${fa(open.length)} هدف باز`,
        lines: lines.length > 0 ? lines : ['هیچ هدف بازی نیست.'],
        action: { label: 'دیدن خانه', tab: 'tasks' },
      };
    }

    case 'why.suggestion': {
      if (!context.lastSuggestionReason) {
        // Nothing on screen to explain: answer from the manager itself rather
        // than refusing, because "why?" always has a real answer here.
        const brief = buildDailyBrief(managerInput(context));
        if (brief.recommendation) {
          return {
            intent,
            title: `دلیل این پیشنهاد: ${brief.recommendation.text}`,
            lines: [
              brief.recommendation.reason,
              'این پیشنهاد بر پایهٔ قواعد محلی است، نه پیش‌بینی قطعی.',
            ],
            action: { label: 'رفتن به کارها', tab: brief.recommendation.tab },
          };
        }
      }
      if (!context.lastSuggestionReason) {
        return {
          intent,
          title: 'پیشنهاد تازه‌ای در کار نیست',
          lines: [
            'هر پیشنهادی که نمایش می‌دهم، دلیلش را کنار خودش می‌نویسم.',
            'الان پیشنهاد بازی وجود ندارد که بخواهم توضیحش بدهم.',
          ],
        };
      }
      return {
        intent,
        title: 'دلیل پیشنهاد',
        lines: [context.lastSuggestionReason],
      };
    }

    case 'smalltalk.greeting': {
      const today = formatJalaaliFull(context.todayKey);
      return {
        intent,
        title: 'سلام! در خدمتم.',
        lines: [
          `امروز ${today.weekday} ${fa(today.day)} ${today.month} ${fa(today.year)} است.`,
          'می‌توانی بپرسی «بریف امروز» یا از فرمان‌های پیشنهادی استفاده کنی.',
        ],
        action: { label: 'دیدن وظایف', tab: 'tasks' },
      };
    }

    case 'smalltalk.date': {
      const today = formatJalaaliFull(context.todayKey ?? getTodayKey());
      return {
        intent,
        title: `امروز ${today.weekday} است`,
        lines: [
          `${today.weekday} ${fa(today.day)} ${today.month} ${fa(today.year)}`,
          `به تاریخ میلادی: ${fa(new Date().toISOString().slice(0, 10))}`,
        ],
      };
    }

    case 'smalltalk.time': {
      const clock = formatClockHHMM();
      return {
        intent,
        title: `الان ساعت ${fa(clock)} است`,
        lines: ['اگر کار عمیقی داری، همین حالا یک جلسهٔ تمرکز شروع کن؛ برنامه‌ات را هم می‌توانم بچینم.'],
        action: { label: 'شروع تمرکز', tab: 'focus' },
      };
    }

    case 'smalltalk.thanks': {
      return {
        intent,
        title: 'قابلی نداشت.',
        lines: ['اگر کاری مانده، بگو تا همین‌جا انجامش بدهم.'],
      };
    }

    case 'smalltalk.identity': {
      return {
        intent,
        title: 'من مدیر کایروس هستم',
        lines: [
          'آفلاین: بریف روز، مرور هفته، پیدا کردن کار و پروژه با نام، برنامه‌ریزی و پاسخ از دادهٔ خودت — بدون اینترنت.',
          'فقط فرمان‌های مشخص را می‌شناسم و گفت‌وگوی آزاد یا پاسخ به هر پرسش را پشتیبانی نمی‌کنم.',
          'حذف کردن هیچ‌وقت در دست من نیست.',
        ],
      };
    }

    case 'unknown':
    default:
      return unknownAnswer();

    case 'help':
      return helpAnswer();
  }
}

/** Suggestions shown as chips above the input. */
export const QUICK_COMMANDS = [
  'بریف امروز',
  'این هفته چطور بودم؟',
  'وظایف امروز من چیست؟',
  'برای امروز لیست وظایف بساز',
  'کدام کارها عقب افتاده؟',
  'کدام پروژه عقب افتاده؟',
  'گزارش تمرکز این هفته',
  'وضعیت عادت‌ها',
  'برای امروز برنامه بساز',
] as const;

/** Adapts the command context to what the coach and planner need. */
function managerInput(context: CommandContext): ManagerInput {
  return {
    tasks: context.tasks,
    habits: context.habits,
    habitLogs: context.habitLogs,
    sessions: context.sessions ?? [],
    goals: context.goals,
    projects: context.projects,
    stages: context.stages,
    todayKey: context.todayKey,
    goalMinutes: context.goalMinutes,
  };
}
