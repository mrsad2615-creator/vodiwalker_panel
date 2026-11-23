/**
 * Where to get a key, and what to type once you have one.
 *
 * The point of presets is that nobody should have to guess a base URL. Pick a
 * provider, press «دریافت کلید», and the address and a known-good model name are
 * filled in — the only thing left is the key itself.
 */

export interface AiProvider {
  id: string;
  name: string;
  /** OpenAI-compatible base URL. */
  baseUrl: string;
  /** A model that exists today and is cheap enough to try. */
  model: string;
  /** Where the user gets a key. */
  keyUrl: string;
  /** One honest line about the trade. */
  note: string;
}

export const AI_PROVIDERS: AiProvider[] = [
  {
    id: 'openrouter',
    name: 'OpenRouter',
    baseUrl: 'https://openrouter.ai/api/v1',
    model: 'openai/gpt-4o-mini',
    keyUrl: 'https://openrouter.ai/keys',
    note: 'یک کلید برای ده‌ها مدل؛ پرداخت به‌ازای مصرف و چند مدل رایگان.',
  },
  {
    id: 'groq',
    name: 'Groq',
    baseUrl: 'https://api.groq.com/openai/v1',
    model: 'llama-3.3-70b-versatile',
    keyUrl: 'https://console.groq.com/keys',
    note: 'سریع‌ترین پاسخ؛ سهمیهٔ رایگان روزانه دارد.',
  },
  {
    id: 'openai',
    name: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
    keyUrl: 'https://platform.openai.com/api-keys',
    note: 'کیفیت بالا و پایدار؛ نیاز به اعتبار در حساب.',
  },
  {
    id: 'gemini',
    name: 'Google Gemini',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    model: 'gemini-2.0-flash',
    keyUrl: 'https://aistudio.google.com/app/apikey',
    note: 'کلید رایگان از AI Studio؛ فارسی‌اش خوب است.',
  },
  {
    id: 'ollama',
    name: 'Ollama روی کامپیوتر خودم',
    baseUrl: 'http://localhost:11434/v1',
    model: 'llama3.1',
    keyUrl: 'https://ollama.com/download',
    note: 'کاملاً محلی و بی‌هزینه؛ باید گوشی و کامپیوتر در یک شبکه باشند.',
  },
  {
    id: 'custom',
    name: 'سرویس دلخواه (سازگار با OpenAI)',
    baseUrl: '',
    model: '',
    keyUrl: '',
    note: 'هر آدرسی که endpoint سازگار با OpenAI دارد.',
  },
];

export function providerFor(baseUrl: string): AiProvider | undefined {
  const normalized = baseUrl.trim().replace(/\/+$/, '');
  return AI_PROVIDERS.find(
    (provider) => provider.baseUrl && provider.baseUrl.replace(/\/+$/, '') === normalized
  );
}
