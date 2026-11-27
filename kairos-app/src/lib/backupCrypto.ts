import type { KairosBackupPayload } from '@/types/domain';
import { validateBackup } from '@/lib/backupValidation';

/** Independent envelope version; the JSON payload keeps its own backup version. */
export interface EncryptedBackup {
  format: 'KAIROS-ENCRYPTED';
  version: 1;
  kdf: 'PBKDF2-SHA256';
  iterations: number;
  cipher: 'AES-256-GCM';
  salt: string;
  iv: string;
  ciphertext: string;
}

const ITERATIONS = 310_000;
const MAX_CIPHERTEXT_BYTES = 64 * 1024 * 1024;
const encoder = new TextEncoder();

function cryptoApi(): Crypto {
  if (!globalThis.crypto?.subtle || !globalThis.crypto.getRandomValues) throw new Error('backup-crypto-unavailable');
  return globalThis.crypto;
}
function encodeBase64(bytes: Uint8Array): string {
  let result = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    result += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(result);
}
function decodeBase64(value: unknown, maxBytes: number): Uint8Array<ArrayBuffer> {
  if (typeof value !== 'string' || value.length > Math.ceil(maxBytes / 3) * 4 ||
      !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(value)) throw new Error('invalid-encrypted-backup');
  let binary: string;
  try { binary = atob(value); } catch { throw new Error('invalid-encrypted-backup'); }
  if (binary.length > maxBytes) throw new Error('invalid-encrypted-backup');
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}
function header(envelope: EncryptedBackup): string {
  // Construct canonical field order; headers are authenticated as additional data.
  return JSON.stringify({ format: envelope.format, version: envelope.version, kdf: envelope.kdf,
    iterations: envelope.iterations, cipher: envelope.cipher, salt: envelope.salt, iv: envelope.iv });
}
async function keyFor(password: string, salt: Uint8Array<ArrayBuffer>, iterations: number): Promise<CryptoKey> {
  if (!password || password.length > 1024) throw new Error('invalid-backup-password');
  const subtle = cryptoApi().subtle;
  const material = await subtle.importKey('raw', encoder.encode(password), 'PBKDF2', false, ['deriveKey']);
  return subtle.deriveKey({ name: 'PBKDF2', salt, iterations, hash: 'SHA-256' }, material,
    { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}

export function isEncryptedBackup(value: unknown): value is EncryptedBackup {
  return !!value && typeof value === 'object' && !Array.isArray(value) && (value as Record<string, unknown>).format === 'KAIROS-ENCRYPTED';
}

/** Password is never persisted; losing it makes the encrypted backup unrecoverable. */
export async function encryptBackup(payload: KairosBackupPayload, password: string): Promise<EncryptedBackup> {
  validateBackup(payload);
  const api = cryptoApi();
  const salt = api.getRandomValues(new Uint8Array(16));
  const iv = api.getRandomValues(new Uint8Array(12));
  const envelope: EncryptedBackup = { format: 'KAIROS-ENCRYPTED', version: 1, kdf: 'PBKDF2-SHA256',
    iterations: ITERATIONS, cipher: 'AES-256-GCM', salt: encodeBase64(salt), iv: encodeBase64(iv), ciphertext: '' };
  const plain = encoder.encode(JSON.stringify(payload));
  if (plain.byteLength + 16 > MAX_CIPHERTEXT_BYTES) throw new Error('backup-too-large');
  const key = await keyFor(password, salt, ITERATIONS);
  const encrypted = await api.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: encoder.encode(header(envelope)), tagLength: 128 }, key, plain);
  envelope.ciphertext = encodeBase64(new Uint8Array(encrypted));
  return envelope;
}

/** Check envelope metadata before key derivation to bound untrusted KDF work. */
export async function decryptBackup(value: unknown, password: string): Promise<KairosBackupPayload> {
  if (!isEncryptedBackup(value) || value.version !== 1 || value.kdf !== 'PBKDF2-SHA256' || value.cipher !== 'AES-256-GCM' ||
      !Number.isInteger(value.iterations) || value.iterations < 100_000 || value.iterations > 1_000_000) throw new Error('invalid-encrypted-backup');
  const salt = decodeBase64(value.salt, 16);
  const iv = decodeBase64(value.iv, 12);
  const ciphertext = decodeBase64(value.ciphertext, MAX_CIPHERTEXT_BYTES);
  if (salt.length !== 16 || iv.length !== 12 || ciphertext.length < 16) throw new Error('invalid-encrypted-backup');
  const key = await keyFor(password, salt, value.iterations);
  let decrypted: ArrayBuffer;
  try {
    decrypted = await cryptoApi().subtle.decrypt({ name: 'AES-GCM', iv, additionalData: encoder.encode(header(value)), tagLength: 128 }, key, ciphertext);
  } catch { throw new Error('backup-decryption-failed'); }
  let parsed: unknown;
  try { parsed = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(decrypted)); }
  catch { throw new Error('invalid-backup'); }
  validateBackup(parsed);
  return parsed;
}
