import type { UserSettings } from '@/types/domain';
import { AI_TOOLS, executeAiTool, type ToolCall } from './tools';
import {
  cleanSecret,
  completionsEndpoint,
  isNativePlatform,
  postJson,
  type HttpResult,
} from './transport';

/**
 * The cloud half of the manager.
 *
 * The contract that matters (and that makes this safe to ship):
 *   • Nothing happens unless the user turned it on and typed a key. With the
 *     switch off, this module is never called and the offline engine answers.
 *   • The model cannot touch the app by itself. It can only *ask* for a tool;
 *     `executeAiTool` decides what that means, and every write goes through the
 *     normal db layer (event ledger + undo).
 *   • The loop is bounded. Two tool rounds, then the model must answer in words.
 *   • Every failure is a Persian sentence the user can act on, never a stack trace.
 */

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant' | 'tool';
  content: string;
  tool_call_id?: string;
  tool_calls?: RawToolCall[];
}

interface RawToolCall {
  id: string;
  type: 'function';
  function: { name: string; arguments: string };
}

export interface AiTurnResult {
  ok: boolean;
  /** The assistant's final words, Persian. */
  reply: string;
  /** Tools the model asked for, in order, with what they did. */
  steps: { name: string; summary: string; ok: boolean }[];
  /** Tab to open, if a navigate tool was used. */
  navigateTo?: string;
  error?: string;
}

export type FetchLike = (
  input: string,
  init?: {
    method?: string;
    headers?: Record<string, string>;
    body?: string;
    signal?: AbortSignal;
  }
) => Promise<{ ok: boolean; status: number; json: () => Promise<unknown>; text?: () => Promise<string> }>;

export function isAiReady(settings: UserSettings): boolean {
  return Boolean(
    settings.aiEnabled &&
      cleanSecret(settings.aiKey) &&
      settings.aiBaseUrl.trim() &&
      settings.aiModel.trim()
  );
}

export const AI_MODEL_HINT =
  'نمونه: openai/gpt-4o-mini · anthropic/claude-3.5-sonnet · llama-3.3-70b-versatile';

const TIMEOUT_MS = 45_000;
const MAX_TOOL_ROUNDS = 3;

function endpoint(baseUrl: string): string {
  return completionsEndpoint(baseUrl);
}

