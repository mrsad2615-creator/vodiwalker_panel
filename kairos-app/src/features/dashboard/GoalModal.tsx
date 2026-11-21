import { useEffect, useState } from 'react';
import { Trash2 } from '@/components/ui/icons';
import type { Goal, TaskCategory } from '@/types/domain';
import { createGoal, deleteGoal, updateGoal } from '@/lib/db';
import { CATEGORY_META } from '@/lib/format';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { JalaliDatePicker } from '@/components/ui/JalaliDatePicker';
import { formatJalaaliFull } from '@/lib/date';
import { Numeric } from '@/components/ui/Numeric';
import { NumericInput } from '@/components/ui/NumericInput';
import { useApp } from '@/context/AppContext';

interface GoalModalProps {
  open: boolean;
  goal?: Goal | null;
  onClose: () => void;
}

export function GoalModal({ open, goal, onClose }: GoalModalProps) {
  const { pushToast } = useApp();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<TaskCategory>('work');
  const [currentValue, setCurrentValue] = useState(0);
  const [targetValue, setTargetValue] = useState(10);
  const [unit, setUnit] = useState('واحد');
  const [deadline, setDeadline] = useState<string | null>(null);
  const [showDatePicker, setShowDatePicker] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (goal) {
      setTitle(goal.title);
      setDescription(goal.description);
      setCategory(goal.category);
      setCurrentValue(goal.currentValue);
      setTargetValue(goal.targetValue);
      setUnit(goal.unit);
      setDeadline(goal.deadline);
    } else {
      setTitle('');
      setDescription('');
      setCategory('work');
      setCurrentValue(0);
      setTargetValue(10);
      setUnit('ساعت');
      setDeadline(null);
    }
    setShowDatePicker(false);
  }, [open, goal]);

  const handleSave = async () => {
    if (!title.trim()) {
      pushToast({ title: 'عنوان هدف الزامی است', tone: 'warning' });
      return;
    }
    const safeTarget = Math.max(1, Number(targetValue) || 1);
    const safeCurrent = Math.max(0, Number(currentValue) || 0);

    if (goal) {
      await updateGoal(goal.id, {
        title: title.trim(),
        description: description.trim(),
        category,
        currentValue: safeCurrent,
        targetValue: safeTarget,
        unit: unit.trim() || 'واحد',
        deadline,
      });
      pushToast({ title: 'هدف به‌روزرسانی شد', tone: 'success' });
    } else {
      await createGoal({
        title: title.trim(),
        description: description.trim(),
        category,
        currentValue: safeCurrent,
        targetValue: safeTarget,
        unit: unit.trim() || 'واحد',
        deadline,
      });
      pushToast({ title: 'هدف جدید ایجاد شد', tone: 'success' });
    }
    onClose();
  };

  const handleDelete = async () => {
    if (!goal) return;
    await deleteGoal(goal.id);
    pushToast({ title: 'هدف حذف شد', tone: 'default' });
    onClose();
  };

  return (
    <ModalSheet
      open={open}
      onClose={onClose}
      title={goal ? 'ویرایش هدف شخصی' : 'هدف شخصی جدید'}
      subtitle="اهداف کلیدی شما با پیشرفت دقیق در داشبورد دنبال می‌شوند"
      footer={
        <div className="flex items-center gap-2.5">
          {goal && (
            <button
              type="button"
              onClick={handleDelete}
              className="flex h-11 w-11 items-center justify-center rounded-xl border border-[var(--danger)]/25 bg-[var(--danger)]/10 text-[var(--danger-fg)] transition hover:bg-[var(--danger)]/20"
              aria-label="حذف هدف"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
          <button
            type="button"
            onClick={handleSave}
            className="btn-royal flex-1 rounded-xl py-2.5 text-sm font-semibold"
          >
            {goal ? 'ذخیره تغییرات' : 'ایجاد هدف'}
          </button>
        </div>
      }
    >
      <div className="space-y-4">
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">عنوان هدف</label>
          <input
            type="text"
            dir="auto"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="مثلاً: مطالعه 12 کتاب تخصصی"
            className="luxury-input"
          />
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">توضیحات</label>
          <textarea
            rows={2}
            dir="auto"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="انگیزه یا معیار تحقق هدف..."
            className="luxury-input resize-none"
          />
        </div>

        <div className="grid grid-cols-3 gap-2.5">
          <div>
            <label className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">پیشرفت فعلی</label>
            <NumericInput
              min={0}
              max={99999}
              fallback={0}
              value={currentValue}
              onChange={setCurrentValue}
              className="luxury-input num-geo text-center"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">مقدار هدف</label>
            <NumericInput
              min={1}
              max={99999}
              fallback={10}
              value={targetValue}
              onChange={setTargetValue}
              className="luxury-input num-geo text-center"
            />
          </div>
          <div>
            <label className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">واحد</label>
            <input
              type="text"
              dir="auto"
              value={unit}
              onChange={(e) => setUnit(e.target.value)}
              placeholder="کتاب / ساعت"
              className="luxury-input text-center"
            />
          </div>
        </div>

        <div>
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">دسته‌بندی</label>
          <div className="grid grid-cols-3 gap-2">
            {(Object.keys(CATEGORY_META) as TaskCategory[]).map((cat) => (
              <button
                key={cat}
                type="button"
                onClick={() => setCategory(cat)}
                className={`rounded-xl border px-2.5 py-2 text-xs font-medium transition ${
                  category === cat
                    ? 'border-[var(--accent)] bg-[var(--surface-3)] text-[var(--text)]'
                    : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-2)]'
                }`}
              >
                {CATEGORY_META[cat].label}
              </button>
            ))}
          </div>
        </div>

        <div>
          <div className="mb-1.5 flex items-center justify-between">
            <label className="text-xs font-medium text-[var(--text-2)]">مهلت هدف (اختیاری)</label>
            <button
              type="button"
              onClick={() => setShowDatePicker((p) => !p)}
              className="text-xs font-medium text-[var(--accent-fg)]"
            >
              {deadline ? (
                <span>
                  <Numeric value={formatJalaaliFull(deadline).day} /> {formatJalaaliFull(deadline).month}{' '}
                  <Numeric value={formatJalaaliFull(deadline).year} />
                </span>
              ) : (
                'انتخاب تاریخ شمسی'
              )}
            </button>
          </div>
          {showDatePicker && (
            <JalaliDatePicker
              value={deadline}
              onChange={(dk) => {
                setDeadline(dk);
                setShowDatePicker(false);
              }}
            />
          )}
        </div>
      </div>
    </ModalSheet>
  );
}
