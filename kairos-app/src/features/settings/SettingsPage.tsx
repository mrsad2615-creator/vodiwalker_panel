import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import {
  Bell,
  Database,
  Download,
  ShieldCheck,
  Smartphone,
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
  inspectBackup,
  updateSettings,
  type BackupImportResult,
} from '@/lib/db';
import { SampleDataControl } from '@/components/settings/SampleDataControl';
import {
  fireInstantNotification,
  getNotificationPermission,
  requestNotificationPermission,
  syncTaskRemindersNative,
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
import { hasProgress } from '@/lib/chrono/persistence';
import { syncWidgetSnapshot } from '@/components/settings/WidgetSync';
import { buildWidgetSnapshot } from '@/lib/widgetSnapshot';
import { getTodayKey } from '@/lib/date';
import type { KairosBackupPayload } from '@/types/domain';

export function SettingsPage() {
  const { settings, pushToast, focus, setFocusLinkedTask } = useApp();
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
  const [wiping, setWiping] = useState(false);
  const [widgetSyncError, setWidgetSyncError] = useState(false);
  const [nativeCleanupError, setNativeCleanupError] = useState(false);
  const [pendingBackup, setPendingBackup] = useState<KairosBackupPayload | null>(null);
  const [backupEstimate, setBackupEstimate] = useState<BackupImportResult | null>(null);
  const [restorePreferences, setRestorePreferences] = useState(false);
  const [importing, setImporting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [encryptExport, setEncryptExport] = useState(false);
  const [exportPassword, setExportPassword] = useState('');
  const [exportConfirmation, setExportConfirmation] = useState('');
  const [encryptedFile, setEncryptedFile] = useState<File | null>(null);
  const [importPassword, setImportPassword] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const backupReadVersion = useRef(0);

  useEffect(() => {
    const fail = () => setWidgetSyncError(true);
    window.addEventListener('kairos-widget-sync-error', fail);
    return () => window.removeEventListener('kairos-widget-sync-error', fail);
  }, []);

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
    if (exporting) return;
    if (encryptExport && (exportPassword.length < 12 || exportPassword !== exportConfirmation)) {
      pushToast({ title: 'گذرواژه را بررسی کنید', description: 'حداقل ۱۲ نویسه و تکرار یکسان لازم است.', tone: 'warning' });
      return;
    }
    setExporting(true);
    let result: Awaited<ReturnType<typeof exportBackupFile>>;
    try {
      const data = await exportBackup();
      result = await exportBackupFile(data, encryptExport ? exportPassword : undefined);
    } catch {
      pushToast({ title: 'ساخت پشتیبان انجام نشد', description: 'خواندن داده‌ها یا رمزگذاری کامل نشد؛ دوباره تلاش کنید.', tone: 'warning' });
      return;
    } finally {
      setExporting(false);
    }
    if (!result.ok) {
      pushToast({
        title: 'ذخیرهٔ پشتیبان انجام نشد',
        description: 'دسترسی به فایل‌ها ممکن نبود؛ دوباره تلاش کنید.',
        tone: 'warning',
      });
      return;
    }
    setExportPassword('');
    setExportConfirmation('');
    pushToast({
      title: result.via === 'share' ? 'پشتیبان آمادهٔ اشتراک‌گذاری است' : 'درخواست دانلود پشتیبان ارسال شد',
      description:
        result.via === 'share'
          ? 'فایل JSON ساخته شد؛ از پنجرهٔ باز‌شده در مقصد دلخواه ذخیره‌اش کنید.'
          : 'محل ذخیره و کامل‌شدن دانلود را در مرورگر بررسی کنید.',
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

  const handleImportFile = async (file: File, password?: string) => {
    const version = ++backupReadVersion.current;
    setPendingBackup(null);
    setBackupEstimate(null);
    setRestorePreferences(false);
    if (password === undefined) {
      setEncryptedFile(null);
      setImportPassword('');
    }
    try {
      const backup = await readBackupFile(file, password);
      const estimate = await inspectBackup(backup);
      if (version !== backupReadVersion.current) return;
      setEncryptedFile(null);
      setImportPassword('');
      setBackupEstimate(estimate);
      setPendingBackup(backup);
    } catch (error) {
      if (version !== backupReadVersion.current) return;
      if (error instanceof Error && error.message === 'backup-password-required') {
        setEncryptedFile(file);
        return;
      }
      pushToast({
        title: 'فایل قابل بررسی نیست',
        description: password !== undefined ? 'گذرواژه، سلامت یا نسخهٔ فایل را بررسی کنید؛ هیچ داده‌ای تغییر نکرد.' : 'ساختار یا نسخهٔ فایل یا دسترسی به پایگاه داده را بررسی کنید؛ هیچ داده‌ای تغییر نکرد.',
        tone: 'warning',
      });
    }
  };

  const confirmImport = async () => {
    if (!pendingBackup || importing) return;
    if (restorePreferences && hasProgress(focus)) {
      pushToast({ title: 'ابتدا جلسهٔ تمرکز را پایان دهید', tone: 'warning' });
      return;
    }
    setImporting(true);
    try {
      const result = await importBackup(pendingBackup, { restorePreferences });
      setPendingBackup(null);
      pushToast({
        title: 'بازیابی ادغامی انجام شد',
        description: `${result.added} رکورد اضافه شد؛ ${result.skipped} رکورد موجود دست‌نخورده ماند. ${restorePreferences ? 'ترجیحات انتخاب‌شده بازیابی شدند.' : 'تنظیمات شخصی جایگزین نشد.'}`,
        tone: 'success',
      });
    } catch {
      pushToast({ title: 'بازیابی انجام نشد', description: 'فایل، تنظیمات یا جلسهٔ تمرکز را بررسی کنید؛ تراکنش تغییرات را بازگرداند.', tone: 'warning' });
    } finally {
      setImporting(false);
    }
  };

  return (
    <div className="space-y-4">
      <SectionHeader
        eyebrow={sectionEyebrow('settings')}
        title="تنظیمات"
        meta={<span>شخصی‌سازی، یادآوری‌ها و مدیریت دادهٔ آفلاین دستگاه</span>}
      />

      <LuxuryCard className="space-y-3 p-4">
        <h2 className="text-xs font-bold text-[var(--text)]">ویجت‌های صفحهٔ اصلی</h2>
        <p className="text-xs leading-6 text-[var(--text-2)]">در صفحهٔ اصلی گوشی، از فهرست ویجت‌های اندروید «وظایف امروز»، «نمودار سالانه» یا «تمرکز» را اضافه کن. ویجت‌ها تصویر آخرین دادهٔ همگام‌شده‌اند؛ برای تغییر وظیفه روی ویجت بزن و در برنامه تیک بزن.</p>
        <label className="flex items-center gap-3 text-xs text-[var(--text)]">
          <input type="checkbox" checked={settings.widgetShowTitles !== false} onChange={(event) => { void updateSettings({ widgetShowTitles: event.target.checked }).catch(() => pushToast({ title: 'ذخیرهٔ تنظیم ویجت انجام نشد', tone: 'warning' })); }} />
          نمایش عنوان وظایف روی صفحهٔ اصلی
        </label>
        <p className="text-xs leading-5 text-[var(--text-3)]">عنوان‌ها به‌طور پیش‌فرض نمایش داده می‌شوند و ممکن است دیگران آن‌ها را ببینند؛ با خاموش‌کردن، برنامه تلاش می‌کند تصویر ویجت را بدون عنوان تازه کند.</p>
        {widgetSyncError && <p role="alert" className="text-xs leading-6 text-[var(--danger)]">همگام‌سازی ویجت ناموفق بود. ممکن است تصویر قدیمی هنوز عنوان‌ها را نشان دهد؛ تا رفع مشکل، ویجت را از صفحهٔ اصلی بردار.</p>}
      </LuxuryCard>

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

      <LuxuryCard className="space-y-2 p-4">
        <p className="text-xs font-bold text-[var(--text)]">دستیار محلی</p>
        <p className="text-[11px] leading-6 text-[var(--text-2)]">
          دستیار از فرمان‌های مشخص و داده‌های روی دستگاه استفاده می‌کند؛ مدل زبانی و سرویس بیرونی ندارد.
          گفت‌وگوی آزاد و پاسخ به هر پرسش پشتیبانی نمی‌شود. تنظیمات اتصال قدیمی بدون تغییر در دستگاه باقی می‌مانند، اما دیگر استفاده نمی‌شوند.
        </p>
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

        <p className="text-xs leading-6 text-[var(--text-2)]">پشتیبان وظایف، عادت‌ها، تمرکز، هدف‌ها، ژورنال، پروژه‌ها و بخشی از تنظیمات را ذخیره می‌کند. بازیابی ادغامی است و ترجیحات فقط با انتخاب شما جایگزین می‌شوند؛ نشست فعال و تاریخچه‌ها در فایل نیستند. بدون انتخاب رمزگذاری، فایل شامل اطلاعات شخصی آشکار است.</p>
        <label className="flex min-h-11 items-center gap-2 text-xs text-[var(--text)]">
          <input type="checkbox" checked={encryptExport} onChange={(event) => { setEncryptExport(event.target.checked); setExportPassword(''); setExportConfirmation(''); }} />
          رمزگذاری فایل پشتیبان با گذرواژه
        </label>
        {encryptExport && <div className="space-y-2 text-xs">
          <p className="leading-6 text-[var(--text-2)]">گذرواژه را در جای امن نگه دارید؛ در صورت فراموشی هیچ راه بازیابی فایل وجود ندارد. رمزگذاری شامل فایل‌های CSV نیست.</p>
          <label className="block">گذرواژهٔ پشتیبان (حداقل ۱۲ نویسه)<input type="password" autoComplete="new-password" maxLength={1024} value={exportPassword} onChange={(event) => setExportPassword(event.target.value)} className="mt-1 w-full rounded-xl border border-[var(--accent-fg)]/30 bg-[var(--surface-input)] p-2" /></label>
          <label className="block">تکرار گذرواژه<input type="password" autoComplete="new-password" maxLength={1024} value={exportConfirmation} onChange={(event) => setExportConfirmation(event.target.value)} className="mt-1 w-full rounded-xl border border-[var(--accent-fg)]/30 bg-[var(--surface-input)] p-2" /></label>
        </div>}
        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={() => void handleExport()}
            disabled={exporting}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[var(--text)] disabled:opacity-50"
          >
            <Download className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
            <span>خروجی پشتیبان</span>
          </button>
          <button
            type="button"
            disabled={importing}
            onClick={() => fileInputRef.current?.click()}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[var(--text)] disabled:opacity-50"
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

        {encryptedFile && <div role="region" aria-label="گذرواژهٔ پشتیبان" className="space-y-2 rounded-xl border border-[var(--accent-fg)]/30 p-3 text-xs">
          <label className="block">گذرواژهٔ فایل<input type="password" autoComplete="current-password" maxLength={1024} value={importPassword} onChange={(event) => setImportPassword(event.target.value)} className="mt-1 w-full rounded-xl border border-[var(--accent-fg)]/30 bg-[var(--surface-input)] p-2" /></label>
          <div className="flex gap-2">
            <button type="button" onClick={() => void handleImportFile(encryptedFile, importPassword)} className="btn-royal rounded-xl px-3 py-2">بررسی فایل</button>
            <button type="button" onClick={() => { backupReadVersion.current++; setEncryptedFile(null); setImportPassword(''); }} className="btn-ghost-luxury rounded-xl px-3 py-2">انصراف</button>
          </div>
        </div>}
        {pendingBackup && (
          <div role="region" aria-label="پیش‌نمایش بازیابی" className="space-y-2 rounded-xl border border-[var(--accent-fg)]/30 bg-[var(--surface-1)] p-3 text-[11px] leading-6 text-[var(--text)]">
            <p className="font-bold">پیش‌نمایش بازیابی ادغامی</p>
            <p>وظیفه: {pendingBackup.tasks.length} · عادت: {pendingBackup.habits.length} · ثبت عادت: {pendingBackup.habitLogs.length} · تمرکز: {pendingBackup.focusSessions.length} · هدف: {pendingBackup.goals.length}</p>
            <p>پروژه: {pendingBackup.projects?.length ?? 0} · مرحله: {pendingBackup.projectStages?.length ?? 0} · ژورنال: {pendingBackup.journal?.length ?? 0}</p>
            {backupEstimate && <p>در بررسی فعلی: {backupEstimate.added} رکورد جدید و {backupEstimate.skipped} شناسهٔ موجود. در صورت تغییر داده‌ها پیش از تأیید، نتیجه ممکن است متفاوت باشد.</p>}
            <p className="text-[var(--text-2)]">فقط رکوردهای جدید اضافه می‌شوند؛ شناسه‌های موجود جایگزین نمی‌شوند. وضعیت دادهٔ نمونه ممکن است تغییر کند. فایل‌های قدیمی ممکن است همهٔ بخش‌ها را نداشته باشند. فایل بدون رمزگذاری تأیید صحت رمزنگاری‌شده ندارد؛ حتی رمزگذاری نیز هویت سازنده را ثابت نمی‌کند.</p>
            <label className="flex min-h-11 items-center gap-2 text-[var(--text)]">
              <input type="checkbox" checked={restorePreferences} disabled={importing} onChange={(event) => setRestorePreferences(event.target.checked)} />
              جایگزینی ترجیحات شخصی با تنظیمات فایل
            </label>
            <p className="text-[var(--text-2)]">در صورت انتخاب، ظاهر، هدف‌های روزانه، میان‌برها و تنظیمات معمول تمرکز جایگزین می‌شوند؛ کلید و مجوزها و تنظیمات حساس دستگاه منتقل نمی‌شوند. ابتدا جلسهٔ تمرکز را پایان دهید.</p>
            <div className="flex gap-2">
              <button type="button" disabled={importing} onClick={() => void confirmImport()} className="btn-royal rounded-xl px-3 py-2 disabled:opacity-50">{importing ? 'در حال بازیابی…' : 'تأیید و افزودن'}</button>
              <button type="button" disabled={importing} onClick={() => setPendingBackup(null)} className="btn-ghost-luxury rounded-xl px-3 py-2">انصراف</button>
            </div>
          </div>
        )}

        <div className="space-y-2 border-t border-[var(--accent-fg)]/10 pt-3">
          {nativeCleanupError && <p role="alert" className="text-xs leading-6 text-[var(--danger-fg)]">پاک‌سازی ویجت یا یادآوری سیستم تأیید نشد. ویجت‌ها و اعلان‌ها را دستی بررسی و در صورت نیاز حذف کنید.</p>}
          <SampleDataControl />

          {!confirmWipe ? (
            <button
              type="button"
              onClick={() => setConfirmWipe(true)}
              className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-[var(--danger)]/25 bg-[var(--danger)]/10 py-2 text-[11px] font-medium text-[var(--danger-fg)] transition hover:bg-[var(--danger)]/20"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>پاک‌سازی داده‌های ثبت‌شده</span>
            </button>
          ) : (
            <div className="space-y-3 rounded-xl border border-[var(--danger)]/35 bg-[var(--danger)]/12 p-3">
              <p className="text-xs leading-6 text-[var(--text)]">
                وظایف، عادت‌ها، پروژه‌ها، هدف‌ها، ژورنال، نشست‌ها و سابقهٔ تغییر و بازگردانی از پایگاه محلی حذف می‌شوند. این کار قابل بازگردانی نیست.
              </p>
              <p className="text-xs leading-6 text-[var(--text-2)]">
                تنظیمات و بعضی داده‌های دستگاه حفظ می‌شوند؛ فایل‌های پشتیبان صادرشده و پشتیبان سیستم حذف نمی‌شوند. پاک‌شدن آنی ویجت‌ها و اعلان‌های سیستم تضمین نشده است. پیش از ادامه یک پشتیبان جداگانه بگیرید.
              </p>
              {hasProgress(focus) && <p role="alert" className="text-xs leading-6 text-[var(--danger-fg)]">ابتدا جلسهٔ تمرکز جاری یا مکث‌شده را ذخیره یا بازنشانی کنید.</p>}
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={wiping || hasProgress(focus)}
                  onClick={async () => {
                    if (wiping || hasProgress(focus)) return;
                    setWiping(true);
                    try {
                      await clearAllUserData();
                      setFocusLinkedTask(null);
                      setConfirmWipe(false);
                      const results = await Promise.allSettled([
                        syncTaskRemindersNative([], false),
                        syncWidgetSnapshot(buildWidgetSnapshot([], getTodayKey(), false, false)),
                      ]);
                      const failed = results.some((result) => result.status === 'rejected');
                      setNativeCleanupError(failed);
                      pushToast(failed
                        ? { title: 'داده‌های پایگاه پاک شدند؛ پاک‌سازی دستگاه ناقص است', description: 'ویجت یا یادآوری سیستم ممکن است دادهٔ قدیمی را نشان دهد؛ بررسی و حذف دستی لازم است.', tone: 'warning' }
                        : { title: 'رکوردها و تاریخچهٔ انتخاب‌شده پاک شدند', description: 'تنظیمات، فایل‌های پشتیبان و داده‌های سیستم ممکن است باقی بمانند.', tone: 'default' });
                    } catch {
                      pushToast({ title: 'پاک‌سازی کامل نشد', description: 'دوباره تلاش کنید؛ پاک‌شدن اطلاعات تأیید نشده است.', tone: 'warning' });
                    } finally {
                      setWiping(false);
                    }
                  }}
                  className="min-h-12 rounded-lg bg-[var(--danger)] px-3 py-1 text-xs font-bold text-[var(--bg)] disabled:opacity-50"
                >
                  {wiping ? 'در حال پاک‌سازی…' : 'بله، پاک کن'}
                </button>
                <button
                  type="button"
                  disabled={wiping}
                  onClick={() => setConfirmWipe(false)}
                  className="min-h-12 rounded-lg bg-[var(--surface-2)] px-3 py-1 text-xs text-[var(--text-2)] disabled:opacity-50"
                >
                  انصراف
                </button>
              </div>
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
