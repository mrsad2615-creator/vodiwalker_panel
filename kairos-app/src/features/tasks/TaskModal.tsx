import { useEffect, useState } from 'react';
import { Bell, Calendar, Check, Clock, Plus, Repeat, Tag, Trash2, X } from '@/components/ui/icons';
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
import { useGoals, useProjects } from '@/lib/queries';

export function TaskModal() {
  const { taskModal, closeTaskModal, pushToast, settings } = useApp();
  const editing = taskModal.task;
  // A task can be the work that moves a goal. Optional on purpose: most tasks
  // are just today, and forcing a goal on them would be a second inbox.
  const goals = useGoals();
  const openGoals = goals.filter((goal) => !goal.completed);
  const projects = useProjects().filter((project) => project.status === 'active');

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
  const [goalId, setGoalId] = useState<string>('');
  const [projectId, setProjectId] = useState<string>('');
  const [estimateMin, setEstimateMin] = useState<string>('');

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
      setGoalId(editing.goalId ?? '');
      setProjectId(editing.projectId ?? '');
      setEstimateMin(editing.estimateMin ? String(editing.estimateMin) : '');
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
      setGoalId('');
      setProjectId('');
      setEstimateMin('');
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
        goalId: goalId || null,
        projectId: projectId || null,
        estimateMin: estimateMin ? Math.max(5, Number(estimateMin) || 0) : null,
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
        goalId: goalId || null,
        projectId: projectId || null,
        estimateMin: estimateMin ? Math.max(5, Number(estimateMin) || 0) : null,
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
                className="flex h-11 w-11 items-center justify-center rounded-xl border border-[var(--danger)]/25 bg-[var(--danger)]/10 text-[var(--danger-fg)] transition hover:bg-[var(--danger)]/20"
              >
                <Trash2 className="h-4 w-4" />
              </button>
              <button
                type="button"
                onClick={handleArchiveToggle}
                className="btn-ghost-luxury rounded-xl px-3 py-2.5 text-xs font-medium text-[var(--text-2)]"
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
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">عنوان وظیفه</label>
          <input
            type="text"
            dir="auto"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
                e.preventDefault();
                void handleSave();
              }
            }}
            placeholder="چه کاری باید انجام شود؟"
            className="luxury-input font-medium"
          />
        </div>

        {/* Description */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">توضیحات</label>
          <textarea
            rows={2}
            dir="auto"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="جزئیات، نکات کلیدی یا لینک‌های مرتبط..."
            className="luxury-input resize-none"
          />
        </div>

        {/* Date & Time */}
        <div className="grid grid-cols-2 gap-2.5">
          <div>
            <label className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[var(--text-2)]">
              <Calendar className="h-3.5 w-3.5 text-[var(--accent)]" />
              <span>تاریخ (شمسی)</span>
            </label>
            <button
              type="button"
              onClick={() => setShowDatePicker((p) => !p)}
              className="luxury-input flex items-center justify-between text-right text-xs"
            >
              {dueDate ? (
                <span className="text-[var(--text)]">
                  <Numeric value={formatJalaaliFull(dueDate).day} /> {formatJalaaliFull(dueDate).month}{' '}
                  <Numeric value={formatJalaaliFull(dueDate).year} className="text-[var(--text-2)]" />
                </span>
              ) : (
                <span className="text-[var(--text-3)]">بدون تاریخ</span>
              )}
            </button>
          </div>

          <div>
            <label className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[var(--text-2)]">
              <Clock className="h-3.5 w-3.5 text-[var(--accent)]" />
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
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">وضعیت اجرا</label>
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
                    ? 'border-[var(--accent)] bg-[var(--surface-3)] text-[var(--text)]'
                    : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-2)]'
                }`}
              >
                {st.label}
              </button>
            ))}
          </div>
        </div>

        {/* Eisenhower Quadrant (4-box Matrix) */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">
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
                      ? 'border-[var(--accent-fg)]/45 bg-[var(--surface-3)]'
                      : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] hover:border-[var(--accent-fg)]/22'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-[var(--text)]">{meta.title}</span>
                    <span
                      className="num-geo text-[10px] font-bold"
                      style={{ color: meta.accent }}
                    >
                      {meta.code}
                    </span>
                  </div>
                  <p className="mt-0.5 text-[10px] text-[var(--text-2)]">{meta.action}</p>
                </button>
              );
            })}
          </div>
        </div>

        {/* Priority & Category */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">سطح اولویت</label>
            <div className="flex gap-1.5">
              {(['high', 'medium', 'low'] as TaskPriority[]).map((p) => (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPriority(p)}
                  className={`flex-1 rounded-xl border py-2 text-[11px] font-medium transition ${
                    priority === p
                      ? 'border-[var(--accent)] bg-[var(--surface-3)] text-[var(--text)]'
                      : 'border-[var(--accent-fg)]/10 bg-[var(--surface-input)] text-[var(--text-2)]'
                  }`}
                >
                  {PRIORITY_META[p].label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">دسته‌بندی</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as TaskCategory)}
              className="luxury-input py-2 text-xs"
            >
              {(Object.keys(CATEGORY_META) as TaskCategory[]).map((cat) => (
                <option key={cat} value={cat} className="bg-[var(--surface-2)] text-[var(--text)]">
                  {CATEGORY_META[cat].label}
                </option>
              ))}
            </select>
          </div>

          {projects.length > 0 && (
            <div>
              <label
                htmlFor="task-project"
                className="mb-1.5 block text-xs font-medium text-[var(--text-2)]"
              >
                پروژه
              </label>
              <select
                id="task-project"
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                className="luxury-input py-2 text-xs"
              >
                <option value="" className="bg-[var(--surface-2)] text-[var(--text)]">
                  بدون پروژه
                </option>
                {projects.map((project) => (
                  <option key={project.id} value={project.id} className="bg-[var(--surface-2)] text-[var(--text)]">
                    {project.title}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label
              htmlFor="task-estimate"
              className="mb-1.5 block text-xs font-medium text-[var(--text-2)]"
            >
              تخمین (دقیقه)
            </label>
            <input
              id="task-estimate"
              value={estimateMin}
              onChange={(e) => setEstimateMin(e.target.value.replace(/[^0-9]/g, ''))}
              inputMode="numeric"
              placeholder="مثلاً 45"
              className="luxury-input py-2 text-xs"
              maxLength={4}
            />
          </div>

          {openGoals.length > 0 && (
            <div className="col-span-2">
              <label
                htmlFor="task-goal"
                className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[var(--text-2)]"
              >
                هدف مرتبط
                <span className="text-[10px] font-normal text-[var(--text-3)]">
                  (پیشرفت هدف از همین کارها ساخته می‌شود)
                </span>
              </label>
              <select
                id="task-goal"
                value={goalId}
                onChange={(e) => setGoalId(e.target.value)}
                className="luxury-input py-2 text-xs"
              >
                <option value="" className="bg-[var(--surface-2)] text-[var(--text)]">
                  بدون هدف
                </option>
                {openGoals.map((goal) => (
                  <option key={goal.id} value={goal.id} className="bg-[var(--surface-2)] text-[var(--text)]">
                    {goal.title}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>

        {/* Subtasks */}
        <div>
          <label className="mb-1.5 block text-xs font-medium text-[var(--text-2)]">زیرکارها</label>
          <div className="space-y-1.5">
            {subtasks.map((sub) => (
              <div
                key={sub.id}
                className="flex items-center gap-2 rounded-xl border border-[var(--accent-fg)]/10 bg-[var(--surface-input)] px-3 py-2"
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
                      ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-on)]'
                      : 'border-[var(--accent-fg)]/30'
                  }`}
                >
                  {sub.completed && <Check className="h-3 w-3" />}
                </button>
                <span
                  className={`flex-1 text-xs ${
                    sub.completed ? 'text-[var(--text-3)] line-through' : 'text-[var(--text)]'
                  }`}
                >
                  {sub.title}
                </span>
                <button
                  type="button"
                  onClick={() => setSubtasks(subtasks.filter((s) => s.id !== sub.id))}
                  className="text-[var(--text-3)] hover:text-[var(--danger-fg)]"
                  aria-label="حذف زیرکار"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}

            <div className="flex gap-2">
              <input
                type="text"
                dir="auto"
                value={subtaskInput}
                onChange={(e) => setSubtaskInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
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
                className="btn-ghost-luxury shrink-0 rounded-xl px-3 text-xs text-[var(--accent-fg)]"
              >
                <Plus className="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        {/* Tags */}
        <div>
          <label className="mb-1.5 flex items-center gap-1 text-xs font-medium text-[var(--text-2)]">
            <Tag className="h-3.5 w-3.5 text-[var(--accent)]" />
            <span>برچسب‌ها</span>
          </label>
          <div className="mb-2 flex flex-wrap gap-1.5">
            {tags.map((t) => (
              <span
                key={t}
                className="inline-flex items-center gap-1 rounded-full border border-[var(--accent-fg)]/22 bg-[var(--surface-3)] px-2.5 py-0.5 text-[11px] text-[var(--accent-fg)]"
              >
                #{t}
                <button
                  type="button"
                  onClick={() => setTags(tags.filter((x) => x !== t))}
                  className="hover:text-[var(--text)]"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              type="text"
              dir="auto"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
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
              className="btn-ghost-luxury shrink-0 rounded-xl px-3 text-xs text-[var(--accent-fg)]"
            >
              افزودن
            </button>
          </div>
        </div>

        {/* Recurrence & Reminder */}
        <div className="grid grid-cols-1 gap-3 rounded-2xl border border-[var(--accent-fg)]/10 bg-[var(--surface-input)] p-3.5">
          <div className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-1.5 text-xs font-medium text-[var(--text)]">
              <Repeat className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
              تکرار وظیفه
            </span>
            <select
              value={recurrence}
              onChange={(e) => setRecurrence(e.target.value as RecurrenceRule)}
              className="rounded-xl border border-[var(--accent-fg)]/15 bg-[var(--surface-2)] px-3 py-1.5 text-xs text-[var(--text)] outline-none"
            >
              {(Object.keys(RECURRENCE_LABELS) as RecurrenceRule[]).map((r) => (
                <option key={r} value={r} className="bg-[var(--surface-2)]">
                  {RECURRENCE_LABELS[r]}
                </option>
              ))}
            </select>
          </div>

          <div className="h-px bg-[var(--accent-fg)]/10" />

          <div className="flex items-center justify-between gap-3">
            <span className="flex items-center gap-1.5 text-xs font-medium text-[var(--text)]">
              <Bell className="h-3.5 w-3.5 text-[var(--accent-fg)]" />
              یادآوری و اعلان
            </span>
            <button
              type="button"
              role="switch"
              aria-checked={reminderEnabled}
              onClick={() => setReminderEnabled((v) => !v)}
              className={`relative h-6 w-11 rounded-full transition-colors ${
                reminderEnabled ? 'bg-[var(--accent)]' : 'bg-[var(--surface-3)]'
              }`}
            >
              <span
                className={`absolute top-0.5 h-5 w-5 rounded-full bg-[var(--text)] transition-all ${
                  reminderEnabled ? 'left-0.5' : 'left-[22px]'
                }`}
              />
            </button>
          </div>

          {reminderEnabled && (
            <div className="flex items-center justify-between text-[11px] text-[var(--text-2)]">
              <span>زمان اعلام قبل از موعد:</span>
              <select
                value={reminderOffsetMin}
                onChange={(e) => setReminderOffsetMin(Number(e.target.value))}
                className="rounded-lg border border-[var(--accent-fg)]/15 bg-[var(--surface-2)] px-2.5 py-1 text-xs text-[var(--text)] outline-none"
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
