import { useEffect, useState } from 'react';
import { Trash2 } from 'lucide-react';
import type {
  Habit,
  HabitIconName,
  HabitScheduleType,
  HabitTrackingMode,
} from '@/types/domain';
import { createHabit, deleteHabit, updateHabit } from '@/lib/db';
import { getTodayKey, PERSIAN_WEEKDAYS_SHORT } from '@/lib/date';
import {
  HABIT_ACCENT_SWATCHES,
  HABIT_ICON_OPTIONS,
  HabitIcon,
} from '@/components/ui/KairosMark';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { NumericInput } from '@/components/ui/NumericInput';
import { useApp } from '@/context/AppContext';

interface HabitModalProps {
  open: boolean;
  habit?: Habit | null;
  onClose: () => void;
  onDeleted?: () => void;
}

export function HabitModal({ open, habit, onClose, onDeleted }: HabitModalProps) {
  const { pushToast } = useApp();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [icon, setIcon] = useState<HabitIconName>('brain');
  const [accentColor, setAccentColor] = useState<string>(HABIT_ACCENT_SWATCHES[0]);
  const [scheduleType, setScheduleType] = useState<HabitScheduleType>('daily');
  const [scheduledDays, setScheduledDays] = useState<number[]>([0, 1, 2, 3, 4, 5, 6]);
  const [weeklyTarget, setWeeklyTarget] = useState(5);
  const [trackingMode, setTrackingMode] = useState<HabitTrackingMode>('boolean');
  const [targetValue, setTargetValue] = useState(30);
  const [unit, setUnit] = useState('دقیقه');

  useEffect(() => {
    if (!open) return;
    if (habit) {
      setName(habit.name);
      setDescription(habit.description);
      setIcon(habit.icon);
      setAccentColor(habit.accentColor);
      setScheduleType(habit.scheduleType);
      setScheduledDays([...habit.scheduledDays]);
      setWeeklyTarget(habit.weeklyTarget);
      setTrackingMode(habit.trackingMode);
      setTargetValue(habit.targetValue);
      setUnit(habit.unit);
    } else {
      setName('');
      setDescription('');
      setIcon('brain');
      setAccentColor(HABIT_ACCENT_SWATCHES[0]);
      setScheduleType('daily');
      setScheduledDays([0, 1, 2, 3, 4, 5, 6]);
      setWeeklyTarget(5);
      setTrackingMode('boolean');
      setTargetValue(30);
      setUnit('دقیقه');
    }
  }, [open, habit]);

  const toggleDay = (dayIdx: number) => {
    setScheduledDays((prev) => {
      if (prev.includes(dayIdx)) {
        if (prev.length === 1) return prev;
        return prev.filter((d) => d !== dayIdx);
      }
      return [...prev, dayIdx].sort((a, b) => a - b);
    });
  };

  const handleSave = async () => {
    if (!name.trim()) {
      pushToast({ title: 'نام عادت را وارد کنید', tone: 'warning' });
      return;
    }

    const finalTarget = trackingMode === 'boolean' ? 1 : Math.max(1, Number(targetValue) || 1);
    const finalUnit = trackingMode === 'boolean' ? 'بار' : unit.trim() || 'واحد';
    const finalDays =
      scheduleType === 'specific_days' ? scheduledDays : [0, 1, 2, 3, 4, 5, 6];

    if (habit) {
      await updateHabit(habit.id, {
        name: name.trim(),
        description: description.trim(),
        icon,
        accentColor,
        scheduleType,
        scheduledDays: finalDays,
        weeklyTarget: scheduleType === 'weekly_target' ? weeklyTarget : finalDays.length,
        trackingMode,
        targetValue: finalTarget,
        unit: finalUnit,
      });
      pushToast({ title: 'عادت به‌روزرسانی شد', tone: 'success' });
    } else {
      await createHabit({
        name: name.trim(),
        description: description.trim(),
        icon,
        accentColor,
        scheduleType,
        scheduledDays: finalDays,
        weeklyTarget: scheduleType === 'weekly_target' ? weeklyTarget : finalDays.length,
        trackingMode,
        targetValue: finalTarget,
        unit: finalUnit,
        reminderTime: null,
        startDate: getTodayKey(),
      });
      pushToast({ title: 'عادت جدید ساخته شد', tone: 'success' });
    }
    onClose();
  };

  const handleDelete = async () => {
    if (!habit) return;
    await deleteHabit(habit.id);
    pushToast({ title: 'عادت و تاریخچه آن حذف شد', tone: 'default' });
    onClose();
    onDeleted?.();
  };

  return (
    <ModalSheet
      open={open}
      onClose={onClose}
      title={habit ? 'ویرایش عادت' : 'ایجاد عادت جدید'}
      subtitle="ساختار دقیق روزانه یا هفتگی برای ثبت در تقویم حرارتی 365 روزه"
      footer={
        <div className="flex items-center gap-2.5">
          {habit && (
            <button
              type="button"
              onClick={handleDelete}
              aria-label="حذف عادت"
              className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#D9778B]/25 bg-[#D9778B]/10 text-[#EB9BAB] hover:bg-[#D9778B]/20"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
          <button
            type="button"
            onClick={handleSave}
            className="btn-royal flex-1 rounded-xl py-2.5 text-sm font-semibold"
          >
            {habit ? 'ذخیره تغییرات' : 'ایجاد عادت'}
          </button>
        </div>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">نام عادت</label>
          <input
            type="text"
            dir="auto"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="مثلاً: مطالعه روزانه، مدیتیشن، تمرین قدرتی"
            className="luxury-input"
          />
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">توضیحات</label>
          <textarea
            rows={2}
            dir="auto"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="معیار دقیق انجام عادت..."
            className="luxury-input resize-none"
          />
        </div>

        {/* Icon Picker */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">آیکون اختصاصی</label>
          <div className="grid grid-cols-8 gap-1.5">
            {HABIT_ICON_OPTIONS.map((opt) => (
              <button
                key={opt.name}
                type="button"
                title={opt.label}
                onClick={() => setIcon(opt.name)}
                className={`flex h-9 items-center justify-center rounded-xl border transition ${
                  icon === opt.name
                    ? 'border-[#B69AEF] bg-[#241936]'
                    : 'border-[#B69AEF]/10 bg-[#100F16] hover:border-[#B69AEF]/28'
                }`}
              >
                <HabitIcon name={opt.name} color={icon === opt.name ? accentColor : '#A79EB9'} />
              </button>
            ))}
          </div>
        </div>

        {/* Accent Swatch */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">رنگ شاخص</label>
          <div className="flex items-center gap-2.5">
            {HABIT_ACCENT_SWATCHES.map((hex) => (
              <button
                key={hex}
                type="button"
                onClick={() => setAccentColor(hex)}
                className={`h-7 w-7 rounded-full transition ${
                  accentColor === hex ? 'scale-110 ring-2 ring-[#F4F1F8] ring-offset-2 ring-offset-[#15141B]' : ''
                }`}
                style={{ backgroundColor: hex }}
                aria-label={hex}
              />
            ))}
          </div>
        </div>

        {/* Schedule Type */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">برنامه زمانی</label>
          <div className="grid grid-cols-3 gap-2">
            {(
              [
                { id: 'daily', label: 'هر روز' },
                { id: 'specific_days', label: 'روزهای خاص' },
                { id: 'weekly_target', label: 'هدف هفتگی' },
              ] as const
            ).map((sc) => (
              <button
                key={sc.id}
                type="button"
                onClick={() => setScheduleType(sc.id)}
                className={`rounded-xl border py-2 text-xs font-medium transition ${
                  scheduleType === sc.id
                    ? 'border-[#8052D6] bg-[#241936] text-[#F4F1F8]'
                    : 'border-[#B69AEF]/10 bg-[#100F16] text-[#A79EB9]'
                }`}
              >
                {sc.label}
              </button>
            ))}
          </div>

          {scheduleType === 'specific_days' && (
            <div className="mt-2.5 grid grid-cols-7 gap-1.5">
              {PERSIAN_WEEKDAYS_SHORT.map((w, idx) => {
                const active = scheduledDays.includes(idx);
                return (
                  <button
                    key={w}
                    type="button"
                    onClick={() => toggleDay(idx)}
                    className={`h-9 rounded-xl border text-xs font-semibold transition ${
                      active
                        ? 'border-[#B69AEF]/50 bg-[#8052D6] text-[#F4F1F8]'
                        : 'border-[#B69AEF]/12 bg-[#100F16] text-[#6E6680]'
                    }`}
                  >
                    {w}
                  </button>
                );
              })}
            </div>
          )}

          {scheduleType === 'weekly_target' && (
            <div className="mt-2.5 flex items-center justify-between rounded-xl border border-[#B69AEF]/12 bg-[#100F16] px-3.5 py-2.5 text-xs">
              <span className="text-[#A79EB9]">تعداد روز هدف در هفته:</span>
              <NumericInput
                min={1}
                max={7}
                fallback={5}
                value={weeklyTarget}
                onChange={setWeeklyTarget}
                className="num-geo w-14 rounded-lg border border-[#B69AEF]/20 bg-[#15141B] py-1 text-center text-[#F4F1F8] outline-none"
              />
            </div>
          )}
        </div>

        {/* Tracking Mode */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">نوع ثبت پیشرفت</label>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setTrackingMode('boolean')}
              className={`rounded-xl border py-2 text-xs font-medium transition ${
                trackingMode === 'boolean'
                  ? 'border-[#8052D6] bg-[#241936] text-[#F4F1F8]'
                  : 'border-[#B69AEF]/10 bg-[#100F16] text-[#A79EB9]'
              }`}
            >
              تکمیلی (انجام شد / نشد)
            </button>
            <button
              type="button"
              onClick={() => setTrackingMode('quantitative')}
              className={`rounded-xl border py-2 text-xs font-medium transition ${
                trackingMode === 'quantitative'
                  ? 'border-[#8052D6] bg-[#241936] text-[#F4F1F8]'
                  : 'border-[#B69AEF]/10 bg-[#100F16] text-[#A79EB9]'
              }`}
            >
              مقداری (دقیقه / صفحه / لیوان)
            </button>
          </div>

          {trackingMode === 'quantitative' && (
            <div className="mt-2.5 grid grid-cols-2 gap-2.5">
              <div>
                <label className="mb-1 block text-[11px] text-[#A79EB9]">هدف روزانه</label>
                <NumericInput
                  min={1}
                  max={9999}
                  fallback={30}
                  value={targetValue}
                  onChange={setTargetValue}
                  className="luxury-input num-geo text-center text-xs"
                />
              </div>
              <div>
                <label className="mb-1 block text-[11px] text-[#A79EB9]">واحد اندازه‌گیری</label>
                <input
                  type="text"
                  dir="auto"
                  value={unit}
                  onChange={(e) => setUnit(e.target.value)}
                  placeholder="دقیقه / صفحه / لیوان"
                  className="luxury-input text-center text-xs"
                />
              </div>
            </div>
          )}
        </div>
      </div>
    </ModalSheet>
  );
}
