export type MotivationCategory = 'drive' | 'courage' | 'hope' | 'balance';
export interface MotivationLine { id: string; category: MotivationCategory; text: string; action: string }

/** Original local prompts, not attributed quotations or evidence-based predictions. */
export const MOTIVATION_LINES: readonly MotivationLine[] = [
  { id: 'drive-1', category: 'drive', text: 'شروع همیشه حس خوبی ندارد. گاهی باید فقط فایل را باز کنی و ببینی قدم اول چیست.', action: 'کاری را باز کن و فقط قدم اولش را مشخص کن.' },
  { id: 'drive-2', category: 'drive', text: 'فهرست بلند، نقشه نیست. یک کار روشن را انتخاب کن و بقیه را فعلاً کنار بگذار.', action: 'یک کار مشخص را برای همین نوبت انتخاب کن.' },
  { id: 'drive-3', category: 'drive', text: 'اگر حواست مدام پرت می‌شود، شاید کار هنوز به اندازهٔ کافی کوچک نشده است.', action: 'کار را به بخشی تقسیم کن که در ده دقیقه جا شود.' },
  { id: 'drive-4', category: 'drive', text: 'لازم نیست از همان دقیقهٔ اول متمرکز باشی؛ به خودت چند دقیقه فرصت ورود بده.', action: 'یک نوبت کوتاه تمرکز را امتحان کن.' },
  { id: 'drive-5', category: 'drive', text: 'بعضی کارها با منتظر ماندن سبک‌تر نمی‌شوند؛ فقط مبهم‌تر می‌مانند.', action: 'قدم بعدی یک کار عقب‌افتاده را بنویس.' },
  { id: 'drive-6', category: 'drive', text: 'یک بار انجام دادن هنوز عادت نیست؛ اما جایی است که عادت از آن شروع می‌شود.', action: 'کاری کوچک را برای یک زمان مشخص برنامه‌ریزی کن.' },
  { id: 'courage-1', category: 'courage', text: 'می‌شود از نتیجه مطمئن نبود و با این حال، تصمیمی سنجیده گرفت.', action: 'آنچه می‌دانی و آنچه هنوز نمی‌دانی را بنویس.' },
  { id: 'courage-2', category: 'courage', text: 'شجاعت همیشه صدای بلندی ندارد؛ گاهی پرسیدن یک سؤال دشوار است.', action: 'یک سؤال را که به تعویق انداخته‌ای مشخص کن.' },
  { id: 'courage-3', category: 'courage', text: 'اشتباه در روش، دلیل اشتباه بودن هدف نیست. می‌شود روش را عوض کرد.', action: 'یک مانع را بنویس و راه دیگری برایش بررسی کن.' },
  { id: 'courage-4', category: 'courage', text: 'برگشتن به کاری که رهایش کرده‌ای ممکن است سخت باشد. لازم نیست از جایی شروع کنی که قبلاً مانده بودی.', action: 'یک شروع کوچک و تازه برای آن کار تعیین کن.' },
  { id: 'courage-5', category: 'courage', text: 'نه گفتن به یک درخواست، گاهی تنها راه وقت گذاشتن برای تعهدهای قبلی است.', action: 'پیش از پذیرفتن کار جدید، برنامه‌ات را نگاه کن.' },
  { id: 'hope-1', category: 'hope', text: 'همه‌چیز یک‌باره بهتر نمی‌شود؛ با این حال، بعضی چیزها را می‌شود اندکی بهتر کرد.', action: 'یک تغییر کوچک و ممکن برای امروز پیدا کن.' },
  { id: 'hope-2', category: 'hope', text: 'اینکه امروز راه را نمی‌بینی، به معنی نبودن راه نیست. ممکن است به زمان یا کمک نیاز داشته باشی.', action: 'یک نفر یا منبع قابل اعتماد برای کمک انتخاب کن.' },
  { id: 'hope-3', category: 'hope', text: 'پیشرفت همیشه شبیه جلو رفتن نیست؛ گاهی فهمیدن اینکه چه چیزی جواب نمی‌دهد هم مهم است.', action: 'چیزی را که از یک تجربه فهمیدی یادداشت کن.' },
  { id: 'hope-4', category: 'hope', text: 'یک روز سخت، دربارهٔ همهٔ روزهای بعد تصمیم نمی‌گیرد.', action: 'برای فردا فقط یک کار قابل انجام کنار بگذار.' },
  { id: 'hope-5', category: 'hope', text: 'می‌توانی از نقطه‌ای که هستی شروع کنی، نه از نقطه‌ای که فکر می‌کردی باید باشی.', action: 'وضعیت فعلی یک کار را بدون قضاوت ثبت کن.' },
  { id: 'balance-1', category: 'balance', text: 'خستگی نشانهٔ شکست نیست. پیش از تصمیم بعدی، شاید به مکث نیاز داشته باشی.', action: 'چند دقیقه از صفحه فاصله بگیر.' },
  { id: 'balance-2', category: 'balance', text: 'برنامه‌ای که هیچ جایی برای اتفاق‌های پیش‌بینی‌نشده ندارد، احتمالاً روی کاغذ بهتر از زندگی واقعی کار می‌کند.', action: 'میان دو کار امروز کمی فاصله بگذار.' },
  { id: 'balance-3', category: 'balance', text: 'تمام نشدن فهرست، ارزش تلاشی را که کرده‌ای از بین نمی‌برد.', action: 'آنچه واقعاً امروز انجام شد مرور کن.' },
  { id: 'balance-4', category: 'balance', text: 'اگر برنامه مدام عقب می‌افتد، شاید وقت اصلاح برنامه است، نه سرزنش خودت.', action: 'برای یکی از کارها زمان واقع‌بینانه‌تری انتخاب کن.' },
  { id: 'balance-5', category: 'balance', text: 'استراحت قرار نیست جایزهٔ تمام شدن همهٔ کارها باشد؛ بخشی از برنامه است.', action: 'زمان استراحت بعدی را مشخص کن.' },
];

export const MOTIVATION_CATEGORIES: { id: MotivationCategory; label: string }[] = [
  { id: 'drive', label: 'حرکت' }, { id: 'courage', label: 'قدرت' },
  { id: 'hope', label: 'امید' }, { id: 'balance', label: 'تعادل' },
];

export function dailyLineIndex(dateKey: string, count: number): number {
  if (count <= 0) return 0;
  let hash = 0;
  for (const char of dateKey) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return hash % count;
}
