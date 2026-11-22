import type { Project, ProjectStage, Task } from '@/types/domain';
import { diffDays } from './date';

/**
 * Project intelligence.
 *
 * Progress is deliberately *not* "completed tasks ÷ all tasks". A project with
 * three finished stages and forty tiny tasks is not 7% done. So:
 *
 *   · stages carry weight (a milestone can weigh more than a chore);
 *   · tasks contribute a second, smaller signal (40% of the bar);
 *   · blocked tasks are excluded from the "remaining work" maths but shown;
 *   · when there is not enough structure, the function says so instead of
 *     inventing a number.
 */

export interface ProjectProgress {
  percent: number;
  /** How the number was built, so the UI can explain it. */
  basis: 'stages+tasks' | 'stages' | 'tasks' | 'none';
  stagesDone: number;
  stagesTotal: number;
  tasksDone: number;
  tasksTotal: number;
  /** Weighted stage completion, 0..1 (0 when there are no stages). */
  stageRatio: number;
  taskRatio: number;
}

/** Weight of the stage component when both signals exist. */
const STAGE_SHARE = 0.6;

export function computeProjectProgress(
  stages: ProjectStage[],
  tasks: Task[]
): ProjectProgress {
  const own = stages.slice().sort((a, b) => a.order - b.order);
  const stagesTotal = own.length;
  const stagesDone = own.filter((stage) => stage.done).length;

  const live = tasks.filter((task) => !task.archived);
  const tasksTotal = live.length;
  const tasksDone = live.filter((task) => task.status === 'completed').length;

  const totalWeight = own.reduce((sum, stage) => sum + Math.max(0.1, stage.weight), 0);
  const doneWeight = own
    .filter((stage) => stage.done)
    .reduce((sum, stage) => sum + Math.max(0.1, stage.weight), 0);

  const stageRatio = totalWeight > 0 ? doneWeight / totalWeight : 0;
  const taskRatio = tasksTotal > 0 ? tasksDone / tasksTotal : 0;

  if (stagesTotal > 0 && tasksTotal > 0) {
    return {
      percent: Math.round((stageRatio * STAGE_SHARE + taskRatio * (1 - STAGE_SHARE)) * 100),
      basis: 'stages+tasks',
      stagesDone,
      stagesTotal,
      tasksDone,
      tasksTotal,
      stageRatio,
      taskRatio,
    };
  }
  if (stagesTotal > 0) {
    return {
      percent: Math.round(stageRatio * 100),
      basis: 'stages',
      stagesDone,
      stagesTotal,
      tasksDone,
      tasksTotal,
      stageRatio,
      taskRatio,
    };
  }
  if (tasksTotal > 0) {
    return {
      percent: Math.round(taskRatio * 100),
      basis: 'tasks',
      stagesDone,
      stagesTotal,
      tasksDone,
      tasksTotal,
      stageRatio,
      taskRatio,
    };
  }
  return {
    percent: 0,
    basis: 'none',
    stagesDone,
    stagesTotal,
    tasksDone,
    tasksTotal,
    stageRatio,
    taskRatio,
  };
}

export function describeBasis(progress: ProjectProgress): string {
  switch (progress.basis) {
    case 'stages+tasks':
      return `${progress.stagesDone}/${progress.stagesTotal} مرحله · ${progress.tasksDone}/${progress.tasksTotal} کار`;
    case 'stages':
      return `${progress.stagesDone} از ${progress.stagesTotal} مرحله`;
    case 'tasks':
      return `${progress.tasksDone} از ${progress.tasksTotal} کار`;
    default:
      return 'بدون مرحله و کار — پیشرفتی برای شمردن نیست';
  }
}

/** Tasks that cannot start yet because something they depend on is open. */
export function blockedTasks(tasks: Task[]): { task: Task; waitingOn: Task[] }[] {
  const byId = new Map(tasks.map((task) => [task.id, task]));
  const blocked: { task: Task; waitingOn: Task[] }[] = [];

  for (const task of tasks) {
    if (task.status === 'completed' || task.archived) continue;
    const waitingOn = (task.dependsOn ?? [])
      .map((id) => byId.get(id))
      .filter((dep): dep is Task => Boolean(dep) && dep!.status !== 'completed');
    if (waitingOn.length > 0) blocked.push({ task, waitingOn });
  }
  return blocked;
}

