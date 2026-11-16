import { useEffect, useRef, useState } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import {
  Bell,
  Database,
  Download,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Trash2,
  Upload,
  User,
  Volume2,
} from 'lucide-react';
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
import { KairosLogoMark } from '@/components/ui/KairosMark';
import type { KairosBackupPayload } from '@/types/domain';

export function SettingsPage() {
  const { settings, pushToast } = useApp();
  const [displayName, setDisplayName] = useState(settings.displayName);
  const [dailyFocusGoal, setDailyFocusGoal] = useState(settings.dailyFocusGoalMinutes);
  const [dailyTaskGoal, setDailyTaskGoal] = useState(settings.dailyTaskGoal);
  const [permState, setPermState] = useState<NotificationPermissionState>('prompt');
  const [confirmWipe, setConfirmWipe] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setDisplayName(settings.displayName);
    setDailyFocusGoal(settings.dailyFocusGoalMinutes);
    setDailyTaskGoal(settings.dailyTaskGoal);
  }, [settings]);

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
    const data = await exportBackup();
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `kairos-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
    pushToast({ title: 'فایل پشتیبان ذخیره شد', tone: 'success' });
  };

  const handleImportFile = async (file: File) => {
    try {
      const raw = await file.text();
      const parsed = JSON.parse(raw) as KairosBackupPayload;
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
      <div>
        <h1 className="text-lg font-bold text-[#F4F1F8]">تنظیمات</h1>
        <p className="mt-0.5 text-[11px] text-[#A79EB9]">
          شخصی‌سازی، یادآوری‌ها و مدیریت داده‌های آفلاین دستگاه
        </p>
      </div>

      {/* PROFILE & DAILY GOALS */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center gap-2 text-xs font-bold text-[#F4F1F8]">
          <User className="h-4 w-4 text-[#B69AEF]" />
          <span>پروفایل و اهداف روزانه</span>
        </div>

        <div>
          <label className="mb-1.5 block text-[11px] text-[#A79EB9]">نام نمایشی در داشبورد</label>
          <input
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="luxury-input text-xs"
          />
        </div>

        <div className="grid grid-cols-2 gap-2.5">
          <div>
            <label className="mb-1.5 block text-[11px] text-[#A79EB9]">هدف تمرکز روزانه (دقیقه)</label>
            <input
              type="number"
              min={15}
              max={720}
              value={dailyFocusGoal}
              onChange={(e) => setDailyFocusGoal(Number(e.target.value))}
              className="luxury-input num-geo text-center text-xs"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-[11px] text-[#A79EB9]">هدف وظایف روزانه</label>
            <input
              type="number"
              min={1}
              max={50}
              value={dailyTaskGoal}
              onChange={(e) => setDailyTaskGoal(Number(e.target.value))}
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

      {/* NOTIFICATIONS & HAPTICS */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs font-bold text-[#F4F1F8]">
            <Bell className="h-4 w-4 text-[#B69AEF]" />
            <span>اعلان‌ها و یادآوری‌ها</span>
          </div>
          <span className="rounded-lg bg-[#241936] px-2 py-0.5 text-[10px] font-medium text-[#B69AEF]">
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
            <span className="text-[#F4F1F8]">یادآوری وظایف و پایان تمرکز</span>
            <button
              type="button"
              role="switch"
              aria-checked={settings.notificationsEnabled}
              onClick={() =>
                updateSettings({ notificationsEnabled: !settings.notificationsEnabled })
              }
              className={`relative h-6 w-11 rounded-full transition-colors ${
                settings.notificationsEnabled ? 'bg-[#8052D6]' : 'bg-[#241936]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[#F4F1F8] transition-all ${
                  settings.notificationsEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-[#F4F1F8]">
              <Volume2 className="h-3.5 w-3.5 text-[#A79EB9]" />
              صدای زنگ آرام تمرکز
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={settings.soundEnabled}
              onClick={() => updateSettings({ soundEnabled: !settings.soundEnabled })}
              className={`relative h-6 w-11 rounded-full transition-colors ${
                settings.soundEnabled ? 'bg-[#8052D6]' : 'bg-[#241936]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[#F4F1F8] transition-all ${
                  settings.soundEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>

          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-[#F4F1F8]">
              <Smartphone className="h-3.5 w-3.5 text-[#A79EB9]" />
              بازخورد لمسی (Haptics)
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={settings.hapticsEnabled}
              onClick={() => updateSettings({ hapticsEnabled: !settings.hapticsEnabled })}
              className={`relative h-6 w-11 rounded-full transition-colors ${
                settings.hapticsEnabled ? 'bg-[#8052D6]' : 'bg-[#241936]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[#F4F1F8] transition-all ${
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
              className="btn-ghost-luxury rounded-xl py-2 text-[11px] font-medium text-[#B69AEF]"
            >
              درخواست مجوز سیستم
            </button>
          )}
          <button
            type="button"
            onClick={handleTestNotification}
            className={`btn-ghost-luxury rounded-xl py-2 text-[11px] font-medium text-[#F4F1F8] ${
              permState === 'granted' ? 'col-span-2' : ''
            }`}
          >
            ارسال اعلان آزمایشی
          </button>
        </div>
      </LuxuryCard>

      {/* LOCAL DATA & PRIVACY */}
      <LuxuryCard className="space-y-3.5 p-4">
        <div className="flex items-center gap-2 text-xs font-bold text-[#F4F1F8]">
          <Database className="h-4 w-4 text-[#B69AEF]" />
          <span>ذخیره‌سازی آفلاین و پشتیبان‌گیری</span>
        </div>

        <div className="grid grid-cols-5 gap-1.5 rounded-2xl border border-[#B69AEF]/10 bg-[#0F0E15] p-2.5 text-center">
          <div>
            <Numeric value={dbCounts.tasks} variant="display" className="text-xl font-bold text-[#F4F1F8]" />
            <span className="mt-0.5 block text-[9px] text-[#A79EB9]">وظیفه</span>
          </div>
          <div>
            <Numeric value={dbCounts.habits} variant="display" className="text-xl font-bold text-[#B69AEF]" />
            <span className="mt-0.5 block text-[9px] text-[#A79EB9]">عادت</span>
          </div>
          <div>
            <Numeric value={dbCounts.logs} variant="display" className="text-xl font-bold text-[#F4F1F8]" />
            <span className="mt-0.5 block text-[9px] text-[#A79EB9]">ثبت عادت</span>
          </div>
          <div>
            <Numeric value={dbCounts.sessions} variant="display" className="text-xl font-bold text-[#B69AEF]" />
            <span className="mt-0.5 block text-[9px] text-[#A79EB9]">تمرکز</span>
          </div>
          <div>
            <Numeric value={dbCounts.goals} variant="display" className="text-xl font-bold text-[#F4F1F8]" />
            <span className="mt-0.5 block text-[9px] text-[#A79EB9]">هدف</span>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={handleExport}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[#F4F1F8]"
          >
            <Download className="h-3.5 w-3.5 text-[#B69AEF]" />
            <span>خروجی پشتیبان</span>
          </button>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="btn-ghost-luxury flex items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-medium text-[#F4F1F8]"
          >
            <Upload className="h-3.5 w-3.5 text-[#B69AEF]" />
            <span>بازیابی پشتیبان</span>
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

        <div className="space-y-2 border-t border-[#B69AEF]/10 pt-3">
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
            className="btn-ghost-luxury flex w-full items-center justify-center gap-1.5 rounded-xl py-2 text-[11px] font-medium text-[#A79EB9]"
          >
            <RefreshCw className="h-3.5 w-3.5" />
            <span>بارگذاری مجدد داده‌های نمونه</span>
          </button>

          {!confirmWipe ? (
            <button
              type="button"
              onClick={() => setConfirmWipe(true)}
              className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-[#D9778B]/25 bg-[#D9778B]/10 py-2 text-[11px] font-medium text-[#EB9BAB] transition hover:bg-[#D9778B]/20"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>پاک‌سازی کامل و شروع از فضای خالی</span>
            </button>
          ) : (
            <div className="flex items-center gap-2 rounded-xl border border-[#D9778B]/35 bg-[#D9778B]/12 p-2.5">
              <span className="flex-1 text-[11px] text-[#F4F1F8]">
                تمام داده‌ها حذف شوند؟
              </span>
              <button
                type="button"
                onClick={async () => {
                  await clearAllUserData();
                  setConfirmWipe(false);
                  pushToast({ title: 'تمام داده‌ها پاک شدند', tone: 'default' });
                }}
                className="rounded-lg bg-[#D9778B] px-3 py-1 text-[11px] font-bold text-[#09090D]"
              >
                بله، پاک کن
              </button>
              <button
                type="button"
                onClick={() => setConfirmWipe(false)}
                className="rounded-lg bg-[#15141B] px-2.5 py-1 text-[11px] text-[#A79EB9]"
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
            <p className="font-brand text-xs font-bold text-[#F4F1F8]">KAIROS v1.0.0</p>
            <p className="mt-0.5 flex items-center gap-1 text-[10.5px] text-[#A79EB9]">
              <ShieldCheck className="h-3.5 w-3.5 text-[#8052D6]" />
              <span>
                100% آفلاین •{' '}
                {isNativeAndroid() ? 'اجرای بومی اندروید' : 'آماده نصب PWA و اندروید'}
              </span>
            </p>
          </div>
        </div>
      </LuxuryCard>
    </div>
  );
}
