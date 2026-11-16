import { useMemo, useState } from 'react';
import {
  Archive,
  Bell,
  Check,
  CheckSquare,
  Clock,
  Columns3,
  Grid2x2,
  List,
  Plus,
  Repeat,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import type {
  EisenhowerQuadrant,
  Task,
  TaskCategory,
  TaskPriority,
  TaskStatus,
} from '@/types/domain';
import { archiveTask, deleteTask, setTaskStatus, updateTask } from '@/lib/db';
import { useTasks } from '@/lib/queries';
import { getTodayKey } from '@/lib/date';
import {
  CATEGORY_META,
  formatRelativeDueLabel,
  PRIORITY_META,
  QUADRANT_META,
} from '@/lib/format';
import { triggerHaptic } from '@/lib/native';
import { useApp } from '@/context/AppContext';
import { LuxuryCard } from '@/components/ui/LuxuryCard';
import { Numeric } from '@/components/ui/Numeric';
import { EmptyState } from '@/components/ui/EmptyState';

type TaskViewMode = 'list' | 'kanban' | 'eisenhower' | 'archive';

export function TasksPage() {
  const { openCreateTask, openEditTask, pushToast, settings } = useApp();
  const [viewMode, setViewMode] = useState<TaskViewMode>('list');
  const [search, setSearch] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [filterCategory, setFilterCategory] = useState<TaskCategory | 'all'>('all');
  const [filterPriority, setFilterPriority] = useState<TaskPriority | 'all'>('all');
  const [onlyRecurring, setOnlyRecurring] = useState(false);
  const [dragTaskId, setDragTaskId] = useState<string | null>(null);

  const todayKey = getTodayKey();
  const tasks = useTasks();

  const filteredTasks = useMemo(() => {
    const q = search.trim().toLowerCase();
    return tasks.filter((t) => {
      if (viewMode === 'archive') {
        if (!t.archived) return false;
      } else {
        if (t.archived) return false;
      }
      if (filterCategory !== 'all' && t.category !== filterCategory) return false;
      if (filterPriority !== 'all' && t.priority !== filterPriority) return false;
      if (onlyRecurring && t.recurrence === 'none') return false;
      if (q) {
        const hay = `${t.title} ${t.description} ${t.tags.join(' ')}`.toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    });
  }, [tasks, viewMode, filterCategory, filterPriority, onlyRecurring, search]);

  const counts = useMemo(() => {
    const active = tasks.filter((t) => !t.archived);
    return {
      total: active.length,
      done: active.filter((t) => t.status === 'completed').length,
      inProgress: active.filter((t) => t.status === 'in_progress').length,
      archived: tasks.filter((t) => t.archived).length,
    };
  }, [tasks]);

  const handleStatusChange = async (task: Task, nextStatus: TaskStatus) => {
    const { spawnedNext } = await setTaskStatus(task.id, nextStatus);
    void triggerHaptic(nextStatus === 'completed' ? 'success' : 'light', settings.hapticsEnabled);
    if (spawnedNext) {
      pushToast({
        title: 'نوبت بعدی وظیفه تکرارشونده ثبت شد',
        description: task.title,
        tone: 'success',
      });
    }
  };

  const handleMoveQuadrant = async (task: Task, quadrant: EisenhowerQuadrant) => {
    await updateTask(task.id, { quadrant });
    void triggerHaptic('light', settings.hapticsEnabled);
  };

  const listGroups = useMemo(() => {
    const overdue: Task[] = [];
    const today: Task[] = [];
    const upcoming: Task[] = [];
    const unscheduled: Task[] = [];
    const completed: Task[] = [];

    for (const t of filteredTasks) {
      if (t.status === 'completed') {
        completed.push(t);
      } else if (!t.dueDate) {
        unscheduled.push(t);
      } else if (t.dueDate < todayKey) {
        overdue.push(t);
      } else if (t.dueDate === todayKey) {
        today.push(t);
      } else {
        upcoming.push(t);
      }
    }

    upcoming.sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''));
    return [
      { id: 'overdue', title: 'عقب‌افتاده', items: overdue, accent: '#EB9BAB' },
      { id: 'today', title: 'امروز', items: today, accent: '#B69AEF' },
      { id: 'upcoming', title: 'آینده', items: upcoming, accent: '#F4F1F8' },
      { id: 'unscheduled', title: 'بدون تاریخ', items: unscheduled, accent: '#A79EB9' },
      { id: 'completed', title: 'تکمیل‌شده‌های اخیر', items: completed, accent: '#8052D6' },
    ].filter((g) => g.items.length > 0);
  }, [filteredTasks, todayKey]);

  const renderTaskCard = (task: Task, compact = false) => {
    const isDone = task.status === 'completed';
    const dueInfo = formatRelativeDueLabel(task.dueDate, todayKey);
    const doneSubs = task.subtasks.filter((s) => s.completed).length;
    const cat = CATEGORY_META[task.category];

    return (
      <LuxuryCard
        key={task.id}
        draggable={viewMode === 'kanban'}
        onDragStart={() => setDragTaskId(task.id)}
        onDragEnd={() => setDragTaskId(null)}
        className={`transition ${compact ? 'p-3' : 'p-3.5'} ${
          isDone && viewMode === 'list' ? 'opacity-65' : 'hover:border-[#B69AEF]/24'
        }`}
      >
        <div className="flex items-start gap-2.5">
          <button
            type="button"
            onClick={() =>
              handleStatusChange(task, isDone ? 'todo' : 'completed')
            }
            aria-label={isDone ? 'بازگردانی به انجام نشده' : 'تکمیل وظیفه'}
            className={`mt-0.5 flex h-5.5 w-5.5 shrink-0 items-center justify-center rounded-lg border transition ${
              isDone
                ? 'border-[#8052D6] bg-[#8052D6] text-[#F4F1F8]'
                : task.status === 'in_progress'
                ? 'border-[#B69AEF] bg-[#241936] text-[#B69AEF]'
                : 'border-[#B69AEF]/28 bg-[#100F16] hover:border-[#B69AEF]'
            }`}
          >
            {isDone ? (
              <Check className="h-3.5 w-3.5" strokeWidth={2.5} />
            ) : task.status === 'in_progress' ? (
              <span className="h-2 w-2 rounded-full bg-[#B69AEF]" />
            ) : null}
          </button>

          <button
            type="button"
            onClick={() => openEditTask(task)}
            className="min-w-0 flex-1 text-right focus:outline-none"
          >
            <p
              className={`text-[13px] font-medium leading-5 ${
                isDone ? 'text-[#A79EB9] line-through' : 'text-[#F4F1F8]'
              }`}
            >
              {task.title}
            </p>
            {!compact && task.description && (
              <p className="mt-0.5 line-clamp-1 text-[11px] text-[#6E6680]">
                {task.description}
              </p>
            )}

            <div className="mt-2 flex flex-wrap items-center gap-1.5 text-[10px]">
              <span
                className="rounded-md px-1.5 py-0.5 font-medium"
                style={{ backgroundColor: cat.badgeBg, color: cat.badgeText }}
              >
                {cat.label}
              </span>

              <span
                className={`inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 ${
                  dueInfo.isOverdue && !isDone
                    ? 'bg-[#D9778B]/15 text-[#EB9BAB]'
                    : 'bg-[#100F16] text-[#A79EB9]'
                }`}
              >
                <Numeric value={dueInfo.label} variant="geo" className="text-[10px]" />
                {task.dueTime && (
                  <>
                    <span>•</span>
                    <Numeric value={task.dueTime} variant="geo" className="text-[10px]" />
                  </>
                )}
              </span>

              {task.subtasks.length > 0 && (
                <span className="rounded-md bg-[#241936]/80 px-1.5 py-0.5 text-[#B69AEF]">
                  <Numeric
                    value={`${doneSubs}/${task.subtasks.length}`}
                    variant="geo"
                    className="text-[10px]"
                  />{' '}
                  زیرکار
                </span>
              )}

              {task.recurrence !== 'none' && (
                <Repeat className="h-3 w-3 text-[#B69AEF]" aria-label="وظیفه تکرارشونده" />
              )}

              {task.reminderEnabled && (
                <Bell className="h-3 w-3 text-[#8052D6]" aria-label="دارای یادآوری" />
              )}

              <span
                className="mr-auto inline-flex items-center gap-1 text-[10px]"
                style={{ color: PRIORITY_META[task.priority].textColor }}
              >
                <span
                  className="h-1.5 w-1.5 rounded-full"
                  style={{ backgroundColor: PRIORITY_META[task.priority].dotColor }}
                />
                {QUADRANT_META[task.quadrant].code}
              </span>
            </div>
          </button>

          {/* Archive quick button for completed items in list view */}
          {viewMode === 'list' && isDone && (
            <button
              type="button"
              onClick={async () => {
                await archiveTask(task.id, true);
                pushToast({ title: 'به آرشیو منتقل شد', tone: 'default' });
              }}
              aria-label="انتقال به آرشیو"
              className="rounded-lg p-1.5 text-[#6E6680] hover:bg-[#241936] hover:text-[#B69AEF]"
            >
              <Archive className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        {/* Touch-friendly Kanban Status Switcher */}
        {viewMode === 'kanban' && (
          <div className="mt-2.5 flex items-center gap-1 border-t border-[#B69AEF]/10 pt-2">
            {(
              [
                { id: 'todo', label: 'انجام نشده' },
                { id: 'in_progress', label: 'در جریان' },
                { id: 'completed', label: 'تکمیل' },
              ] as const
            ).map((col) => (
              <button
                key={col.id}
                type="button"
                onClick={() => handleStatusChange(task, col.id)}
                className={`flex-1 rounded-lg py-1 text-[10px] font-medium transition ${
                  task.status === col.id
                    ? 'bg-[#8052D6] text-[#F4F1F8]'
                    : 'bg-[#100F16] text-[#A79EB9] hover:text-[#F4F1F8]'
                }`}
              >
                {col.label}
              </button>
            ))}
          </div>
        )}
      </LuxuryCard>
    );
  };

  return (
    <div className="space-y-4">
      {/* Header & KPI Counter */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-lg font-bold text-[#F4F1F8]">مدیریت وظایف</h1>
          <p className="mt-0.5 text-[11px] text-[#A79EB9]">
            <Numeric value={counts.done} className="font-semibold text-[#B69AEF]" /> تکمیل‌شده از{' '}
            <Numeric value={counts.total} className="font-semibold text-[#F4F1F8]" /> وظیفه فعال
            {counts.inProgress > 0 && (
              <>
                {' '}
                • <Numeric value={counts.inProgress} className="text-[#B69AEF]" /> در جریان
              </>
            )}
          </p>
        </div>

        <button
          type="button"
          onClick={() => openCreateTask({ defaultDueDate: todayKey })}
          className="btn-royal flex items-center gap-1.5 rounded-2xl px-3.5 py-2.5 text-xs font-semibold"
        >
          <Plus className="h-4 w-4" />
          <span>وظیفه جدید</span>
        </button>
      </div>

      {/* 4 View Modes Segmented Bar */}
      <div className="grid grid-cols-4 gap-1 rounded-2xl border border-[#B69AEF]/12 bg-[#15141B] p-1">
        {(
          [
            { id: 'list', label: 'لیست', icon: List },
            { id: 'kanban', label: 'کانبان', icon: Columns3 },
            { id: 'eisenhower', label: 'آیزنهاور', icon: Grid2x2 },
            { id: 'archive', label: 'آرشیو', icon: Archive },
          ] as const
        ).map((tab) => {
          const Icon = tab.icon;
          const active = viewMode === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setViewMode(tab.id)}
              className={`flex items-center justify-center gap-1 rounded-xl py-2 text-[11px] font-medium transition ${
                active
                  ? 'bg-[#241936] text-[#F4F1F8] shadow-[inset_0_1px_0_rgba(182,154,239,0.2)]'
                  : 'text-[#A79EB9] hover:text-[#F4F1F8]'
              }`}
            >
              <Icon className={`h-3.5 w-3.5 ${active ? 'text-[#B69AEF]' : ''}`} />
              <span>{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Search & Filter Row */}
      <div className="flex items-center gap-2">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-[#6E6680]" />
          <input
            type="text"
            dir="auto"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="جست‌وجوی عنوان، توضیحات یا برچسب..."
            className="luxury-input py-2.5 pr-9 pl-3 text-xs"
          />
        </div>
        <button
          type="button"
          onClick={() => setShowFilters((p) => !p)}
          aria-label="فیلتر وظایف"
          className={`flex h-10 w-10 items-center justify-center rounded-xl border transition ${
            showFilters || filterCategory !== 'all' || filterPriority !== 'all' || onlyRecurring
              ? 'border-[#8052D6] bg-[#241936] text-[#B69AEF]'
              : 'border-[#B69AEF]/12 bg-[#15141B] text-[#A79EB9]'
          }`}
        >
          <SlidersHorizontal className="h-4 w-4" />
        </button>
      </div>

      {showFilters && (
        <LuxuryCard variant="subtle" className="space-y-3 p-3.5">
          <div className="grid grid-cols-2 gap-2.5">
            <div>
              <label className="mb-1 block text-[10.5px] text-[#A79EB9]">دسته‌بندی</label>
              <select
                value={filterCategory}
                onChange={(e) => setFilterCategory(e.target.value as TaskCategory | 'all')}
                className="luxury-input py-1.5 text-xs"
              >
                <option value="all">همه دسته‌ها</option>
                {(Object.keys(CATEGORY_META) as TaskCategory[]).map((cat) => (
                  <option key={cat} value={cat} className="bg-[#15141B]">
                    {CATEGORY_META[cat].label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-[10.5px] text-[#A79EB9]">اولویت</label>
              <select
                value={filterPriority}
                onChange={(e) => setFilterPriority(e.target.value as TaskPriority | 'all')}
                className="luxury-input py-1.5 text-xs"
              >
                <option value="all">همه اولویت‌ها</option>
                <option value="high">بالا</option>
                <option value="medium">متوسط</option>
                <option value="low">عادی</option>
              </select>
            </div>
          </div>

          <div className="flex items-center justify-between">
            <label className="flex items-center gap-2 text-xs text-[#F4F1F8]">
              <input
                type="checkbox"
                checked={onlyRecurring}
                onChange={(e) => setOnlyRecurring(e.target.checked)}
                className="accent-[#8052D6]"
              />
              فقط وظایف تکرارشونده
            </label>
            <button
              type="button"
              onClick={() => {
                setFilterCategory('all');
                setFilterPriority('all');
                setOnlyRecurring(false);
                setSearch('');
              }}
              className="text-[11px] text-[#B69AEF]"
            >
              پاک کردن فیلترها
            </button>
          </div>
        </LuxuryCard>
      )}

      {/* VIEW 1: SMART GROUPED LIST */}
      {viewMode === 'list' && (
        <div className="space-y-4">
          {listGroups.length === 0 ? (
            <EmptyState
              icon={<CheckSquare className="h-5 w-5" />}
              title="وظیفه‌ای مطابق فیلتر فعلی یافت نشد"
              description="با دکمه «وظیفه جدید» کار بعدی خود را ثبت کنید یا فیلتر جست‌وجو را تغییر دهید."
              action={
                <button
                  type="button"
                  onClick={() => openCreateTask({ defaultDueDate: todayKey })}
                  className="btn-royal rounded-xl px-4 py-2 text-xs font-semibold"
                >
                  ایجاد وظیفه
                </button>
              }
            />
          ) : (
            listGroups.map((group) => (
              <section key={group.id} className="space-y-2">
                <div className="flex items-center justify-between px-1">
                  <h2 className="text-xs font-bold" style={{ color: group.accent }}>
                    {group.title}
                  </h2>
                  <Numeric
                    value={group.items.length}
                    variant="geo"
                    className="rounded-md bg-[#15141B] px-2 py-0.5 text-[11px] text-[#A79EB9]"
                  />
                </div>
                <div className="space-y-2">{group.items.map((t) => renderTaskCard(t))}</div>
              </section>
            ))
          )}
        </div>
      )}

      {/* VIEW 2: KANBAN BOARD (To Do / In Progress / Completed) */}
      {viewMode === 'kanban' && (
        <div className="space-y-3.5">
          {(
            [
              { status: 'todo', title: 'انجام نشده', en: 'To Do', accent: '#A79EB9' },
              { status: 'in_progress', title: 'در حال انجام', en: 'In Progress', accent: '#B69AEF' },
              { status: 'completed', title: 'تکمیل شده', en: 'Completed', accent: '#8052D6' },
            ] as const
          ).map((col) => {
            const items = filteredTasks.filter((t) => t.status === col.status);
            return (
              <div
                key={col.status}
                onDragOver={(e) => e.preventDefault()}
                onDrop={async () => {
                  if (!dragTaskId) return;
                  const target = tasks.find((x) => x.id === dragTaskId);
                  if (target && target.status !== col.status) {
                    await handleStatusChange(target, col.status);
                  }
                  setDragTaskId(null);
                }}
                className="rounded-2xl border border-[#B69AEF]/12 bg-[#111018] p-3"
              >
                <div className="mb-2.5 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span
                      className="h-2 w-2 rounded-full"
                      style={{ backgroundColor: col.accent }}
                    />
                    <h2 className="text-xs font-bold text-[#F4F1F8]">{col.title}</h2>
                    <span className="num-geo text-[10px] text-[#6E6680]">{col.en}</span>
                  </div>
                  <Numeric
                    value={items.length}
                    variant="geo"
                    className="rounded-lg bg-[#1B1924] px-2 py-0.5 text-[11px] font-semibold text-[#B69AEF]"
                  />
                </div>

                {items.length === 0 ? (
                  <p className="rounded-xl border border-dashed border-[#B69AEF]/12 py-4 text-center text-[11px] text-[#6E6680]">
                    وظیفه‌ای در این ستون نیست
                  </p>
                ) : (
                  <div className="space-y-2">{items.map((t) => renderTaskCard(t, true))}</div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* VIEW 3: EISENHOWER 4-QUADRANT MATRIX */}
      {viewMode === 'eisenhower' && (
        <div className="grid grid-cols-1 gap-3">
          {(['q1', 'q2', 'q3', 'q4'] as EisenhowerQuadrant[]).map((q) => {
            const meta = QUADRANT_META[q];
            const items = filteredTasks.filter(
              (t) => t.quadrant === q && t.status !== 'completed'
            );
            return (
              <LuxuryCard key={q} className="p-3.5">
                <div className="mb-2.5 flex items-start justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <span
                        className="num-geo rounded-md px-1.5 py-0.5 text-[10px] font-bold text-[#09090D]"
                        style={{ backgroundColor: meta.accent }}
                      >
                        {meta.code}
                      </span>
                      <h2 className="text-xs font-bold text-[#F4F1F8]">{meta.title}</h2>
                    </div>
                    <p className="mt-0.5 text-[10.5px] text-[#A79EB9]">
                      {meta.subtitle} — <span className="text-[#B69AEF]">{meta.action}</span>
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={() => openCreateTask({ defaultDueDate: todayKey, defaultQuadrant: q })}
                    aria-label={`افزودن به ${meta.title}`}
                    className="flex h-7 w-7 items-center justify-center rounded-lg border border-[#B69AEF]/18 bg-[#241936] text-[#B69AEF] hover:bg-[#8052D6] hover:text-[#F4F1F8]"
                  >
                    <Plus className="h-3.5 w-3.5" />
                  </button>
                </div>

                {items.length === 0 ? (
                  <p className="rounded-xl bg-[#09090D]/55 py-3 text-center text-[11px] text-[#6E6680]">
                    وظیفه فعالی در این خانه نیست
                  </p>
                ) : (
                  <div className="space-y-2">
                    {items.map((task) => (
                      <div
                        key={task.id}
                        className="flex items-center justify-between gap-2 rounded-xl border border-[#B69AEF]/10 bg-[#0F0E15] px-3 py-2.5"
                      >
                        <button
                          type="button"
                          onClick={() => handleStatusChange(task, 'completed')}
                          aria-label="تکمیل"
                          className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md border border-[#B69AEF]/30 hover:border-[#B69AEF]"
                        />
                        <button
                          type="button"
                          onClick={() => openEditTask(task)}
                          className="min-w-0 flex-1 text-right"
                        >
                          <p className="truncate text-xs font-medium text-[#F4F1F8]">{task.title}</p>
                          {task.dueTime && (
                            <span className="inline-flex items-center gap-1 text-[10px] text-[#A79EB9]">
                              <Clock className="h-2.5 w-2.5" />
                              <Numeric value={task.dueTime} className="text-[10px]" />
                            </span>
                          )}
                        </button>
                        <select
                          aria-label="تغییر خانه آیزنهاور"
                          value={task.quadrant}
                          onChange={(e) =>
                            handleMoveQuadrant(task, e.target.value as EisenhowerQuadrant)
                          }
                          className="num-geo rounded-lg border border-[#B69AEF]/16 bg-[#15141B] px-1.5 py-1 text-[10px] text-[#B69AEF] outline-none"
                        >
                          <option value="q1">Q1</option>
                          <option value="q2">Q2</option>
                          <option value="q3">Q3</option>
                          <option value="q4">Q4</option>
                        </select>
                      </div>
                    ))}
                  </div>
                )}
              </LuxuryCard>
            );
          })}
        </div>
      )}

      {/* VIEW 4: COMPLETED ARCHIVE */}
      {viewMode === 'archive' && (
        <div className="space-y-2.5">
          {filteredTasks.length === 0 ? (
            <EmptyState
              icon={<Archive className="h-5 w-5" />}
              title="آرشیو خالی است"
              description="وظایف تکمیل‌شده‌ای که آرشیو می‌کنید در اینجا برای مراجعه بعدی نگهداری می‌شوند."
            />
          ) : (
            filteredTasks.map((task) => (
              <LuxuryCard
                key={task.id}
                className="flex items-center justify-between gap-3 px-3.5 py-3"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-medium text-[#A79EB9] line-through">
                    {task.title}
                  </p>
                  <p className="mt-0.5 text-[10.5px] text-[#6E6680]">
                    {CATEGORY_META[task.category].label}
                    {task.completedDate && (
                      <>
                        {' '}
                        • تکمیل در{' '}
                        <Numeric
                          value={formatRelativeDueLabel(task.completedDate, todayKey).label}
                          className="text-[10.5px]"
                        />
                      </>
                    )}
                  </p>
                </div>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={async () => {
                      await archiveTask(task.id, false);
                      pushToast({ title: 'از آرشیو بازیابی شد', tone: 'success' });
                    }}
                    aria-label="بازیابی از آرشیو"
                    className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#241936] text-[#B69AEF] hover:bg-[#8052D6] hover:text-[#F4F1F8]"
                  >
                    <RotateCcw className="h-3.5 w-3.5" />
                  </button>
                  <button
                    type="button"
                    onClick={async () => {
                      await deleteTask(task.id);
                      pushToast({ title: 'برای همیشه حذف شد', tone: 'default' });
                    }}
                    aria-label="حذف دائمی"
                    className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#D9778B]/12 text-[#EB9BAB] hover:bg-[#D9778B]/25"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                </div>
              </LuxuryCard>
            ))
          )}
        </div>
      )}
    </div>
  );
}
