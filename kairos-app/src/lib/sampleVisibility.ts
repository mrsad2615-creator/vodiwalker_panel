import { db, ensureSettings } from './db';
import { populateStarterWorkspace } from './seed';

/** A choice never deletes rows. Creation and the initial decision commit together. */
export async function chooseSampleVisibility(visible: boolean, completeSetup = false): Promise<void> {
  const current = await ensureSettings();
  if (visible && !current.hasSampleData) {
    await populateStarterWorkspace(undefined, completeSetup);
    return;
  }
  // Update atomically with respect to other settings writes; no data rows touched.
  await db.transaction('rw', db.settings, async () => {
    const row = await db.settings.get('default');
    if (!row) throw new Error('settings-missing');
    await db.settings.put({ ...row, sampleVisible: visible, ...(completeSetup ? { sampleSetupComplete: true } : {}), updatedAt: new Date().toISOString() });
  });
}
