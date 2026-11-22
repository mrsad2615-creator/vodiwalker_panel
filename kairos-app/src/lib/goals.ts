import type { Goal, Project, ProjectStage, Task } from '@/types/domain';
import { goalProgressFromProjects } from './projects';
import { CATEGORY_META } from './format';

/**
 * Goal progress, measured twice.
 *
 * A goal used to be a number you typed: `currentValue` out of `targetValue`. That
 * is a wish with a progress bar. Now a task can point at a goal, and when it
 * does, the goal stops being self-reported and starts being *earned*: complete
 * the work, the bar moves. Manual entry stays for goals that have no task-shaped
 * work (read 12 books), because forcing every goal into tasks is its own lie.
 */

export type GoalProgressMode = 'linked' | 'projects' | 'manual' | 'untracked';

export interface GoalProgress {
  mode: GoalProgressMode;
  percent: number;
  /** Linked tasks only. */
  done: number;
  total: number;
  /** Manual goals only, clamped for display. */
  current: number;
  target: number;
}

export function tasksForGoal(goalId: string, tasks: Task[]): Task[] {
  return tasks.filter((task) => task.goalId === goalId);
}

export function computeGoalProgress(
  goal: Goal,
  tasks: Task[],
  /** Projects that point at this goal. Optional, so every old call still works. */
  projectsView?: { projects: Project[]; stages: ProjectStage[] }
): GoalProgress {
  const linked = tasks.filter((task) => task.goalId === goal.id && !task.archived);

  if (linked.length > 0) {
    const done = linked.filter((task) => task.status === 'completed').length;
    return {
      mode: 'linked',
      percent: Math.round((done / linked.length) * 100),
      done,
      total: linked.length,
      current: done,
      target: linked.length,
    };
  }

  // Second measurement: a goal whose work lives in projects inherits their
  // weighted progress instead of falling back to a typed number.
  if (projectsView) {
    const own = projectsView.projects.filter(
      (project) => project.goalId === goal.id && project.status !== 'archived'
    );
    const fromProjects = goalProgressFromProjects(
      own.map((project) => project.id),
      projectsView.stages,
      tasks
    );
    if (fromProjects) {
      return {
        mode: 'projects',
        percent: goal.completed ? 100 : fromProjects.percent,
        done: 0,
        total: 0,
        current: fromProjects.percent,
        target: 100,
      };
    }
  }

  const target = Math.max(0, goal.targetValue);
  const current = Math.max(0, goal.currentValue);
  if (target <= 0) {
    return { mode: 'untracked', percent: goal.completed ? 100 : 0, done: 0, total: 0, current, target };
  }

  return {
    mode: 'manual',
    percent: goal.completed ? 100 : Math.min(100, Math.round((current / target) * 100)),
    done: 0,
    total: 0,
    current,
    target,
  };
}

/** One honest line under the bar: what the number is made of. */
export function goalProgressCaption(goal: Goal, progress: GoalProgress): string {
  if (progress.mode === 'linked') {
    return `${progress.done} از ${progress.total} کار`;
  }
  if (progress.mode === 'projects') {
    return `از پروژه‌های این هدف · ${progress.percent}%`;
  }
  if (progress.mode === 'manual') {
    return `${progress.current} از ${progress.target}${goal.unit ? ` ${goal.unit}` : ''}`;
  }
  return 'هدف بدون اندازه‌گیری';
}

/** The next unlinkable / actionable thing, if the goal is still open. */
export function nextOpenTaskForGoal(goalId: string, tasks: Task[]): Task | null {
  return (
    tasks
      .filter((task) => task.goalId === goalId && !task.archived && task.status !== 'completed')
      .sort((a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999'))[0] ?? null
  );
}

export function goalCategoryLabel(goal: Goal): string {
  return CATEGORY_META[goal.category]?.label ?? 'عمومی';
}
