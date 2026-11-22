import type { UserSettings } from '@/types/domain';
import { AI_TOOLS, executeAiTool, type ToolCall } from './tools';

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
  return Boolean(settings.aiEnabled && settings.aiKey.trim() && settings.aiBaseUrl.trim() && settings.aiModel.trim());
}

export const AI_MODEL_HINT =
  'نمونه: openai/gpt-4o-mini · anthropic/claude-3.5-sonnet · llama-3.3-70b-versatile';

const TIMEOUT_MS = 45_000;
const MAX_TOOL_ROUNDS = 3;

function endpoint(baseUrl: string): string {
  const trimmed = baseUrl.trim().replace(/\/+$/, '');
  return trimmed.endsWith('/chat/completions') ? trimmed : `${trimmed}/chat/completions`;
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

  const fetchImpl: FetchLike = params.fetchImpl ?? (globalThis.fetch as unknown as FetchLike);
  // The transcript is trimmed: a long chat must not turn into a long bill, and
  // the model only needs the last few exchanges to stay coherent.
  const messages: ChatMessage[] = [...history.slice(-8), { role: 'user', content: userText }];
  const steps: AiTurnResult['steps'] = [];
  let navigateTo: string | undefined;

  for (let round = 0; round < MAX_TOOL_ROUNDS; round += 1) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    let payload: CompletionResponse;

    try {
      const response = await fetchImpl(endpoint(settings.aiBaseUrl), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${settings.aiKey.trim()}`,
        },
        body: JSON.stringify({
          model: settings.aiModel.trim(),
          messages,
          tools: AI_TOOLS,
          tool_choice: 'auto',
          temperature: 0.3,
        }),
        signal: controller.signal,
      });

      if (!response.ok) {
        let detail = '';
        try {
          const body = (await response.json()) as CompletionResponse;
          detail = body?.error?.message ?? '';
        } catch {
          detail = '';
        }
        return { ok: false, reply: '', steps, error: persianError(response.status, detail) };
      }
      payload = (await response.json()) as CompletionResponse;
    } catch (error) {
      const aborted = (error as Error)?.name === 'AbortError';
      return {
        ok: false,
        reply: '',
        steps,
        error: aborted
          ? 'پاسخ سرویس دیر آمد و لغو شد. دوباره بپرس.'
          : 'اتصال برقرار نشد. اینترنت یا آدرس سرویس را بررسی کن.',
      };
    } finally {
      clearTimeout(timer);
    }

    const message = payload.choices?.[0]?.message;
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
