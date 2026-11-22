import { Capacitor } from '@capacitor/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import type { KairosBackupPayload } from '@/types/domain';

/**
 * Backup export that actually works on Android.
 *
 * Inside a WebView an `<a download>` click does nothing, which is why the old
 * export silently failed on the phone. On native we write the JSON to the app
 * cache directory and open the system share sheet (Drive, Telegram, Files, …);
 * on the web we keep the classic blob download.
 */

export interface BackupSaveResult {
  ok: boolean;
  via: 'share' | 'download' | 'error';
  /** Where the file went, when the platform tells us. */
  location?: string;
  error?: string;
}

export function buildBackupFileName(now: Date = new Date()): string {
  const stamp = now.toISOString().slice(0, 10);
  return `kairos-backup-${stamp}.json`;
}

function toBase64(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

/**
 * Saving a text file the way Android actually allows: write to cache, hand the
 * URI to the share sheet. Used by the JSON backup and by the CSV export, because
 * both have to survive the same WebView limitation.
 */
export async function saveTextFile(
  fileName: string,
  text: string,
  mime: string,
  shareTitle: string
): Promise<BackupSaveResult> {
  if (Capacitor.isNativePlatform()) {
    try {
      const written = await Filesystem.writeFile({
        path: fileName,
        data: toBase64(text),
        directory: Directory.Cache,
        recursive: true,
      });

      try {
        await Share.share({
          title: shareTitle,
          url: written.uri,
          dialogTitle: 'ذخیره یا ارسال فایل',
        });
        return { ok: true, via: 'share', location: written.uri };
      } catch {
        // The user closed the share sheet — the file still exists in cache.
        return { ok: true, via: 'share', location: written.uri };
      }
    } catch (error) {
      return { ok: false, via: 'error', error: error instanceof Error ? error.message : 'unknown' };
    }
  }

  try {
    const blob = new Blob([text], { type: mime });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.rel = 'noopener';
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 2000);
    return { ok: true, via: 'download', location: fileName };
  } catch (error) {
    return { ok: false, via: 'error', error: error instanceof Error ? error.message : 'unknown' };
  }
}

export async function exportBackupFile(payload: KairosBackupPayload): Promise<BackupSaveResult> {
  return saveTextFile(
    buildBackupFileName(),
    JSON.stringify(payload, null, 2),
    'application/json',
    'پشتیبان KAIROS'
  );
}

/** `File.text()` is missing in older WebViews and in jsdom — fall back to FileReader. */
export async function readFileText(file: File): Promise<string> {
  if (typeof file.text === 'function') return file.text();
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('read-failed'));
    reader.readAsText(file);
  });
}

/** Accepts a backup file from an <input type="file"> or the share sheet. */
export async function readBackupFile(file: File): Promise<KairosBackupPayload> {
  const raw = await readFileText(file);
  const parsed = JSON.parse(raw) as KairosBackupPayload;
  if (!parsed || typeof parsed !== 'object' || !('version' in parsed)) {
    throw new Error('invalid-backup');
  }
  return parsed;
}
