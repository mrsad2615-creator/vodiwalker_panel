import type { FocusSession, Goal, Habit, HabitLog, Task } from '@/types/domain';
import { addDays, getPersianWeekdayIndex, getTodayKey } from './date';
import { db, DEFAULT_SETTINGS, makeHabitLogId } from './db';

/**
 * Deterministic pseudo-random helper so starter history looks natural, realistic,
 * and consistent across tests while remaining 100% genuine stored IndexedDB data.
 */
function seededUnit(seed: number): number {
  const x = Math.sin(seed * 9301 + 49297) * 233280;
  return x - Math.floor(x);
}

export async function seedStarterWorkspaceIfEmpty(): Promise<boolean> {
  const [taskCount, habitCount, sessionCount, settings] = await Promise.all([
    db.tasks.count(),
    db.habits.count(),
    db.focusSessions.count(),
    db.settings.get('default'),
  ]);

  // If user already initialized/cleared settings or has data, never overwrite
  if (settings || taskCount > 0 || habitCount > 0 || sessionCount > 0) {
    return false;
  }

  await populateStarterWorkspace();
  return true;
}

export async function populateStarterWorkspace(todayKey: string = getTodayKey()): Promise<void> {
  const nowIso = new Date().toISOString();
  const startHistoryDate = addDays(todayKey, -95);

  const habits: Habit[] = [
    {
      id: 'hab_deep_work',
      name: 'کار عمیق صبحگاهی',
      description: 'حداقل 60 دقیقه تمرکز بدون اعلان روی مهم‌ترین پروژه روز',
      icon: 'brain',
      accentColor: '#6D4AFF',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'quantitative',
      targetValue: 60,
      unit: 'دقیقه',
      reminderTime: '08:30',
      archived: false,
      startDate: startHistoryDate,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
    {
      id: 'hab_reading',
      name: 'مطالعه شبانه',
      description: 'مطالعه کتاب تخصصی یا غیرداستانی پیش از خواب',
      icon: 'book',
      accentColor: '#B9A8FF',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'quantitative',
      targetValue: 25,
      unit: 'صفحه',
      reminderTime: '22:00',
      archived: false,
      startDate: startHistoryDate,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
    {
      id: 'hab_training',
      name: 'تمرین قدرتی و هوازی',
      description: 'جلسه تمرینی هدفمند برای حفظ انرژی و سلامت جسم',
      icon: 'dumbbell',
      accentColor: '#7C5CFF',
      scheduleType: 'specific_days',
      scheduledDays: [0, 2, 4, 5], // شنبه، دوشنبه، چهارشنبه، پنجشنبه
      weeklyTarget: 4,
      trackingMode: 'boolean',
      targetValue: 1,
      unit: 'جلسه',
      reminderTime: '18:00',
      archived: false,
      startDate: startHistoryDate,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
    {
      id: 'hab_hydration',
      name: 'آب کافی روزانه',
      description: 'نوشیدن 8 لیوان آب در طول روز برای تمرکز پایدار',
      icon: 'droplets',
      accentColor: '#5F4BB3',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'quantitative',
      targetValue: 8,
      unit: 'لیوان',
      reminderTime: null,
      archived: false,
      startDate: startHistoryDate,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
    {
      id: 'hab_review',
      name: 'مرور و برنامه‌ریزی شبانه',
      description: 'انتخاب 3 اولویت کلیدی فردا در 10 دقیقه پایانی شب',
      icon: 'moon',
      accentColor: '#BDAFF5',
      scheduleType: 'daily',
      scheduledDays: [0, 1, 2, 3, 4, 5, 6],
      weeklyTarget: 7,
      trackingMode: 'boolean',
      targetValue: 1,
      unit: 'بار',
      reminderTime: '21:30',
      archived: false,
      startDate: startHistoryDate,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
  ];

  // Generate 95 days of genuine historical logs (including completed, partial, missed, AND intentional unlogged gaps!)
  const habitLogs: HabitLog[] = [];
  for (let offset = -95; offset <= 0; offset++) {
    const dk = addDays(todayKey, offset);
    const weekday = getPersianWeekdayIndex(dk);

    // Simulate realistic trend: slightly higher adherence in the recent 30 days vs previous month
    const recentBoost = offset > -30 ? 0.14 : 0;
    // Slight weekend dip on Fridays
    const dayMod = weekday === 6 ? -0.12 : weekday === 1 || weekday === 2 ? 0.08 : 0;

    habits.forEach((habit, hIdx) => {
      if (habit.scheduleType === 'specific_days' && !habit.scheduledDays.includes(weekday)) {
        return;
      }

      // On today, leave 2 habits pending so user can interactively check them off immediately!
      if (offset === 0) {
        if (habit.id === 'hab_deep_work') {
          habitLogs.push({
            id: makeHabitLogId(habit.id, dk),
            habitId: habit.id,
            dateKey: dk,
            status: 'completed',
            value: 60,
            updatedAt: nowIso,
          });
        } else if (habit.id === 'hab_hydration') {
          habitLogs.push({
            id: makeHabitLogId(habit.id, dk),
            habitId: habit.id,
            dateKey: dk,
            status: 'partial',
            value: 5,
            updatedAt: nowIso,
          });
        } else if (habit.id === 'hab_training') {
          habitLogs.push({
            id: makeHabitLogId(habit.id, dk),
            habitId: habit.id,
            dateKey: dk,
            status: 'completed',
            value: 1,
            updatedAt: nowIso,
          });
        }
        return;
      }

      // Build a clean recent streak for deep work & reading over last 8 days
      if (offset >= -8 && (habit.id === 'hab_deep_work' || habit.id === 'hab_reading')) {
        habitLogs.push({
          id: makeHabitLogId(habit.id, dk),
          habitId: habit.id,
          dateKey: dk,
          status: 'completed',
          value: habit.targetValue,
          updatedAt: nowIso,
        });
        return;
      }

      const roll = seededUnit((offset + 120) * 17 + hIdx * 53);

      // ~10% of historical days genuinely unlogged (no record written!) to showcase unlogged vs missed separation
      if (roll < 0.09) {
        return;
      }

      const successThreshold = 0.62 + recentBoost + dayMod + (hIdx === 0 ? 0.1 : hIdx === 3 ? -0.05 : 0);

      if (roll < successThreshold) {
        habitLogs.push({
          id: makeHabitLogId(habit.id, dk),
          habitId: habit.id,
          dateKey: dk,
          status: 'completed',
          value: habit.targetValue,
          updatedAt: nowIso,
        });
      } else if (habit.trackingMode === 'quantitative' && roll < successThreshold + 0.16) {
        habitLogs.push({
          id: makeHabitLogId(habit.id, dk),
          habitId: habit.id,
          dateKey: dk,
          status: 'partial',
          value: Math.max(1, Math.round(habit.targetValue * 0.55)),
          updatedAt: nowIso,
        });
      } else {
        habitLogs.push({
          id: makeHabitLogId(habit.id, dk),
          habitId: habit.id,
          dateKey: dk,
          status: 'missed',
          value: 0,
          updatedAt: nowIso,
        });
      }
    });
  }

  // Realistic tasks across today, overdue, upcoming, in_progress, completed this month & previous month
  const tasks: Task[] = [
    {
      id: 'tsk_today_1',
      title: 'بازبینی معماری سیستم طراحی نسخه جدید',
      description: 'بررسی کنتراست رنگ‌ها، تایپوگرافی اعداد انگلیسی و مقیاس کارت‌ها در نمایشگرهای موبایل',
      dueDate: todayKey,
      dueTime: '10:30',
      priority: 'high',
      quadrant: 'q1',
      category: 'work',
      tags: ['طراحی', 'محصول'],
      status: 'completed',
      subtasks: [
        { id: 'sub_1', title: 'بررسی پالت مشکی و بنفش', completed: true },
        { id: 'sub_2', title: 'تایید اعداد Tabular در داشبورد', completed: true },
      ],
      recurrence: 'none',
      reminderEnabled: true,
      reminderOffsetMin: 15,
      archived: false,
      postponeCount: 0,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: todayKey,
      completedAt: nowIso,
    },
    {
      id: 'tsk_today_2',
      title: 'تکمیل گزارش استراتژی فصل پاییز',
      description: 'جمع‌بندی شاخص‌های کلیدی عملکرد و ارسال نسخه نهایی برای تیم',
      dueDate: todayKey,
      dueTime: '14:00',
      priority: 'high',
      quadrant: 'q1',
      category: 'work',
      tags: ['استراتژی', 'فوری'],
      status: 'in_progress',
      subtasks: [
        { id: 'sub_3', title: 'تحلیل داده‌های ماه گذشته', completed: true },
        { id: 'sub_4', title: 'تدوین اهداف ماه آینده', completed: false },
        { id: 'sub_5', title: 'بازخوانی نهایی متن', completed: false },
      ],
      recurrence: 'none',
      reminderEnabled: true,
      reminderOffsetMin: 10,
      archived: false,
      postponeCount: 1,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: null,
      completedAt: null,
    },
    {
      id: 'tsk_today_3',
      title: 'طراحی ساختار یادداشت‌های مطالعه ماهانه',
      description: 'خلاصه‌برداری از فصل چهارم کتاب عادت‌های اتمی و پیاده‌سازی در برنامه روزانه',
      dueDate: todayKey,
      dueTime: '19:00',
      priority: 'medium',
      quadrant: 'q2',
      category: 'learning',
      tags: ['مطالعه', 'توسعه فردی'],
      status: 'todo',
      subtasks: [],
      recurrence: 'weekly',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 0,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: null,
      completedAt: null,
    },
    {
      id: 'tsk_today_4',
      title: 'بررسی بودجه ماهانه و سرمایه‌گذاری منظم',
      description: 'ثبت هزینه‌های ثابت و تخصیص درصد پس‌انداز ماهانه',
      dueDate: todayKey,
      dueTime: '20:30',
      priority: 'medium',
      quadrant: 'q2',
      category: 'finance',
      tags: ['مالی'],
      status: 'completed',
      subtasks: [],
      recurrence: 'monthly',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 0,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: todayKey,
      completedAt: nowIso,
    },
    {
      id: 'tsk_today_5',
      title: 'پاسخ به ایمیل‌ها و هماهنگی جلسه فردا',
      description: 'ارسال دستور جلسه کوتاه برای هماهنگی صبح فردا',
      dueDate: todayKey,
      dueTime: '16:30',
      priority: 'low',
      quadrant: 'q3',
      category: 'work',
      tags: ['ارتباطات'],
      status: 'todo',
      subtasks: [],
      recurrence: 'daily',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 2,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: null,
      completedAt: null,
    },
    {
      id: 'tsk_overdue_1',
      title: 'مرتب‌سازی آرشیو فایل‌های ابری و بکاپ‌گیری',
      description: 'دسته‌بندی پوشه پروژه‌ها و پاک‌سازی فایل‌های تکراری',
      dueDate: addDays(todayKey, -2),
      dueTime: '17:00',
      priority: 'low',
      quadrant: 'q4',
      category: 'personal',
      tags: ['نظم دیجیتال'],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 3,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: null,
      completedAt: null,
    },
    {
      id: 'tsk_future_1',
      title: 'جلسه نقد طراحی و بهینه‌سازی تجربه کاربری',
      description: 'ارائه نسخه اولیه صفحه تحلیل عملکرد به تیم محصول',
      dueDate: addDays(todayKey, 1),
      dueTime: '11:00',
      priority: 'high',
      quadrant: 'q2',
      category: 'creative',
      tags: ['UX', 'جلسه'],
      status: 'todo',
      subtasks: [
        { id: 'sub_6', title: 'آماده‌سازی اسلایدها', completed: false },
      ],
      recurrence: 'none',
      reminderEnabled: true,
      reminderOffsetMin: 15,
      archived: false,
      postponeCount: 0,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: null,
      completedAt: null,
    },
    {
      id: 'tsk_future_2',
      title: 'چکاپ ورزشی و تنظیم برنامه دویدن ماه آینده',
      description: 'ارزیابی رکورد 5 کیلومتر و برنامه‌ریزی جلسات استقامتی',
      dueDate: addDays(todayKey, 3),
      dueTime: '07:30',
      priority: 'medium',
      quadrant: 'q2',
      category: 'health',
      tags: ['ورزش'],
      status: 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: false,
      postponeCount: 0,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: null,
      completedAt: null,
    },
  ];

  // Add historical completed/overdue tasks across the last 70 days for genuine Monthly & Annual Reviews
  const histTitles = [
    { title: 'بازبینی اسپرینت و اولویت‌بندی بک‌لاگ', cat: 'work' as const, q: 'q1' as const },
    { title: 'تمرین کدنویسی الگوریتم و معماری تمیز', cat: 'learning' as const, q: 'q2' as const },
    { title: 'طراحی وایرفریم ماژول گزارش‌های هوشمند', cat: 'creative' as const, q: 'q2' as const },
    { title: 'برنامه‌ریزی تغذیه و تمرین هفتگی', cat: 'health' as const, q: 'q2' as const },
    { title: 'تسویه صورت‌حساب‌ها و بررسی جریان نقدی', cat: 'finance' as const, q: 'q1' as const },
    { title: 'تدوین مستندات فنی پروژه', cat: 'work' as const, q: 'q2' as const },
  ];

  for (let offset = -68; offset <= -1; offset += 2) {
    const dk = addDays(todayKey, offset);
    const tpl = histTitles[Math.abs(offset) % histTitles.length];
    const roll = seededUnit((offset + 200) * 29);
    const isCompleted = offset > -30 ? roll < 0.84 : roll < 0.72;
    tasks.push({
      id: `tsk_hist_${Math.abs(offset)}`,
      title: tpl.title,
      description: 'ثبت‌شده در تاریخچه عملکرد ماهانه',
      dueDate: dk,
      dueTime: '11:00',
      priority: tpl.q === 'q1' ? 'high' : 'medium',
      quadrant: tpl.q,
      category: tpl.cat,
      tags: [tpl.cat === 'work' ? 'پروژه' : 'برنامه'],
      status: isCompleted ? 'completed' : 'todo',
      subtasks: [],
      recurrence: 'none',
      reminderEnabled: false,
      reminderOffsetMin: 0,
      archived: isCompleted && offset < -14,
      postponeCount: isCompleted ? 0 : 1,
      createdAt: nowIso,
      updatedAt: nowIso,
      completedDate: isCompleted ? dk : null,
      completedAt: isCompleted ? nowIso : null,
    });
  }

  // Focus sessions across today and last 65 days
  const focusSessions: FocusSession[] = [
    {
      id: 'foc_today_1',
      mode: 'pomodoro',
      durationMinutes: 45,
      durationSeconds: 2700,
      dateKey: todayKey,
      taskId: 'tsk_today_1',
      taskTitleSnapshot: 'بازبینی معماری سیستم طراحی نسخه جدید',
      completedNaturally: true,
      startedAt: nowIso,
      endedAt: nowIso,
    },
    {
      id: 'foc_today_2',
      mode: 'pomodoro',
      durationMinutes: 25,
      durationSeconds: 1500,
      dateKey: todayKey,
      taskId: 'tsk_today_2',
      taskTitleSnapshot: 'تکمیل گزارش استراتژی فصل پاییز',
      completedNaturally: true,
      startedAt: nowIso,
      endedAt: nowIso,
    },
  ];

  for (let offset = -64; offset <= -1; offset++) {
    const roll = seededUnit((offset + 300) * 41);
    if (roll < 0.25) continue;
    const dk = addDays(todayKey, offset);
    const sessionsToday = roll > 0.75 ? 2 : 1;
    for (let s = 0; s < sessionsToday; s++) {
      const mins = s === 0 ? (roll > 0.6 ? 50 : 25) : 25;
      focusSessions.push({
        id: `foc_hist_${Math.abs(offset)}_${s}`,
        mode: s === 0 && mins === 50 ? 'free' : 'pomodoro',
        durationMinutes: mins,
        durationSeconds: mins * 60,
        dateKey: dk,
        taskId: null,
        taskTitleSnapshot: s === 0 ? 'تمرکز عمیق روی پروژه اصلی' : 'مرور و مطالعه تخصصی',
        completedNaturally: true,
        startedAt: nowIso,
        endedAt: nowIso,
      });
    }
  }

  // Active Goals
  const goals: Goal[] = [
    {
      id: 'gol_1',
      title: 'مطالعه 12 کتاب تخصصی در سال جاری',
      description: 'تمرکز بر طراحی محصول، مهندسی نرم‌افزار و بهره‌وری فردی',
      category: 'learning',
      currentValue: 8,
      targetValue: 12,
      unit: 'کتاب',
      deadline: addDays(todayKey, 80),
      completed: false,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
    {
      id: 'gol_2',
      title: 'ثبت 100 ساعت کار عمیق فصل پاییز',
      description: 'حفظ ریتم پایدار تمرکز روزانه بدون حواس‌پرتی',
      category: 'work',
      currentValue: 68,
      targetValue: 100,
      unit: 'ساعت',
      deadline: addDays(todayKey, 35),
      completed: false,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
    {
      id: 'gol_3',
      title: 'تکمیل 40 جلسه تمرین قدرتی',
      description: 'پایبندی منظم به تمرین 4 روز در هفته',
      category: 'health',
      currentValue: 29,
      targetValue: 40,
      unit: 'جلسه',
      deadline: addDays(todayKey, 45),
      completed: false,
      createdAt: nowIso,
      updatedAt: nowIso,
    },
  ];

  const existingSettings = await db.settings.get('default');

  await db.transaction(
    'rw',
    [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings],
    async () => {
      await Promise.all([
        db.tasks.clear(),
        db.habits.clear(),
        db.habitLogs.clear(),
        db.focusSessions.clear(),
        db.goals.clear(),
      ]);
      await db.tasks.bulkPut(tasks);
      await db.habits.bulkPut(habits);
      await db.habitLogs.bulkPut(habitLogs);
      await db.focusSessions.bulkPut(focusSessions);
      await db.goals.bulkPut(goals);
      await db.settings.put({
        ...DEFAULT_SETTINGS,
        ...(existingSettings ?? {}),
        id: 'default',
        hasSampleData: true,
        sampleBannerDismissed: false,
        updatedAt: nowIso,
      });
    }
  );
}
