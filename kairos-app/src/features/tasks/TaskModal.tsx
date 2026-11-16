import { useEffect, useState } from 'react';
import { Bell, Calendar, Check, Clock, Plus, Repeat, Tag, Trash2, X } from 'lucide-react';
import type {
  EisenhowerQuadrant,
  RecurrenceRule,
  Subtask,
  TaskCategory,
  TaskPriority,
  TaskStatus,
} from '@/types/domain';
import { useApp } from '@/context/AppContext';
import { archiveTask, createTask, deleteTask, setTaskStatus, updateTask } from '@/lib/db';
import {
  CATEGORY_META,
  createId,
  PRIORITY_META,
  QUADRANT_META,
  RECURRENCE_LABELS,
} from '@/lib/format';
import { formatJalaaliFull } from '@/lib/date';
import { triggerHaptic } from '@/lib/native';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { JalaliDatePicker } from '@/components/ui/JalaliDatePicker';
import { Numeric } from '@/components/ui/Numeric';

export function TaskModal() {
  const { taskModal, closeTaskModal, pushToast, settings } = useApp();
  const editing = taskModal.task;

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState<string | null>(null);
  const [dueTime, setDueTime] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('medium');
  const [quadrant, setQuadrant] = useState<EisenhowerQuadrant>('q2');
  const [category, setCategory] = useState<TaskCategory>('work');
  const [status, setStatus] = useState<TaskStatus>('todo');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [subtasks, setSubtasks] = useState<Subtask[]>([]);
  const [subtaskInput, setSubtaskInput] = useState('');
  const [recurrence, setRecurrence] = useState<RecurrenceRule>('none');
  const [reminderEnabled, setReminderEnabled] = useState(false);
  const [reminderOffsetMin, setReminderOffsetMin] = useState(15);
  const [showDatePicker, setShowDatePicker] = useState(false);

  useEffect(() => {
    if (!taskModal.open) return;
    if (editing) {
      setTitle(editing.title);
      setDescription(editing.description);
      setDueDate(editing.dueDate);
      setDueTime(editing.dueTime ?? '');
      setPriority(editing.priority);
      setQuadrant(editing.quadrant);
      setCategory(editing.category);
      setStatus(editing.status);
      setTags([...editing.tags]);
      setSubtasks(editing.subtasks.map((s) => ({ ...s })));
      setRecurrence(editing.recurrence);
      setReminderEnabled(editing.reminderEnabled);
      setReminderOffsetMin(editing.reminderOffsetMin);
    } else {
      const defQ = taskModal.defaultQuadrant ?? 'q2';
      setTitle('');
      setDescription('');
      setDueDate(taskModal.defaultDueDate ?? null);
      setDueTime('');
      setQuadrant(defQ);
      setPriority(defQ === 'q1' ? 'high' : defQ === 'q4' ? 'low' : 'medium');
      setCategory('work');
      setStatus('todo');
      setTags([]);
      setSubtasks([]);
      setRecurrence('none');
      setReminderEnabled(false);
      setReminderOffsetMin(15);
    }
    setTagInput('');
    setSubtaskInput('');
    setShowDatePicker(false);
  }, [taskModal.open, editing, taskModal.defaultDueDate, taskModal.defaultQuadrant]);

  const handleAddTag = () => {
    const clean = tagInput.trim().replace(/^#/, '');
    if (!clean || tags.includes(clean)) {
      setTagInput('');
      return;
    }
    setTags([...tags, clean]);
    setTagInput('');
  };

  const handleAddSubtask = () => {
    const clean = subtaskInput.trim();
    if (!clean) return;
    setSubtasks([...subtasks, { id: createId('sub'), title: clean, completed: false }]);
    setSubtaskInput('');
  };

  const handleSave = async () => {
    if (!title.trim()) {
      pushToast({ title: 'عنوان وظیفه را وارد کنید', tone: 'warning' });
      return;
    }
    const cleanTime = dueTime.trim() ? dueTime.trim() : null;

    if (editing) {
      const statusChanged = editing.status !== status;
      await updateTask(editing.id, {
        title: title.trim(),
        description: description.trim(),
        dueDate,
        dueTime: cleanTime,
        priority,
        quadrant,
        category,
        tags,
        subtasks,
        recurrence,
        reminderEnabled,
        reminderOffsetMin,
      });
      if (statusChanged) {
        await setTaskStatus(editing.id, status);
      }
      void triggerHaptic('success', settings.hapticsEnabled);
      pushToast({ title: 'تغییرات وظیفه ذخیره شد', tone: 'success' });
    } else {
      await createTask({
        title: title.trim(),
        description: description.trim(),
        dueDate,
        dueTime: cleanTime,
        priority,
        quadrant,
        category,
        tags,
        status,
        subtasks,
        recurrence,
        reminderEnabled,
        reminderOffsetMin,
      });
      void triggerHaptic('success', settings.hapticsEnabled);
      pushToast({ title: 'وظیفه جدید ایجاد شد', tone: 'success' });
    }
    closeTaskModal();
  };

  const handleDelete = async () => {
    if (!editing) return;
    await deleteTask(editing.id);
    pushToast({ title: 'وظیفه حذف شد', tone: 'default' });
    closeTaskModal();
  };

  const handleArchiveToggle = async () => {
    if (!editing) return;
    await archiveTask(editing.id, !editing.archived);
    pushToast({
      title: editing.archived ? 'وظیفه از آرشیو بازیابی شد' : 'وظیفه به آرشیو منتقل شد',
      tone: 'default',
    });
    closeTaskModal();
  };

  return (
    <ModalSheet
      open={taskModal.open}
      onClose={closeTaskModal}
      title={editing ? 'ویرایش وظیفه' : 'افزودن وظیفه جدید'}
      subtitle="سریع، دقیق و هماهنگ با ماتریس آیزنهاور و تقویم شمسی"
      footer={
        <div className="flex items-center gap-2">
          {editing && (
            <>
              <button
                type="button"
                onClick={handleDelete}
                aria-label="حذف وظیفه"
                className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#D9778B]/25 bg-[#D9778B]/10 text-[#EB9BAB] transition hover:bg-[#D9778B]/20"
              >
                <Trash2 className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={handleArchiveToggle}
                className="btn-ghost-luxury rounded-xl px-3 py-2.5 text-xs font-medium text-[#A79EB9]"
              >
                {editing.archived ? 'بازیابی' : 'آرشیو'}
              </button>
            </>
          )}
          <button
            type="button"
            onClick={handleSave}
            className="btn-royal flex-1 rounded-xl py-2.5 text-sm font-semibold"
          >
            {editing ? 'ذخیره تغییرات' : 'ثبت وظیفه'}
          </button>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Title */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">عنوان وظیفه</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                void handleSave();
              }
            }}
            placeholder="چه کاری باید انجام شود؟"
            className="luxury-input font-medium"
            autoFocus
          />
        </div>

        {/* Description */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">توضیحات</label>
          <textarea
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="جزئیات، نکات کلیدی یا لینک‌های مرتبط..."
            className="luxury-input resize-none"
          />
        </div>

        {/* Date & Time */}
        <div className="grid grid-cols-2 gap-2.5">
          <div>
            <label className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[#A79EB9]">
              <Calendar className="h-3.5 w-3.5 text-[#8052D6]" />
              <span>تاریخ (شمسی)</span>
            </label>
            <button
              type="button"
              onClick={() => setShowDatePicker((p) => !p)}
              className="luxury-input flex items-center justify-between text-right text-xs"
            >
              {dueDate ? (
                <span className="text-[#F4F1F8]">
                  <Numeric value={formatJalaaliFull(dueDate).day} /> {formatJalaaliFull(dueDate).month}{' '}
                  <Numeric value={formatJalaaliFull(dueDate).year} className="text-[#A79EB9]" />
                </span>
              ) : (
                <span className="text-[#6E6680]">بدون تاریخ</span>
              )}
            </button>
          </div>

          <div>
            <label className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[#A79EB9]">
              <Clock className="h-3.5 w-3.5 text-[#8052D6]" />
              <span>ساعت</span>
            </label>
            <input
              type="time"
              value={dueTime}
              onChange={(e) => setDueTime(e.target.value)}
              className="luxury-input num-geo text-center text-xs"
            />
          </div>
        </div>

        {showDatePicker && (
          <JalaliDatePicker
            value={dueDate}
            onChange={(dk) => {
              setDueDate(dk);
              setShowDatePicker(false);
            }}
          />
        )}

        {/* Status */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">وضعیت اجرا</label>
          <div className="grid grid-cols-3 gap-2">
            {(
              [
                { id: 'todo', label: 'انجام نشده' },
                { id: 'in_progress', label: 'در حال انجام' },
                { id: 'completed', label: 'تکمیل شده' },
              ] as const
            ).map((st) => (
              <button
                key={st.id}
                type="button"
                onClick={() => setStatus(st.id)}
                className={`rounded-xl border py-2 text-xs font-medium transition ${
                  status === st.id
                    ? 'border-[#8052D6] bg-[#241936] text-[#F4F1F8]'
                    : 'border-[#B69AEF]/10 bg-[#100F16] text-[#A79EB9]'
                }`}
              >
                {st.label}
              </button>
            ))}
          </div>
        </div>

        {/* Eisenhower Quadrant (4-box Matrix) */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">
            ماتریس اولویت آیزنهاور
          </label>
          <div className="grid grid-cols-2 gap-2">
            {(['q1', 'q2', 'q3', 'q4'] as EisenhowerQuadrant[]).map((q) => {
              const meta = QUADRANT_META[q];
              const active = quadrant === q;
              return (
                <button
                  key={q}
                  type="button"
                  onClick={() => {
                    setQuadrant(q);
                    if (q === 'q1') setPriority('high');
                    else if (q === 'q4') setPriority('low');
                    else setPriority('medium');
                  }}
                  className={`rounded-xl border px-3 py-2 text-right transition ${
                    active
                      ? 'border-[#B69AEF]/45 bg-[#241936]'
                      : 'border-[#B69AEF]/10 bg-[#100F16] hover:border-[#B69AEF]/22'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-[#F4F1F8]">{meta.title}</span>
                    <span
                      className="num-geo text-[10px] font-bold"
                      style={{ color: meta.accent }}
                    >
                      {meta.code}
                    </span>
                  </div>
                  <p className="mt-0.5 text-[10px] text-[#A79EB9]">{meta.action}</p>
                </button>
              );
            })}
          </div>
        </div>

        {/* Priority & Category */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">سطح اولویت</label>
            <div className="flex gap-1.5">
              {(['high', 'medium', 'low'] as TaskPriority[]).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPriority(p)}
                  className={`flex-1 rounded-xl border py-2 text-[11px] font-medium transition ${
                    priority === p
                      ? 'border-[#8052D6] bg-[#241936] text-[#F4F1F8]'
                      : 'border-[#B69AEF]/10 bg-[#100F16] text-[#A79EB9]'
                  }`}
                >
                  {PRIORITY_META[p].label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">دسته‌بندی</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as TaskCategory)}
              className="luxury-input py-2 text-xs"
            >
              {(Object.keys(CATEGORY_META) as TaskCategory[]).map((cat) => (
                <option key={cat} value={cat} className="bg-[#15141B] text-[#F4F1F8]">
                  {CATEGORY_META[cat].label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Subtasks */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[#A79EB9]">زیرکارها</label>
          <div className="space-y-1.5">
            {subtasks.map((sub) => (
              <div
                key={sub.id}
                className="flex items-center gap-2 rounded-xl border border-[#B69AEF]/10 bg-[#100F16] px-3 py-2"
              >
                <button
                  type="button"
                  onClick={() =>
                    setSubtasks(
                      subtasks.map((s) =>
                        s.id === sub.id ? { ...s, completed: !s.completed } : s
                      )
                    )
                  }
                  className={`flex h-4.5 w-4.5 items-center justify-center rounded border ${
                    sub.completed
                      ? 'border-[#8052D6] bg-[#8052D6] text-[#F4F1F8]'
                      : 'border-[#B69AEF]/30'
                  }`}
                >
                  {sub.completed && <Check className="h-3 w-3" />}
                </button>
                <span
                  className={`flex-1 text-xs ${
                    sub.completed ? 'text-[#6E6680] line-through' : 'text-[#F4F1F8]'
                  }`}
                >
                  {sub.title}
                </span>
                <button
                  type="button"
                  onClick={() => setSubtasks(subtasks.filter((s) => s.id !== sub.id))}
                  className="text-[#6E6680] hover:text-[#EB9BAB]"
                  aria-label="حذف زیرکار"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}

            <div className="flex gap-2">
              <input
                type="text"
                value={subtaskInput}
                onChange={(e) => setSubtaskInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleAddSubtask();
                  }
                }}
                placeholder="افزودن زیرکار..."
                className="luxury-input py-2 text-xs"
              />
              <button
                type="button"
                onClick={handleAddSubtask}
                className="btn-ghost-luxury shrink-0 rounded-xl px-3 text-xs text-[#B69AEF]"
              >
                <Plus className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Tags */}
        <div>
          <label className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[#A79EB9]">
            <Tag className="h-3.5 w-3.5 text-[#8052D6]" />
            <span>برچسب‌ها</span>
          </label>
          <div className="mb-2 flex flex-wrap gap-1.5">
            {tags.map((t) => (
              <span
                key={t}
                className="inline-flex items-center gap-1 rounded-full border border-[#B69AEF]/22 bg-[#241936] px-2.5 py-0.5 text-[11px] text-[#B69AEF]"
              >
                #{t}
                <button
                  type="button"
                  onClick={() => setTags(tags.filter((x) => x !== t))}
                  className="hover:text-[#F4F1F8]"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAddTag();
                }
              }}
              placeholder="برچسب جدید + Enter"
              className="luxury-input py-2 text-xs"
            />
            <button
              type="button"
              onClick={handleAddTag}
              className="btn-ghost-luxury shrink-0 rounded-xl px-3 text-xs text-[#B69AEF]"
            >
              افزودن
            </button>
          </div>
        </div>

        {/* Recurrence & Reminder */}
        <div className="grid grid-cols-1 gap-3 rounded-2xl border border-[#B69AEF]/10 bg-[#100F16] p-3.5">
          <div className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-1.5 text-xs font-medium text-[#F4F1F8]">
              <Repeat className="h-3.5 w-3.5 text-[#B69AEF]" />
              تکرار وظیفه
            </span>
            <select
              value={recurrence}
              onChange={(e) => setRecurrence(e.target.value as RecurrenceRule)}
              className="rounded-xl border border-[#B69AEF]/15 bg-[#15141B] px-3 py-1.5 text-xs text-[#F4F1F8] outline-none"
            >
              {(Object.keys(RECURRENCE_LABELS) as RecurrenceRule[]).map((r) => (
                <option key={r} value={r} className="bg-[#15141B]">
                  {RECURRENCE_LABELS[r]}
                </option>
              ))}
            </select>
          </div>

          <div className="h-px bg-[#B69AEF]/10" />

          <div className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-1.5 text-xs font-medium text-[#F4F1F8]">
              <Bell className="h-3.5 w-3.5 text-[#B69AEF]" />
              یادآوری و اعلان
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={reminderEnabled}
              onClick={() => setReminderEnabled((v) => !v)}
              className={`relative h-6 w-11 rounded-full transition-colors ${
                reminderEnabled ? 'bg-[#8052D6]' : 'bg-[#241936]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[#F4F1F8] transition-all ${
                  reminderEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>

          {reminderEnabled && (
            <div className="flex items-center justify-between text-[11px] text-[#A79EB9]">
              <span>زمان اعلام قبل از موعد:</span>
              <select
                value={reminderOffsetMin}
                onChange={(e) => setReminderOffsetMin(Number(e.target.value))}
                className="rounded-lg border border-[#B69AEF]/15 bg-[#15141B] px-2.5 py-1 text-xs text-[#F4F1F8] outline-none"
              >
                <option value={0}>دقیقاً در ساعت موعد</option>
                <option value={10}>10 دقیقه قبل</option>
                <option value={15}>15 دقیقه قبل</option>
                <option value={30}>30 دقیقه قبل</option>
                <option value={60}>1 ساعت قبل</option>
              </select>
            </div>
          )}
        </div>
      </div>
    </ModalSheet>
  );
}
