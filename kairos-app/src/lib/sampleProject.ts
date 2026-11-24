import type { Project, ProjectStage, Task } from '@/types/domain';
import { db, DEFAULT_SETTINGS } from './db';
import { addDays, getTodayKey } from './date';

export const SAMPLE_PROJECT_ID = 'sample-v1:project-plan';

/** Called inside a transaction that includes projects, stages and tasks. */
export async function insertSampleProject(today = getTodayKey()): Promise<void> {
  const now = new Date().toISOString();
  const project: Project = {
    id: SAMPLE_PROJECT_ID, sample: true, goalId: null, title: 'نمونه · برنامه‌ریزی یک پروژه',
    description: 'پروژهٔ تمرینی ساختگی برای آشنایی با مرحله‌ها و کارهای مرتبط.',
    category: 'work', status: 'active', startDate: today, dueDate: addDays(today, 14), createdAt: now, updatedAt: now,
  };
  const stages: ProjectStage[] = [
    { id: 'sample-v1:stage-plan', projectId: project.id, sample: true, title: 'نمونه · تعریف هدف و برنامه', kind: 'stage', order: 0, weight: 1, done: true, completedAt: now, dueDate: today, createdAt: now, updatedAt: now },
    { id: 'sample-v1:stage-work', projectId: project.id, sample: true, title: 'نمونه · انجام کارهای اصلی', kind: 'stage', order: 1, weight: 1, done: false, completedAt: null, dueDate: addDays(today, 7), createdAt: now, updatedAt: now },
    { id: 'sample-v1:stage-finish', projectId: project.id, sample: true, title: 'نمونه · مرور و پایان', kind: 'milestone', order: 2, weight: 1, done: false, completedAt: null, dueDate: addDays(today, 14), createdAt: now, updatedAt: now },
  ];
  const common = {
    sample: true as const, projectId: project.id, description: 'این کار صرفاً برای آشنایی است.',
    dueTime: null, priority: 'medium' as const, quadrant: 'q2' as const, category: 'work' as const,
    tags: ['نمونه'], subtasks: [], recurrence: 'none' as const, reminderEnabled: false,
    reminderOffsetMin: 0, archived: false, postponeCount: 0, createdAt: now, updatedAt: now,
  };
  const tasks: Task[] = [
    { ...common, id: 'sample-v1:project-task-plan', title: 'نمونه · تعریف محدودهٔ پروژه', dueDate: today, status: 'completed', completedDate: today, completedAt: now },
    { ...common, id: 'sample-v1:project-task-work', title: 'نمونه · انجام نخستین قدم', dueDate: addDays(today, 2), status: 'todo', completedDate: null, completedAt: null },
  ];
  // add/bulkAdd intentionally abort on *any* collision rather than overwrite.
  await db.projects.add(project);
  await db.projectStages.bulkAdd(stages);
  await db.tasks.bulkAdd(tasks);
}

/** Add a project for people who already loaded older sample packs. */
export async function addSampleProject(today = getTodayKey()): Promise<void> {
  await db.transaction('rw', [db.projects, db.projectStages, db.tasks, db.settings], async () => {
    if (await db.projects.get(SAMPLE_PROJECT_ID)) throw new Error('sample-project-exists');
    await insertSampleProject(today);
    const current = await db.settings.get('default');
    await db.settings.put({ ...DEFAULT_SETTINGS, ...current, id: 'default', hasSampleData: true });
  });
}
