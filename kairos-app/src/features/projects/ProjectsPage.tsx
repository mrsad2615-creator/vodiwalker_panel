import { useEffect, useMemo, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { Check, Plus, Trash2 } from '@/components/ui/icons';
import { Numeric } from '@/components/ui/Numeric';
import { Rail, RailRow } from '@/components/ui/Rail';
import { SectionHeader } from '@/components/ui/SectionHeader';
import { ModalSheet } from '@/components/ui/ModalSheet';
import { JalaliDatePicker } from '@/components/ui/JalaliDatePicker';
import { CATEGORY_META, createId } from '@/lib/format';
import { formatJalaaliFull, getTodayKey } from '@/lib/date';
import {
  createProject,
  createStage,
  deleteProject,
  deleteStage,
  toggleStage,
  updateProject,
} from '@/lib/db';
import { useApp } from '@/context/AppContext';
import { useGoals, useProjectStages, useProjects, useTasks } from '@/lib/queries';
import {
  computeProjectProgress,
  describeBasis,
  projectRisks,
  riskSentence,
} from '@/lib/projects';
import type { Project, TaskCategory } from '@/types/domain';
import { triggerHaptic } from '@/lib/native';
import { addSampleProject, SAMPLE_PROJECT_ID } from '@/lib/sampleProject';

/**
 * Projects — the room where a goal becomes work.
 *
 * The chain is goal → project → stage → task. This screen owns the middle two.
 * Every percentage on it comes from `computeProjectProgress` (stages weighted, tasks
 * second), and every warning comes from `projectRisks` — so a project cannot show
 * a confident number that no data supports.
 */

export function ProjectsPage() {
  const projects = useProjects();
  const stages = useProjectStages();
  const tasks = useTasks();
  const goals = useGoals();
  const todayKey = getTodayKey();
  const [editing, setEditing] = useState<Project | null>(null);
  const [creating, setCreating] = useState(false);
  const [confirmSample, setConfirmSample] = useState(false);
  const [sampleBusy, setSampleBusy] = useState(false);
  const { pushToast } = useApp();
  const hasSampleProject = projects.some((project) => project.id === SAMPLE_PROJECT_ID && project.sample === true);

  const createSample = async () => {
    if (sampleBusy) return;
    setSampleBusy(true);
    try {
      await addSampleProject();
      setConfirmSample(false);
      pushToast({ title: 'پروژهٔ نمونه اضافه شد', tone: 'success' });
    } catch {
      pushToast({ title: 'پروژهٔ نمونه اضافه نشد', description: 'داده‌های موجود تغییر نکردند؛ احتمالاً یکی از شناسه‌ها از قبل وجود دارد.', tone: 'warning' });
    } finally { setSampleBusy(false); }
  };

  const active = projects.filter((project) => project.status !== 'archived');
  const archived = projects.filter((project) => project.status === 'archived');

  const risks = useMemo(
    () => projectRisks({ projects, stages, tasks, todayKey }),
    [projects, stages, tasks, todayKey]
  );
  const riskById = useMemo(() => new Map(risks.map((risk) => [risk.projectId, risk])), [risks]);

  return (
    <div className="space-y-5 pb-4" data-testid="projects-ready">
      <SectionHeader
        size="page"
        eyebrow="PROJECTS"
        title="پروژه‌ها"
        meta={
          <span className="num-geo">
            <Numeric value={active.length} variant="geo" /> پروژه ·{' '}
            <Numeric value={risks.length} variant="geo" /> در معرض تأخیر
          </span>
        }
        action={
          <button
            type="button"
            onClick={() => setCreating(true)}
            className="flex items-center gap-1 rounded-full border border-[var(--border-strong)] px-3 py-1.5 text-[11px] font-medium text-[var(--accent-fg)]"
          >
            <Plus className="h-3 w-3" />
            پروژه جدید
          </button>
        }
      />


      {!hasSampleProject && (confirmSample ? (
        <div role="region" aria-label="تأیید پروژهٔ نمونه" className="space-y-2 rounded-xl border border-[var(--border)] bg-[var(--surface-1)] p-3 text-[11px] leading-6">
          <p>یک پروژهٔ ساختگی با سه مرحله و دو کار نمونه اضافه می‌شود. داده‌های فعلی تغییر نمی‌کنند؛ آمار می‌تواند ترکیبی شود.</p>
          <div className="flex gap-2">
            <button type="button" disabled={sampleBusy} onClick={() => void createSample()} className="btn-royal rounded-xl px-3 py-2 disabled:opacity-50">تأیید پروژهٔ نمونه</button>
            <button type="button" disabled={sampleBusy} onClick={() => setConfirmSample(false)} className="btn-ghost-luxury rounded-xl px-3 py-2">انصراف</button>
          </div>
        </div>
      ) : (
        <button type="button" onClick={() => setConfirmSample(true)} className="rounded-xl border border-[var(--border)] px-3 py-2 text-[11px] text-[var(--accent-fg)]">افزودن پروژهٔ نمونه</button>
      ))}

      {active.length === 0 ? (
        <Rail>
          <RailRow as="button" onClick={() => setCreating(true)}>
            <span className="flex-1 text-start text-[11.5px] text-[var(--text-3)]">
              هنوز پروژه‌ای نساخته‌ای. پروژه، حلقهٔ واسط هدف و کار روزانه است.
            </span>
            <span className="text-[10.5px] font-medium text-[var(--accent-fg)]">ساختن پروژه</span>
          </RailRow>
        </Rail>
      ) : (
        <div className="space-y-2">
          {active.map((project, index) => (
            <ProjectCard
              key={project.id}
              project={project}
              index={index}
              progress={computeProjectProgress(
                stages.filter((stage) => stage.projectId === project.id),
                tasks.filter((task) => task.projectId === project.id)
              )}
              risk={riskById.get(project.id) ?? null}
              stageCount={stages.filter((stage) => stage.projectId === project.id).length}
              onOpen={() => setEditing(project)}
            />
          ))}
        </div>
      )}

      {archived.length > 0 && (
        <section className="space-y-2">
          <SectionHeader size="section" title="آرشیو" meta={<Numeric value={archived.length} variant="geo" />} />
          <Rail>
            {archived.map((project) => (
              <RailRow key={project.id}>
                <button
                  type="button"
                  onClick={() => void updateProject(project.id, { status: 'active' })}
                  className="flex-1 text-start text-[11px] text-[var(--text-3)]"
                >
                  {project.title} — بازگردانی
                </button>
              </RailRow>
            ))}
          </Rail>
        </section>
      )}

      <ProjectSheet
        key={creating ? 'new' : editing?.id ?? 'idle'}
        project={editing}
        creating={creating}
        goals={goals}
        stages={stages}
        tasks={tasks}
        onClose={() => {
          setEditing(null);
          setCreating(false);
        }}
        onCreated={(created) => {
          setCreating(false);
          setEditing(created);
        }}
      />
    </div>
  );
}

function ProjectCard({
  project,
  progress,
  risk,
  stageCount,
  index,
  onOpen,
}: {
  project: Project;
  progress: ReturnType<typeof computeProjectProgress>;
  risk: ReturnType<typeof projectRisks>[number] | null;
  stageCount: number;
  index: number;
  onOpen: () => void;
}) {
  return (
    <motion.button
      type="button"
      onClick={onOpen}
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.03 }}
      className="w-full rounded-[var(--r-rail)] border border-[var(--border)] bg-[var(--surface-1)] p-3.5 text-start"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-[12.5px] font-semibold text-[var(--text)]">{project.title}</p>
          <p className="mt-0.5 text-[9.5px] text-[var(--text-3)]">
            {CATEGORY_META[project.category]?.label}
            {stageCount > 0 ? ` · ${stageCount} مرحله` : ' · بدون مرحله'}
            {project.dueDate ? ` · موعد ${formatJalaaliFull(project.dueDate).compact}` : ''}
          </p>
        </div>
        <span className="num-geo shrink-0 text-[13px] font-semibold text-[var(--accent-fg)]">
          <Numeric value={progress.percent} suffix="%" />
        </span>
      </div>

      <span className="mt-2 block h-[2px] w-full overflow-hidden rounded-full bg-white/8">
        <span
          className="block h-full rounded-full transition-[width] duration-500"
          style={{ width: `${Math.max(progress.percent, 2)}%`, background: 'var(--accent-fg)' }}
        />
      </span>

      <p className="mt-1.5 text-[9.5px] text-[var(--text-3)]">{describeBasis(progress)}</p>

      {risk && (
        <p
          className="mt-1.5 border-s-2 border-[var(--danger)] ps-2 text-[10px] text-[var(--danger-fg)]"
          data-testid="project-risk"
        >
          {riskSentence(risk)}
        </p>
      )}
    </motion.button>
  );
}

