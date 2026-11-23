import { Capacitor, CapacitorHttp } from '@capacitor/core';

/**
 * One place where the cloud manager's bytes leave the device.
 *
 * Why this exists: the first version used `fetch` inside the WebView, and on a
 * real phone that fails with nothing but «Failed to fetch» — the WebView is a
 * browser, so the provider's CORS policy decides whether the request is allowed,
 * and several providers refuse a `Authorization` preflight from an app origin.
 * The same code works on the desktop preview and fails on the phone, which is the
 * worst possible bug shape.
 *
 * `CapacitorHttp` performs the request in Java instead of in the browser: no CORS,
 * no preflight, no mixed-content block, and the response is the same JSON. On the
 * web (the preview build) it falls back to `fetch`, so the two environments share
 * one entry point and one error vocabulary.
 */

export type Transport = 'native' | 'web';

export interface HttpResult {
  status: number;
  ok: boolean;
  data: unknown;
  /** Set when the request never reached the server (DNS, offline, TLS, blocked). */
  transportError?: string;
  via: Transport;
}

/** Trim quotes and whitespace people copy along with a key. */
export function cleanSecret(value: string): string {
  return value
    .trim()
    .replace(/^["'“”]+|["'“”]+$/g, '')
    .replace(/\s+/g, '');
}

/** Accept `openrouter.ai/api/v1` as well as a full URL; never send a relative one. */
export function normalizeBaseUrl(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, '');
  if (!trimmed) return '';
  if (/^https?:\/\//i.test(trimmed)) return trimmed;
  return `https://${trimmed}`;
}

export function completionsEndpoint(baseUrl: string): string {
  const base = normalizeBaseUrl(baseUrl);
  if (!base) return '';
  return base.endsWith('/chat/completions') ? base : `${base}/chat/completions`;
}

export function isNativePlatform(): boolean {
  try {
    return Capacitor.isNativePlatform();
  } catch {
    return false;
  }
}

export async function postJson(params: {
  url: string;
  headers: Record<string, string>;
  body: unknown;
}): Promise<HttpResult> {
  const { url, headers, body } = params;
  const via: Transport = isNativePlatform() ? 'native' : 'web';

  if (via === 'native') {
    try {
      const response = await CapacitorHttp.post({
        url,
        headers,
        data: body,
        connectTimeout: 20_000,
        readTimeout: 45_000,
      });
      const status = response.status ?? 0;
      return {
        status,
        ok: status >= 200 && status < 300,
        data: response.data ?? null,
        via,
      };
    } catch (error) {
      return {
        status: 0,
        ok: false,
        data: null,
        transportError: (error as Error)?.message ?? String(error),
        via,
      };
    }
  }

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
    let data: unknown = null;
    try {
      data = await response.json();
    } catch {
      data = null;
    }
    return { status: response.status, ok: response.ok, data, via };
  } catch (error) {
    return {
      status: 0,
      ok: false,
      data: null,
      transportError: (error as Error)?.message ?? String(error),
      via,
    };
  }
}
