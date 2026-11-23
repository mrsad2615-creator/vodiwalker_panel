import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import {
  Bell,
  Database,
  Download,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Sparkles,
  TableIcon,
  Trash2,
  Upload,
  User,
  Volume2,
} from '@/components/ui/icons';
import {
  clearAllUserData,
  db,
  exportBackup,
  importBackup,
  updateSettings,
} from '@/lib/db';
import { populateStarterWorkspace } from '@/lib/seed';
import {
  fireInstantNotification,
  getNotificationPermission,
  requestNotificationPermission,
  type NotificationPermissionState,
} from '@/lib/notifications';
import { isNativeAndroid, triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { SectionHeader } from '@/components/ui/SectionHeader';
import { sectionEyebrow } from '@/lib/sections';
import { NumericInput } from '@/components/ui/NumericInput';
import { KairosLogoMark } from '@/components/ui/KairosMark';
import { SafeAreaDiagnostics } from '@/components/ui/SafeAreaDiagnostics';
import { AppearanceStudio } from '@/components/settings/AppearanceStudio';
import { useFocusSessions, useHabitLogs, useHabits, useTasks } from '@/lib/queries';
import { exportBackupFile, readBackupFile, saveTextFile } from '@/lib/backup';
import { tasksToCsv, habitLogsToCsv, focusSessionsToCsv } from '@/lib/csv';
import { APP_VERSION, BUILD_NUMBER, BUILD_SHA } from '@/lib/buildInfo';
import { AI_MODEL_HINT, diagnoseAi, testAiConnection, type AiDiagnosis } from '@/lib/ai/client';
import { AI_PROVIDERS, providerFor } from '@/lib/ai/providers';

export function SettingsPage() {
  const { settings, pushToast } = useApp();
  // The CSV export reads the same live rows the rest of the app draws from.
  const tasks = useTasks();
  const habits = useHabits();
  const habitLogs = useHabitLogs();
  const focusSessions = useFocusSessions();
  const [displayName, setDisplayName] = useState(settings.displayName);
  const [dailyFocusGoal, setDailyFocusGoal] = useState(settings.dailyFocusGoalMinutes);
  const [dailyTaskGoal, setDailyTaskGoal] = useState(settings.dailyTaskGoal);
  const [permState, setPermState] = useState<NotificationPermissionState>('prompt');
  const [confirmWipe, setConfirmWipe] = useState(false);
  // The cloud manager. Every field starts from what is stored, and nothing is
  // written until «ذخیره» is pressed — the key is a secret, not a live control.
  const [aiEnabled, setAiEnabled] = useState(settings.aiEnabled);
  const [aiBaseUrl, setAiBaseUrl] = useState(settings.aiBaseUrl);
  const [aiModel, setAiModel] = useState(settings.aiModel);
  const [aiKey, setAiKey] = useState(settings.aiKey);
  const [aiAutoApply, setAiAutoApply] = useState(settings.aiAutoApply);
  const [aiTesting, setAiTesting] = useState(false);
  const [aiDiagnosis, setAiDiagnosis] = useState<AiDiagnosis | null>(null);
  const [copiedDiagnosis, setCopiedDiagnosis] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setDisplayName(settings.displayName);
  }, [settings.displayName]);

  useEffect(() => {
    setDailyFocusGoal(settings.dailyFocusGoalMinutes);
  }, [settings.dailyFocusGoalMinutes]);

  useEffect(() => {
    setDailyTaskGoal(settings.dailyTaskGoal);
  }, [settings.dailyTaskGoal]);

  useEffect(() => {
    void getNotificationPermission().then(setPermState);
  }, []);

  useEffect(() => {
    setAiEnabled(settings.aiEnabled);
    setAiBaseUrl(settings.aiBaseUrl);
    setAiModel(settings.aiModel);
    setAiKey(settings.aiKey);
    setAiAutoApply(settings.aiAutoApply);
  }, [settings.aiEnabled, settings.aiBaseUrl, settings.aiModel, settings.aiKey, settings.aiAutoApply]);

  const handleSaveAi = async () => {
    const enabled = aiEnabled && aiKey.trim().length > 0 && aiModel.trim().length > 0;
    await updateSettings({
      aiEnabled: enabled,
      aiBaseUrl: aiBaseUrl.trim() || 'https://openrouter.ai/api/v1',
      aiModel: aiModel.trim(),
      aiKey: aiKey.trim(),
      aiAutoApply: enabled ? aiAutoApply : false,
      aiConsentAt: enabled ? new Date().toISOString() : null,
    });
    if (aiEnabled && !enabled) {
      pushToast({
        title: 'مدیر ابری روشن نشد',
        description: 'کلید API و نام مدل لازم است؛ تا آن موقع مدیر آفلاین جواب می‌دهد.',
        tone: 'warning',
      });
      return;
    }
    pushToast({
      title: enabled ? 'مدیر ابری فعال شد' : 'مدیر ابری خاموش شد',
      description: enabled
        ? 'فقط سؤال و خلاصهٔ داده‌هایت به همین آدرس فرستاده می‌شود.'
        : 'همه‌چیز مثل قبل آفلاین کار می‌کند.',
      tone: 'success',
    });
  };

  const handleTestAi = async () => {
    setAiTesting(true);
    setAiDiagnosis(null);
    setCopiedDiagnosis(false);
    const candidate = {
      ...settings,
      aiEnabled: true,
      aiBaseUrl: aiBaseUrl.trim(),
      aiModel: aiModel.trim(),
      aiKey: aiKey.trim(),
    };
    const [result, diagnosis] = await Promise.all([
      testAiConnection({ settings: candidate }),
      diagnoseAi(candidate),
    ]);
    setAiTesting(false);
    setAiDiagnosis(diagnosis);
    pushToast({
      title: result.ok ? 'اتصال برقرار است' : 'اتصال برقرار نشد',
      description: diagnosis.hint,
      tone: result.ok ? 'success' : 'warning',
    });
  };

  const copyDiagnosis = async () => {
    if (!aiDiagnosis) return;
    const text = [
      `platform=${aiDiagnosis.via}`,
      `url=${aiDiagnosis.url}`,
      `model=${aiDiagnosis.model}`,
      `keyLooksValid=${aiDiagnosis.keyLooksValid}`,
      `reached=${aiDiagnosis.reached}`,
      `status=${aiDiagnosis.status}`,
      `detail=${aiDiagnosis.detail}`,
    ].join('\n');
    try {
      await navigator.clipboard?.writeText(text);
      setCopiedDiagnosis(true);
    } catch {
      pushToast({ title: 'کپی نشد', description: text.slice(0, 120), tone: 'warning' });
    }
  };

  const dbCounts = useLiveQuery(async () => {
    const [tasks, habits, logs, sessions, goals] = await Promise.all([
      db.tasks.count(),
      db.habits.count(),
      db.habitLogs.count(),
      db.focusSessions.count(),
      db.goals.count(),
    ]);
    return { tasks, habits, logs, sessions, goals };
  }, []) ?? { tasks: 0, habits: 0, logs: 0, sessions: 0, goals: 0 };

  const handleSaveProfile = async () => {
    await updateSettings({
      displayName: displayName.trim() || 'کاربر کایروس',
      dailyFocusGoalMinutes: Math.max(15, Math.min(720, Number(dailyFocusGoal) || 120)),
      dailyTaskGoal: Math.max(1, Math.min(50, Number(dailyTaskGoal) || 6)),
    });
    void triggerHaptic('success', settings.hapticsEnabled);
    pushToast({ title: 'تنظیمات پروفایل ذخیره شد', tone: 'success' });
  };

  const handleRequestNotifications = async () => {
    const next = await requestNotificationPermission();
    setPermState(next);
    if (next === 'granted') {
      await updateSettings({ notificationsEnabled: true });
      pushToast({ title: 'مجوز اعلان‌ها فعال شد', tone: 'success' });
    } else {
      pushToast({
        title: 'مجوز اعلان سیستم تایید نشد',
        description: 'یادآوری‌های درون‌برنامه‌ای همچنان فعال خواهند بود.',
        tone: 'warning',
      });
    }
  };

  const handleTestNotification = async () => {
    const deliveredSystem = await fireInstantNotification(
      'اعلان آزمایشی کایروس • KAIROS',
      'سیستم یادآوری و اعلان‌های شما با موفقیت فعال است.',
      { soundEnabled: settings.soundEnabled }
    );
    pushToast({
      title: 'اعلان آزمایشی ارسال شد',
      description: deliveredSystem
        ? 'اعلان سیستمی و پیام درون‌برنامه‌ای هر دو با موفقیت اجرا شدند.'
        : 'پیام درون‌برنامه‌ای و صدای زنگ اجرا شد (برای اعلان سیستمی مجوز مرورگر/اندروید را فعال کنید).',
      tone: 'reminder',
    });
  };

  const handleExport = async () => {
    const data = await exportBackup();
    const result = await exportBackupFile(data);
    if (!result.ok) {
      pushToast({
        title: 'ذخیرهٔ پشتیبان انجام نشد',
        description: 'دسترسی به فایل‌ها ممکن نبود؛ دوباره تلاش کنید.',
        tone: 'warning',
      });
      return;
    }
    pushToast({
      title: result.via === 'share' ? 'پشتیبان آمادهٔ اشتراک‌گذاری است' : 'فایل پشتیبان ذخیره شد',
      description:
        result.via === 'share'
          ? 'فایل JSON ساخته شد؛ از پنجرهٔ باز‌شده در درایو، تلگرام یا فایل‌ها ذخیره‌اش کنید.'
          : 'فایل JSON در پوشهٔ دانلود ذخیره شد.',
      tone: 'success',
    });
  };

  /**
   * CSV export: the honest counterpart to the JSON backup. A backup is for coming
   * back; the CSV is for the spreadsheet where the user actually thinks about
   * their week. Three files, named by content, so they never overwrite each other.
   */
  const handleCsvExport = async () => {
    const stamp = new Date().toISOString().slice(0, 10);
    const files: [string, string][] = [
      [`kairos-tasks-${stamp}.csv`, tasksToCsv(tasks)],
      [`kairos-habits-${stamp}.csv`, habitLogsToCsv(habits, habitLogs)],
      [`kairos-focus-${stamp}.csv`, focusSessionsToCsv(focusSessions)],
    ];

    let saved = 0;
    for (const [name, csv] of files) {
      const result = await saveTextFile(name, csv, 'text/csv;charset=utf-8', 'خروجی CSV کایروس');
      if (result.ok) saved++;
    }

    pushToast(
      saved === files.length
        ? {
            title: 'خروجی CSV آماده است',
            description: 'سه فایل: وظایف، عادت‌ها و جلسه‌های تمرکز — هر ردیف یک رکورد.',
            tone: 'success',
          }
        : {
            title: 'خروجی ناقص ماند',
            description: `${saved} فایل از ${files.length} ذخیره شد؛ دوباره تلاش کنید.`,
            tone: 'warning',
          }
    );
  };

  const handleImportFile = async (file: File) => {
    try {
      const parsed = await readBackupFile(file);
      await importBackup(parsed);
      pushToast({
        title: 'بازیابی پشتیبان با موفقیت انجام شد',
        description: 'تمام وظایف، عادت‌ها و گزارش‌های شما بازگردانی شدند.',
        tone: 'success',
      });
    } catch {
      pushToast({
        title: 'خطا در خواندن فایل پشتیبان',
        description: 'لطفاً یک فایل JSON معتبر ساخته‌شده توسط کایروس انتخاب کنید.',
        tone: 'warning',
      });
    }
  };

  return (
    <div className="space-y-4">
      <SectionHeader
        eyebrow={sectionEyebrow('settings')}
        title="تنظیمات"
        meta={<span>شخصی‌سازی، یادآوری‌ها و مدیریت دادهٔ آفلاین دستگاه</span>}
      />

      {/* PROFILE & DAILY GOALS */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center gap-2 text-xs font-bold text-[var(--text)]">
          <User className="h-4 w-4 text-[var(--accent-fg)]" />
          <span>پروفایل و اهداف روزانه</span>
        </div>

        <div>
          <label className="mb-1.5 block text-[11px] text-[var(--text-2)]">نام نمایشی در داشبورد</label>
          <input
            type="text"
            dir="auto"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="luxury-input text-xs"
          />
        </div>

        <div className="grid grid-cols-2 gap-2.5">
          <div>
            <label className="mb-1.5 block text-[11px] text-[var(--text-2)]">هدف تمرکز روزانه (دقیقه)</label>
            <NumericInput
              min={15}
              max={720}
              fallback={120}
              value={dailyFocusGoal}
              onChange={setDailyFocusGoal}
              className="luxury-input num-geo text-center text-xs"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-[11px] text-[var(--text-2)]">هدف وظایف روزانه</label>
            <NumericInput
              min={1}
              max={50}
              fallback={6}
              value={dailyTaskGoal}
              onChange={setDailyTaskGoal}
              className="luxury-input num-geo text-center text-xs"
            />
          </div>
        </div>

        <button
          type="button"
          onClick={handleSaveProfile}
          className="btn-royal w-full rounded-xl py-2.5 text-xs font-semibold"
        >
          ذخیره تنظیمات پروفایل
        </button>
      </LuxuryCard>

      {/* APPEARANCE — accent, environment, motion */}
      <LuxuryCard className="p-4">
        <AppearanceStudio />
      </LuxuryCard>

      {/* NOTIFICATIONS & HAPTICS */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-bold text-[var(--text)]">
            <Bell className="h-4 w-4 text-[var(--accent-fg)]" />
            <span>اعلان‌ها و یادآوری‌ها</span>
          </div>
          <span className="rounded-lg bg-[var(--surface-3)] px-2 py-0.5 text-[10px] font-medium text-[var(--accent-fg)]">
            {permState === 'granted'
              ? 'مجوز فعال'
              : permState === 'denied'
              ? 'مسدود در سیستم'
              : 'نیاز به تایید'}
          </span>
        </div>

        {/* Toggle rows */}
        <div className="space-y-3 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-[var(--text)]">یادآوری وظایف و پایان تمرکز</span>
            <button
              type="button"
              role="switch"
              aria-checked={settings.notificationsEnabled}
              onClick={() =>
                updateSettings({ notificationsEnabled: !settings.notificationsEnabled })
              }
              className={`relative h-6 w-11 rounded-full transition-colors ${
                settings.notificationsEnabled ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[var(--text)] transition-all ${
                  settings.notificationsEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-[var(--text)]">
              <Volume2 className="h-3.5 w-3.5 text-[var(--text-2)]" />
              صدای زنگ آرام تمرکز
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={settings.soundEnabled}
              onClick={() => updateSettings({ soundEnabled: !settings.soundEnabled })}
              className={`relative h-6 w-11 rounded-full transition-colors ${
                settings.soundEnabled ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[var(--text)] transition-all ${
                  settings.soundEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-[var(--text)]">
              <Smartphone className="h-3.5 w-3.5 text-[var(--text-2)]" />
              بازخورد لمسی (Haptics)
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={settings.hapticsEnabled}
              onClick={() => updateSettings({ hapticsEnabled: !settings.hapticsEnabled })}
              className={`relative h-6 w-11 rounded-full transition-colors ${
                settings.hapticsEnabled ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[var(--text)] transition-all ${
                  settings.hapticsEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2 pt-1">
          {permState !== 'granted' && (
            <button
              type="button"
              onClick={handleRequestNotifications}
              className="btn-ghost-luxury rounded-xl py-2 text-[11px] font-medium text-[var(--accent-fg)]"
            >
              درخواست مجوز سیستم
            </button>
          )}
          <button
            type="button"
            onClick={handleTestNotification}
            className={`btn-ghost-luxury rounded-xl py-2 text-[11px] font-medium text-[var(--text)] ${
              permState === 'granted' ? 'col-span-2' : ''
            }`}
          >
            ارسال اعلان آزمایشی
          </button>
        </div>
      </LuxuryCard>

      {/* THE CLOUD MANAGER — optional, off by default */}
      <LuxuryCard className="space-y-3.5 p-4" data-testid="ai-settings">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2 text-xs font-bold text-[var(--text)]">
            <Sparkles className="h-4 w-4 text-[var(--accent-fg)]" />
            <span>مدیر ابری (اختیاری)</span>
          </div>
          <button
            type="button"
            aria-pressed={aiEnabled}
            onClick={() => setAiEnabled((value) => !value)}
            className={`rounded-full px-3 py-1 text-[10.5px] font-semibold transition-colors ${
              aiEnabled
                ? 'bg-[var(--accent)] text-[var(--accent-on)]'
                : 'border border-[var(--border)] text-[var(--text-2)]'
            }`}
          >
            {aiEnabled ? 'روشن' : 'خاموش'}
          </button>
        </div>

        <p className="text-[10.5px] leading-5 text-[var(--text-3)]">
          اگر «تشخیص اتصال» می‌گوید درخواست به سرور نرسید، یعنی شبکه اجازهٔ رفتن به آن سرویس را
          نمی‌دهد (فیلتر، DNS یا اینترنت). در آن حالت یا سرویس دیگری را امتحان کن، یا Ollama را روی
          کامپیوتر خودت بالا بیاور. خاموش که باشد، هیچ‌چیز از گوشی بیرون نمی‌رود و همان مدیر آفلاین جواب می‌دهد. روشن که باشد،
          فقط جملهٔ تو و خلاصهٔ همان چیزی که لازم است (کارها، عادت‌ها، تمرکز) به آدرسی که خودت
          می‌نویسی فرستاده می‌شود. کلید API فقط روی همین دستگاه ذخیره می‌شود و{' '}
          <span className="text-[var(--text-2)]">در فایل پشتیبان نوشته نمی‌شود</span>.
        </p>

        {/* Pick a provider: fills the address and a known-good model, then the
            only thing left is the key. */}
        <div className="space-y-1.5">
          <span className="block text-[10.5px] text-[var(--text-2)]">سرویس</span>
          <div className="flex flex-wrap gap-1.5">
            {AI_PROVIDERS.map((provider) => (
              <button
                key={provider.id}
                type="button"
                onClick={() => {
                  setAiBaseUrl(provider.baseUrl);
                  if (provider.model) setAiModel(provider.model);
                }}
                aria-pressed={providerFor(aiBaseUrl)?.id === provider.id}
                className={`rounded-full px-2.5 py-1 text-[10px] transition-colors ${
                  providerFor(aiBaseUrl)?.id === provider.id
                    ? 'bg-[var(--accent)] text-[var(--accent-on)]'
                    : 'border border-[var(--border)] text-[var(--text-2)]'
                }`}
              >
                {provider.name}
              </button>
            ))}
          </div>
          {providerFor(aiBaseUrl) && (
            <p className="text-[9.5px] leading-4 text-[var(--text-3)]">
              {providerFor(aiBaseUrl)!.note}{' '}
              {providerFor(aiBaseUrl)!.keyUrl && (
                <a
                  href={providerFor(aiBaseUrl)!.keyUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="font-medium text-[var(--accent-fg)] underline decoration-dotted"
                >
                  دریافت کلید ←
                </a>
              )}
            </p>
          )}
        </div>

        <div>
          <label htmlFor="ai-base" className="mb-1.5 block text-[11px] text-[var(--text-2)]">
            آدرس سرویس (سازگار با OpenAI)
          </label>
          <input
            id="ai-base"
            dir="ltr"
            value={aiBaseUrl}
            onChange={(event) => setAiBaseUrl(event.target.value)}
            placeholder="https://openrouter.ai/api/v1"
            className="luxury-input text-xs"
          />
        </div>

        <div>
          <label htmlFor="ai-model" className="mb-1.5 block text-[11px] text-[var(--text-2)]">
            نام مدل
          </label>
          <input
            id="ai-model"
            dir="ltr"
            value={aiModel}
            onChange={(event) => setAiModel(event.target.value)}
            placeholder="openai/gpt-4o-mini"
            className="luxury-input text-xs"
          />
          <p className="num-geo mt-1 text-[9.5px] text-[var(--text-3)]" dir="ltr">
            {AI_MODEL_HINT}
          </p>
        </div>

        <div>
          <label htmlFor="ai-key" className="mb-1.5 block text-[11px] text-[var(--text-2)]">
            کلید API
          </label>
          <input
            id="ai-key"
            type="password"
            dir="ltr"
            autoComplete="off"
            value={aiKey}
            onChange={(event) => setAiKey(event.target.value)}
            placeholder="sk-..."
            className="luxury-input text-xs"
          />
        </div>

        <button
          type="button"
          aria-pressed={aiAutoApply}
          onClick={() => setAiAutoApply((value) => !value)}
          className="flex w-full items-center justify-between gap-2 rounded-xl border border-[var(--border)] px-3 py-2 text-start text-[11px] text-[var(--text-2)]"
        >
          <span>
            تغییرات سطح ۲ را بدون پرسیدن انجام بده
            <span className="mt-0.5 block text-[9.5px] text-[var(--text-3)]">
              ساخت و ویرایش کار، تیک مرحله، ثبت عادت. حذف هیچ‌وقت در دسترس مدیر نیست.
            </span>
          </span>
          <span
            className={`grid h-5 w-9 shrink-0 place-items-center rounded-full ${
              aiAutoApply ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
            }`}
          >
            <span
              className={`h-3.5 w-3.5 rounded-full bg-[var(--bg)] transition-transform ${
                aiAutoApply ? '-translate-x-2' : 'translate-x-2'
              }`}
            />
          </span>
        </button>

        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => void handleSaveAi()}
            className="btn-royal rounded-xl py-2.5 text-xs font-semibold"
          >
            ذخیرهٔ مدیر ابری
          </button>
          <button
            type="button"
            onClick={() => void handleTestAi()}
            disabled={aiTesting}
            className="btn-ghost-luxury rounded-xl py-2.5 text-xs font-medium text-[var(--text)] disabled:opacity-50"
          >
            {aiTesting ? 'در حال آزمودن…' : 'تشخیص اتصال'}
          </button>
        </div>

        {aiDiagnosis && (
          <div
            data-testid="ai-diagnosis"
            className={`space-y-1.5 rounded-xl border p-2.5 ${
              aiDiagnosis.reached
                ? 'border-[var(--border)] bg-[var(--surface-1)]'
                : 'border-[var(--danger)]/40 bg-[color-mix(in_srgb,var(--danger)_8%,transparent)]'
            }`}
          >
            <p className="text-[10.5px] font-medium leading-5 text-[var(--text)]">
              {aiDiagnosis.hint}
            </p>
            <p className="num-geo text-[9px] leading-4 text-[var(--text-3)]" dir="ltr">
              {aiDiagnosis.via} · status {aiDiagnosis.status} · {aiDiagnosis.reached ? 'reached' : 'blocked'}
              {aiDiagnosis.detail ? ` · ${aiDiagnosis.detail.slice(0, 120)}` : ''}
            </p>
            <button
              type="button"
              onClick={() => void copyDiagnosis()}
              className="text-[9.5px] font-medium text-[var(--accent-fg)]"
            >
              {copiedDiagnosis ? 'کپی شد' : 'کپی جزئیات برای فرستادن'}
            </button>
          </div>
        )}
      </LuxuryCard>

      {/* LOCAL DATA & PRIVACY */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center gap-2 text-xs font-bold text-[var(--text)]">
          <Database className="h-4 w-4 text-[var(--accent-fg)]" />
          <span>ذخیره‌سازی آفلاین و پشتیبان‌گیری</span>
        </div>

        <div className="grid grid-cols-5 gap-1.5 rounded-2xl border border-[var(--accent-fg)]/10 bg-[var(--surface-input)] p-2.5 text-center">
          <div>
            <Numeric value={dbCounts.tasks} variant="display" className="text-xl font-bold text-[var(--text)]" />
            <span className="mt-0.5 block text-[9px] text-[var(--text-2)]">وظیفه</span>
          </div>
          <div>
            <Numeric value={dbCounts.habits} variant="display" className="text-xl font-bold text-[var(--accent-fg)]" />
            <span className="mt-0.5 block text-[9px] text-[var(--text-2)]">عادت</span>
          </div>
          <div>
            <Numeric value={dbCounts.logs} variant="display" className="text-xl font-bold text-[var(--text)]" />
            <span className="mt-0.5 block text-[9px] text-[var(--text-2)]">ثبت عادت</span>
          </div>
          <div>
            <Numeric value={dbCounts.sessions} variant="display" className="text-xl font-bold text-[var(--accent-fg)]" />
            <span className="mt-0.5 block text-[9px] text-[var(--text-2)]">تمرکز</span>
          </div>
          <div>
            <Numeric value={dbCounts.goals} variant="display" className="text-xl font-bold text-[var(--text)]" />
            <span className="mt-0.5 block text-[9px] text-[var(--text-2)]">هدف</span>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={handleExport}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[var(--text)]"
          >
            <Download className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
            <span>خروجی پشتیبان</span>
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[var(--text)]"
          >
            <Upload className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
            <span>بازیابی پشتیبان</span>
          </button>
          <button
            type="button"
            onClick={() => void handleCsvExport()}
            className="btn-ghost-luxury col-span-2 flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[var(--text)]"
          >
            <TableIcon className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
            <span>خروجی جدول (CSV) برای اکسل</span>
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json"
            className="hidden"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void handleImportFile(f);
              e.target.value = '';
            }}
          />
        </div>

        <div className="space-y-2 border-t border-[var(--accent-fg)]/10 pt-3">
          <button
            type="button"
            onClick={async () => {
              await populateStarterWorkspace();
              pushToast({
                title: 'داده‌های نمونه بارگذاری شدند',
                description: 'تمامی گزارش‌های ماهانه و نقشه حرارتی با سابقه 95 روزه آماده بررسی‌اند.',
                tone: 'success',
              });
            }}
            className="btn-ghost-luxury flex w-full items-center justify-center gap-1.5 rounded-xl py-2 text-[11px] font-medium text-[var(--text-2)]"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>بارگذاری مجدد داده‌های نمونه</span>
          </button>

          {!confirmWipe ? (
            <button
              type="button"
              onClick={() => setConfirmWipe(true)}
              className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-[var(--danger)]/25 bg-[var(--danger)]/10 py-2 text-[11px] font-medium text-[var(--danger-fg)] transition hover:bg-[var(--danger)]/20"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>پاک‌سازی کامل و شروع از فضای خالی</span>
            </button>
          ) : (
            <div className="flex items-center gap-2 rounded-xl border border-[var(--danger)]/35 bg-[var(--danger)]/12 p-2.5">
              <span className="flex-1 text-[11px] text-[var(--text)]">
                تمام داده‌ها حذف شوند؟
              </span>
              <button
                type="button"
                onClick={async () => {
                  await clearAllUserData();
                  setConfirmWipe(false);
                  pushToast({ title: 'تمام داده‌ها پاک شدند', tone: 'default' });
                }}
                className="rounded-lg bg-[var(--danger)] px-3 py-1 text-[11px] font-bold text-[var(--bg)]"
              >
                بله، پاک کن
              </button>
              <button
                type="button"
                onClick={() => setConfirmWipe(false)}
                className="rounded-lg bg-[var(--surface-2)] px-2.5 py-1 text-[11px] text-[var(--text-2)]"
              >
                انصراف
              </button>
            </div>
          )}
        </div>
      </LuxuryCard>

      {/* PRIVACY & PLATFORM BADGE */}
      <LuxuryCard variant="subtle" className="flex items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <KairosLogoMark size={34} />
          <div>
            <p className="font-brand text-xs font-bold text-[var(--text)]">KAIROS {APP_VERSION} • MRSAD</p>
            <p className="num-geo mt-0.5 text-[10px] text-[var(--text-3)]">
              build {BUILD_NUMBER} · {BUILD_SHA}
            </p>
            <p className="mt-0.5 flex items-center gap-1 text-[10.5px] text-[var(--text-2)]">
              <ShieldCheck className="h-3.5 w-3.5 text-[var(--accent)]" />
              <span>
                100% آفلاین •{' '}
                {isNativeAndroid() ? 'اجرای بومی اندروید' : 'آماده نصب PWA و اندروید'}
              </span>
            </p>
          </div>
        </div>
      </LuxuryCard>

      <SafeAreaDiagnostics />
    </div>
  );
}
