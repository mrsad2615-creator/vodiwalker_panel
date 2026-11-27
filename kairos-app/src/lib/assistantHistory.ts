import { db } from './db';
import type { AssistantHistoryEntry } from '@/types/domain';

/** Delete only the exact messages the user reviewed when confirming.
 * Concurrent additions survive; modified/deleted rows abort the whole operation. */
export async function deleteAssistantHistorySnapshot(snapshot: AssistantHistoryEntry[]): Promise<void> {
  const ids = snapshot.map((entry) => entry.id);
  if (new Set(ids).size !== ids.length) throw new Error('history-changed');
  await db.transaction('rw', db.assistantHistory, async () => {
    const latest = await db.assistantHistory.bulkGet(ids);
    if (latest.some((entry, index) => JSON.stringify(entry) !== JSON.stringify(snapshot[index]))) throw new Error('history-changed');
    await db.assistantHistory.bulkDelete(ids);
  });
}