export interface ProjectRisk {
  projectId: string;
  title: string;
  reason: 'overdue' | 'stalled' | 'blocked' | 'behind';
  days: number;
  openTasks: number;
  percent: number;
}

/**
 * Which project is drifting, and why. Each reason is a different sentence to the
 * user, so they are kept apart rather than collapsed into one "risk" score.
 */
export function projectRisks(params: {
  projects: Project[];
  stages: ProjectStage[];
  tasks: Task[];
  todayKey: string;
}): ProjectRisk[] {
  const { projects, stages, tasks, todayKey } = params;
  const byId = new Map(tasks.map((task) => [task.id, task]));
  const risks: ProjectRisk[] = [];

  for (const project of projects) {
    if (project.status !== 'active') continue;
    const ownTasks = tasks.filter((task) => task.projectId === project.id && !task.archived);
    const ownStages = stages.filter((stage) => stage.projectId === project.id);
    const progress = computeProjectProgress(ownStages, ownTasks);
    const openTasks = ownTasks.filter((task) => task.status !== 'completed').length;

    const overdueDays = project.dueDate ? diffDays(project.dueDate, todayKey) : 0;
    if (project.dueDate && overdueDays > 0 && progress.percent < 100) {
      risks.push({
        projectId: project.id,
        title: project.title,
        reason: 'overdue',
        days: Math.abs(overdueDays),
        openTasks,
        percent: progress.percent,
      });
      continue;
    }

    const blocked = ownTasks.filter((task) => {
      if (task.status === 'completed') return false;
      return (task.dependsOn ?? []).some((id) => byId.get(id)?.status !== 'completed');
    });

    if (blocked.length > 0 && blocked.length === Math.max(1, openTasks)) {
      risks.push({
        projectId: project.id,
        title: project.title,
        reason: 'blocked',
        days: 0,
        openTasks,
        percent: progress.percent,
      });
      continue;
    }

    const lastTouch = [...ownTasks.map((task) => task.updatedAt), project.updatedAt]
      .sort()
      .reverse()[0];
    const stalledDays = lastTouch ? Math.abs(diffDays(lastTouch.slice(0, 10), todayKey)) : 0;
    if (stalledDays >= 10) {
      risks.push({
        projectId: project.id,
        title: project.title,
        reason: 'stalled',
        days: stalledDays,
        openTasks,
        percent: progress.percent,
      });
    }
  }

  return risks.sort((a, b) => b.days - a.days);
}

export function riskSentence(risk: ProjectRisk): string {
  switch (risk.reason) {
    case 'overdue':
      return `${risk.days} روز از موعد گذشته و ${risk.percent}٪ پیش رفته`;
    case 'blocked':
      return `${risk.openTasks} کار باز دارد که همه‌شان پشت یک کار دیگر قفل‌اند`;
    case 'stalled':
      return `${risk.days} روز است که تکان نخورده`;
    default:
      return 'از برنامه عقب است';
  }
}

/** Progress of a goal that owns projects: the mean of its projects, weighted by size. */
export function goalProgressFromProjects(
  projectIds: string[],
  stages: ProjectStage[],
  tasks: Task[]
): { percent: number; projects: number } | null {
  if (projectIds.length === 0) return null;
  const perProject = projectIds.map((id) =>
    computeProjectProgress(
      stages.filter((stage) => stage.projectId === id),
      tasks.filter((task) => task.projectId === id)
    )
  );
  const usable = perProject.filter((progress) => progress.basis !== 'none');
  if (usable.length === 0) return null;
  const percent = Math.round(
    usable.reduce((sum, progress) => sum + progress.percent, 0) / usable.length
  );
  return { percent, projects: usable.length };
}