/** Headers for any OpenAI-compatible provider, plus the two OpenRouter likes. */
function requestHeaders(settings: UserSettings, url: string): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${cleanSecret(settings.aiKey)}`,
  };
  if (url.includes('openrouter.ai')) {
    headers['HTTP-Referer'] = 'https://kairos.app';
    headers['X-Title'] = 'KAIROS';
  }
  return headers;
}

/** A transport failure is a different animal from an HTTP error; say so. */
function transportMessage(detail: string | undefined): string {
  const native = isNativePlatform();
  const suffix = detail ? ` (${detail.slice(0, 120)})` : '';
  return native
    ? `اتصال برقرار نشد؛ درخواست حتی به سرور نرسید${suffix}. بررسی کن: اینترنت گوشی، درست بودن آدرس سرویس، و اینکه سرویس از ایران پاسخ می‌دهد.`
    : `اتصال برقرار نشد؛ مرورگر اجازهٔ این درخواست را نداد${suffix}. در نسخهٔ نصب‌شده روی گوشی این محدودیت وجود ندارد.`;
}

interface CompletionResponse {
  choices?: {
    message?: { content?: string | null; tool_calls?: RawToolCall[] };
    finish_reason?: string;
  }[];
  error?: { message?: string };
}

function persianError(status: number, detail: string): string {
  if (status === 401 || status === 403) return 'کلید API رد شد. کلید را در تنظیمات بررسی کن.';
  if (status === 404) return 'آدرس سرویس یا نام مدل پیدا نشد. هر دو را در تنظیمات بررسی کن.';
  if (status === 429) return 'سهمیهٔ سرویس تمام شده یا درخواست‌ها زیاد بوده؛ کمی بعد دوباره تلاش کن.';
  if (status >= 500) return 'سرویس الان پاسخ درست نمی‌دهد؛ چند دقیقه بعد تلاش کن.';
  return detail ? `خطای سرویس: ${detail}` : `خطای سرویس (کد ${status}).`;
}

/** A request through an injected fetch (tests, and the web preview). */
async function webRequest(
  fetchImpl: FetchLike,
  url: string,
  headers: Record<string, string>,
  body: unknown
): Promise<HttpResult> {
  try {
    const response = await fetchImpl(url, {
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
    return { status: response.status, ok: response.ok, data, via: 'web' };
  } catch (error) {
    return {
      status: 0,
      ok: false,
      data: null,
      transportError: (error as Error)?.message ?? String(error),
      via: 'web',
    };
  }
}

/** Native HTTP has its own timeouts; this guards the promise itself. */
function withTimeout(promise: Promise<HttpResult>, ms = TIMEOUT_MS): Promise<HttpResult> {
  return Promise.race([
    promise,
    new Promise<HttpResult>((resolve) =>
      setTimeout(
        () =>
          resolve({ status: 0, ok: false, data: null, transportError: 'timeout', via: 'native' }),
        ms
      )
    ),
  ]);
}

/** One assistant turn: send, run the tools it asks for, send the results back. */
export async function runAiTurn(params: {
  settings: UserSettings;
  history: ChatMessage[];
  userText: string;
  /** Injected in tests; defaults to the platform fetch. */
  fetchImpl?: FetchLike;
  /** Ask before executing writes. Returns false to refuse that single tool. */
  confirmWrite?: (call: ToolCall) => Promise<boolean>;
}): Promise<AiTurnResult> {
  const { settings, history, userText } = params;
  if (!isAiReady(settings)) {
    return { ok: false, reply: '', steps: [], error: 'مدیر ابری فعال نیست.' };
  }

  const messages: ChatMessage[] = [...history.slice(-8), { role: 'user', content: userText }];
  const steps: AiTurnResult['steps'] = [];
  let navigateTo: string | undefined;
  // Some providers (and many local models) reject `tools` outright. Instead of
  // failing the whole conversation, the first refusal drops the tools and asks
  // again — the model can still answer from the summary it was given.
  let toolsDisabled = false;

  const url = endpoint(settings.aiBaseUrl);
  if (!url) {
    return { ok: false, reply: '', steps, error: 'آدرس سرویس خالی است. در تنظیمات یک سرویس انتخاب کن.' };
  }

  const requestBody = () => ({
    model: settings.aiModel.trim(),
    messages,
    temperature: 0.3,
    ...(toolsDisabled ? {} : { tools: AI_TOOLS, tool_choice: 'auto' }),
  });

  for (let round = 0; round < MAX_TOOL_ROUNDS; round += 1) {
    const payloadResult = params.fetchImpl
      ? await webRequest(params.fetchImpl, url, requestHeaders(settings, url), requestBody())
      : await withTimeout(
          postJson({
            url,
            headers: requestHeaders(settings, url),
            body: requestBody(),
          })
        );

    if (payloadResult.transportError !== undefined || payloadResult.status === 0) {
      const timedOut = (payloadResult.transportError ?? '').toLowerCase().includes('time');
      return {
        ok: false,
        reply: '',
        steps,
        error: timedOut
          ? 'پاسخ سرویس دیر آمد و لغو شد. دوباره بپرس.'
          : transportMessage(payloadResult.transportError),
      };
    }

    const payload = payloadResult.data as CompletionResponse | null;

    if (!payloadResult.ok) {
      if (!toolsDisabled && (payloadResult.status === 400 || payloadResult.status === 422)) {
        toolsDisabled = true;
        continue;
      }
      const detail = payload?.error?.message ?? '';
      return {
        ok: false,
        reply: '',
        steps,
        error: persianError(payloadResult.status, detail),
      };
    }

    if (!payload) {
      return { ok: false, reply: '', steps, error: 'پاسخ سرویس خالی بود.' };
    }

    const message = payload?.choices?.[0]?.message;
    if (!message) {
      return { ok: false, reply: '', steps, error: 'پاسخ سرویس خالی بود.' };
    }

    const toolCalls = message.tool_calls ?? [];
    if (toolCalls.length === 0) {
      return {
        ok: true,
        reply: (message.content ?? '').trim(),
        steps,
        navigateTo,
      };
    }

    messages.push({ role: 'assistant', content: message.content ?? '', tool_calls: toolCalls });

    for (const call of toolCalls) {
      let args: Record<string, unknown> = {};
      try {
        args = call.function.arguments ? (JSON.parse(call.function.arguments) as Record<string, unknown>) : {};
      } catch {
        args = {};
      }

      const allowed =
        params.confirmWrite && isWriteTool(call.function.name)
          ? await params.confirmWrite({
              id: call.id,
              name: call.function.name as never,
              args,
            })
          : true;

      if (!allowed) {
        steps.push({ name: call.function.name, ok: false, summary: 'کاربر اجرای این تغییر را رد کرد.' });
        messages.push({
          role: 'tool',
          tool_call_id: call.id,
          content: JSON.stringify({ ok: false, refused: true, summary: 'کاربر این تغییر را رد کرد.' }),
        });
        continue;
      }

      const result = await executeAiTool(call.function.name, args);
      steps.push({ name: call.function.name, ok: result.ok, summary: result.summary });
      if (call.function.name === 'navigate' && result.ok) {
        navigateTo = String((result.data as { tab?: string })?.tab ?? '');
      }
      messages.push({
        role: 'tool',
        tool_call_id: call.id,
        content: JSON.stringify({ ok: result.ok, summary: result.summary, data: result.data ?? null }).slice(
          0,
          6000
        ),
      });
    }
  }

  return {
    ok: true,
    reply: steps.length
      ? 'انجام شد. اگر کاری مانده بگو.'
      : 'نتوانستم به نتیجه برسم؛ سؤال را کوتاه‌تر بپرس.',
    steps,
    navigateTo,
  };
}

function isWriteTool(name: string): boolean {
  return [
    'create_task',
    'update_task',
    'complete_task',
    'create_project',
    'add_stage',
    'toggle_stage',
    'log_habit',
    'update_settings',
    'create_tasks',
    'reschedule_overdue',
    'postpone_task',
    // An undo is a write, but an obviously safe one: it only restores a row.
    'undo_last',
  ].includes(name);
}

/** Cheap connectivity check for the settings screen. */
export async function testAiConnection(params: {
  settings: UserSettings;
  fetchImpl?: FetchLike;
}): Promise<{ ok: boolean; message: string }> {
  if (!isAiReady(params.settings)) {
    return { ok: false, message: 'اول کلید، آدرس سرویس و نام مدل را پر کن.' };
  }
  const result = await runAiTurn({
    settings: params.settings,
    history: [],
    userText: 'فقط بنویس: آماده‌ام',
    fetchImpl: params.fetchImpl,
  });
  if (!result.ok) return { ok: false, message: result.error ?? 'اتصال برقرار نشد.' };
  return { ok: true, message: 'اتصال برقرار است.' };
}


/* ── Diagnosis ─────────────────────────────────────────────────────────────── */

export interface AiDiagnosis {
  ready: boolean;
  via: 'native' | 'web';
  url: string;
  model: string;
  keyLooksValid: boolean;
  /** Did the request reach the provider at all? */
  reached: boolean;
  status: number;
  /** Raw technical text, for the user to paste back when asking for help. */
  detail: string;
  /** What to do about it, in Persian. */
  hint: string;
}

/**
 * A real request whose only job is to explain what went wrong.
 *
 * The generic failure message was useless — «اتصال برقرار نشد» cannot tell a
 * wrong URL from a blocked request from a rejected key. This sends one tiny
 * completion and reports the layer that broke, so the answer is actionable
 * without a debugger.
 */
export async function diagnoseAi(
  settings: UserSettings,
  fetchImpl?: FetchLike
): Promise<AiDiagnosis> {
  const url = completionsEndpoint(settings.aiBaseUrl);
  const via = isNativePlatform() ? 'native' : 'web';
  const base: AiDiagnosis = {
    ready: isAiReady(settings),
    via,
    url: url || '(خالی)',
    model: settings.aiModel.trim() || '(خالی)',
    keyLooksValid: cleanSecret(settings.aiKey).length >= 20,
    reached: false,
    status: 0,
    detail: '',
    hint: '',
  };

  if (!base.ready) {
    return { ...base, hint: 'اول کلید، آدرس سرویس و نام مدل را پر کن و «ذخیرهٔ مدیر ابری» را بزن.' };
  }

  const body = {
    model: settings.aiModel.trim(),
    messages: [{ role: 'user', content: 'سلام' }],
    max_tokens: 8,
  };

  const result = fetchImpl
    ? await webRequest(fetchImpl, url, requestHeaders(settings, url), body)
    : await withTimeout(postJson({ url, headers: requestHeaders(settings, url), body }));

  if (result.status === 0) {
    return {
      ...base,
      detail: result.transportError ?? 'بدون جزئیات',
      hint: transportMessage(result.transportError),
    };
  }

  const payload = result.data as CompletionResponse | null;
  const providerDetail = payload?.error?.message ?? '';

  if (result.ok) {
    return {
      ...base,
      reached: true,
      status: result.status,
      detail: 'پاسخ سالم دریافت شد.',
      hint: 'اتصال سالم است. اگر در گفت‌وگو خطا دیدی، نام مدل را بررسی کن.',
    };
  }

  const hints: Record<number, string> = {
    400: 'درخواست پذیرفته نشد. معمولاً نام مدل اشتباه است یا این مدل ابزار (tools) را پشتیبانی نمی‌کند.',
    401: 'کلید رد شد. کلید را از سایت سرویس دوباره کپی کن (بدون فاصله و کوتیشن).',
    403: 'کلید اجازهٔ این مدل را ندارد یا حساب محدود است.',
    404: 'آدرس سرویس یا نام مدل پیدا نشد. آدرس باید به /v1 ختم شود.',
    422: 'بدنهٔ درخواست پذیرفته نشد؛ معمولاً نام مدل یا پارامتر tools.',
    429: 'سهمیه/نرخ درخواست پر است. کمی بعد یا با کلید دیگری تلاش کن.',
    503: 'سرویس موقتاً در دسترس نیست.',
  };

  return {
    ...base,
    reached: true,
    status: result.status,
    detail: providerDetail || `HTTP ${result.status}`,
    hint: hints[result.status] ?? persianError(result.status, providerDetail),
  };
}