function ProjectSheet({
  project,
  creating,
  goals,
  stages,
  tasks,
  onClose,
  onCreated,
}: {
  project: Project | null;
  creating: boolean;
  goals: { id: string; title: string }[];
  stages: { id: string; projectId: string; title: string; done: boolean; kind: string; order: number }[];
  tasks: { id: string; projectId?: string | null; status: string; title: string }[];
  onClose: () => void;
  /** Called with the new project so the sheet can stay open on it. */
  onCreated: (project: Project) => void;
}) {
  const open = creating || Boolean(project);
  const { pushToast } = useApp();

  // The editor is remounted per project (see the `key` on <ProjectSheet>), so
  // every field can simply start from its prop. No hydration effect, and no way
  // for a live query to overwrite what the user is currently typing.
  const [title, setTitle] = useState(project?.title ?? '');
  const [description, setDescription] = useState(project?.description ?? '');
  const [category, setCategory] = useState<TaskCategory>(project?.category ?? 'work');
  const [goalId, setGoalId] = useState(project?.goalId ?? '');
  const [dueDate, setDueDate] = useState<string | null>(project?.dueDate ?? null);
  const [showPicker, setShowPicker] = useState(false);
  const [stageTitle, setStageTitle] = useState('');

  // `save()` awaits the database, so a slow write may finish after the editor was
  // replaced by another project. Only the instance that is still on screen is
  // allowed to close the sheet.
  const key = creating ? 'new' : project?.id ?? null;
  const keyRef = useRef<string | null>(key);
  keyRef.current = key;
  const alive = useRef(true);
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  const closeIfStillMine = (savedKey: string | null) => {
    if (alive.current && keyRef.current === savedKey) onClose();
  };

  const ownStages = project ? stages.filter((stage) => stage.projectId === project.id) : [];
  const ownTasks = project ? tasks.filter((task) => task.projectId === project.id) : [];

  const save = async () => {
    const savedKey = key;
    if (!title.trim()) {
      pushToast({ title: 'نام پروژه را وارد کن', tone: 'warning' });
      return;
    }
    if (project) {
      await updateProject(project.id, {
        title: title.trim(),
        description: description.trim(),
        category,
        goalId: goalId || null,
        dueDate,
      });
      pushToast({ title: 'پروژه به‌روز شد', tone: 'success' });
    } else {
      const created = await createProject({
        title: title.trim(),
        description: description.trim(),
        category,
        goalId: goalId || null,
        startDate: getTodayKey(),
        dueDate,
      });
      pushToast({
        title: 'پروژه ساخته شد',
        description: 'حالا مرحله‌هایش را اضافه کن.',
        tone: 'success',
      });
      void triggerHaptic('success');
      // Stay in the editor, now pointed at the saved project: the stages are
      // written next, and closing would throw away that context.
      if (alive.current) onCreated(created);
      return;
    }
    void triggerHaptic('success');
    closeIfStillMine(savedKey);
  };

  const addStage = async () => {
    if (!project || !stageTitle.trim()) return;
    await createStage({
      projectId: project.id,
      title: stageTitle.trim(),
      kind: 'stage',
      order: ownStages.length,
      weight: 1,
      dueDate: null,
    });
    setStageTitle('');
    void triggerHaptic('light');
  };

  return (
    <ModalSheet
      open={open}
      onClose={onClose}
      title={project ? 'ویرایش پروژه' : 'پروژهٔ جدید'}
      subtitle={project ? formatJalaaliFull(getTodayKey()).compact : 'هدف ← پروژه ← مرحله ← کار'}
      footer={
        <div className="flex items-center justify-between gap-3 px-5 pb-1">
          {project ? (
            <button
              type="button"
              onClick={async () => {
                await deleteProject(project.id);
                pushToast({ title: 'پروژه حذف شد؛ کارهایش باقی ماندند', tone: 'warning' });
                if (alive.current) onClose();
              }}
              className="flex items-center gap-1 text-[11px] text-[var(--danger-fg)]"
            >
              <Trash2 className="h-3.5 w-3.5" />
              حذف پروژه
            </button>
          ) : (
            <span className="text-[10.5px] text-[var(--text-3)]">بعداً هم می‌توانی مرحله اضافه کنی</span>
          )}
          <button
            type="button"
            onClick={() => void save()}
            className="rounded-full bg-[var(--accent)] px-5 py-2 text-xs font-bold text-[var(--accent-on)]"
          >
            {project ? 'ذخیره' : 'ساختن'}
          </button>
        </div>
      }
    >
      <div className="space-y-4 px-5 py-4">
        <div>
          <label htmlFor="project-title" className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">
            نام پروژه
          </label>
          <input
            id="project-title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            className="luxury-input text-xs"
            placeholder="مثلاً: آماده‌سازی ارائهٔ پایان فصل"
            maxLength={80}
          />
        </div>

        <div>
          <label htmlFor="project-desc" className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">
            توضیح (اختیاری)
          </label>
          <textarea
            id="project-desc"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={2}
            className="luxury-input resize-none text-xs leading-6"
            placeholder="این پروژه دقیقاً چه چیزی را تحویل می‌دهد؟"
            maxLength={400}
          />
        </div>

        <div className="grid grid-cols-2 gap-2.5">
          <div>
            <label htmlFor="project-cat" className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">
              دسته
            </label>
            <select
              id="project-cat"
              value={category}
              onChange={(event) => setCategory(event.target.value as TaskCategory)}
              className="luxury-input py-2 text-xs"
            >
              {(Object.keys(CATEGORY_META) as TaskCategory[]).map((cat) => (
                <option key={cat} value={cat} className="bg-[var(--surface-2)]">
                  {CATEGORY_META[cat].label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="project-goal" className="mb-1.5 block text-[11px] font-medium text-[var(--text-2)]">
              هدف مرتبط
            </label>
            <select
              id="project-goal"
              value={goalId}
              onChange={(event) => setGoalId(event.target.value)}
              className="luxury-input py-2 text-xs"
            >
              <option value="" className="bg-[var(--surface-2)]">
                بدون هدف
              </option>
              {goals.map((goal) => (
                <option key={goal.id} value={goal.id} className="bg-[var(--surface-2)]">
                  {goal.title}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <button
            type="button"
            onClick={() => setShowPicker((value) => !value)}
            className="text-[11px] text-[var(--accent-fg)]"
          >
            {dueDate ? `موعد: ${formatJalaaliFull(dueDate).compact}` : 'تعیین موعد'}
          </button>
          {showPicker && (
            <div className="mt-2">
              <JalaliDatePicker
                value={dueDate}
                onChange={(value) => {
                  setDueDate(value);
                  setShowPicker(false);
                }}
              />
            </div>
          )}
        </div>

        {project && (
          <div className="border-t border-[var(--border)] pt-3">
            <p className="mb-2 text-[11px] font-medium text-[var(--text-2)]">
              مرحله‌ها <span className="text-[9.5px] text-[var(--text-3)]">(وزن هر مرحله در درصد پیشرفت اثر دارد)</span>
            </p>
            <div className="space-y-1.5">
              {ownStages.map((stage) => (
                <div key={stage.id} className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => void toggleStage(stage.id)}
                    aria-pressed={stage.done}
                    aria-label={`${stage.title} ${stage.done ? 'انجام شد' : 'باز'}`}
                    className={`grid h-5 w-5 shrink-0 place-items-center rounded-full border ${
                      stage.done
                        ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-on)]'
                        : 'border-[var(--border-strong)] text-transparent'
                    }`}
                  >
                    <Check className="h-3 w-3" strokeWidth={3} />
                  </button>
                  <span
                    className={`min-w-0 flex-1 truncate text-[11px] ${
                      stage.done ? 'text-[var(--text-3)] line-through' : 'text-[var(--text)]'
                    }`}
                  >
                    {stage.title}
                  </span>
                  <button
                    type="button"
                    onClick={() => void deleteStage(stage.id)}
                    aria-label={`حذف مرحله ${stage.title}`}
                    className="text-[var(--text-3)]"
                  >
                    <Trash2 className="h-3 w-3" />
                  </button>
                </div>
              ))}
              {ownStages.length === 0 && (
                <p className="text-[10.5px] text-[var(--text-3)]">
                  مرحله‌ای ندارد؛ درصد پیشرفت فعلاً فقط از کارها حساب می‌شود.
                </p>
              )}
            </div>
            <div className="mt-2 flex items-center gap-2">
              <input
                value={stageTitle}
                onChange={(event) => setStageTitle(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') void addStage();
                }}
                placeholder="مرحلهٔ تازه…"
                className="luxury-input py-1.5 text-[11px]"
              />
              <button
                type="button"
                onClick={() => void addStage()}
                aria-label="افزودن مرحله"
                className="grid h-7 w-7 shrink-0 place-items-center rounded-full border border-[var(--border-strong)] text-[var(--accent-fg)]"
              >
                <Plus className="h-3 w-3" />
              </button>
            </div>

            <p className="mt-3 text-[10px] text-[var(--text-3)]">
              <Numeric value={ownTasks.length} variant="geo" /> کار پیوندشده ·{' '}
              <Numeric value={ownTasks.filter((task) => task.status === 'completed').length} variant="geo" />{' '}
              تمام‌شده
            </p>
          </div>
        )}
      </div>
    </ModalSheet>
  );
}

/** Small helper so the sheet can create a stage with a stable id shape. */
export const makeStageId = () => createId('stg');
