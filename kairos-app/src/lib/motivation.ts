export type MotivationCategory = 'drive' | 'courage' | 'hope' | 'balance';
export interface MotivationLine { id: string; category: MotivationCategory; text: string; action: string }

/** Original local prompts, not attributed quotations or evidence-based predictions. */
export const MOTIVATION_LINES: readonly MotivationLine[] = [
  { id: 'drive-1', category: 'drive', text: 'منتظر لحظهٔ کامل نمان؛ یک حرکت کوچک، سکوت را می‌شکند.', action: 'یک کار دو دقیقه‌ای انتخاب کن.' },
  { id: 'drive-2', category: 'drive', text: 'قدرت از تصمیمی شروع می‌شود که همین حالا به عمل تبدیلش می‌کنی.', action: 'اولین قدم را در فهرستت بنویس.' },
  { id: 'drive-3', category: 'drive', text: 'انرژی‌ات را صرف قدم بعدی کن، نه ترس از تمام مسیر.', action: 'مهم‌ترین کار بازت را انتخاب کن.' },
  { id: 'drive-4', category: 'drive', text: 'حرکت کن؛ سرعتت را می‌توانی در طول راه تنظیم کنی.', action: 'یک بازهٔ کوتاه تمرکز شروع کن.' },
  { id: 'drive-5', category: 'drive', text: 'امروز را با یک اقدام واقعی امضا کن.', action: 'یک وظیفهٔ روشن و قابل انجام بساز.' },
  { id: 'drive-6', category: 'drive', text: 'عادت‌های کوچک، موتور روزهای بزرگ‌اند.', action: 'یک عادت کوچک را ثبت کن.' },
  { id: 'courage-1', category: 'courage', text: 'محکم بایست؛ لازم نیست همه‌چیز را همین امروز حل کنی.', action: 'مسئله را به یک قدم کوچک تقسیم کن.' },
  { id: 'courage-2', category: 'courage', text: 'شجاعت یعنی با وجود تردید، قدم حساب‌شده برداری.', action: 'کاری را انتخاب کن که از آن دوری کرده‌ای.' },
  { id: 'courage-3', category: 'courage', text: 'اگر مسیر سخت شد، قدرتت را با تغییر روش نشان بده.', action: 'برای مانع امروز یک راه جایگزین بنویس.' },
  { id: 'courage-4', category: 'courage', text: 'تو می‌توانی دوباره شروع کنی؛ بدون اینکه گذشته را انکار کنی.', action: 'یک کار عقب‌افتاده را برای زمان واقع‌بینانه بچین.' },
  { id: 'courage-5', category: 'courage', text: 'روی تصمیم خودت بایست؛ پیشرفت به تأیید همه نیاز ندارد.', action: 'یک اولویت را برای امروز مشخص کن.' },
  { id: 'hope-1', category: 'hope', text: 'فردا از همین قدم‌های کوچک امروز ساخته می‌شود.', action: 'یک قدم مهربانانه برای آینده‌ات بردار.' },
  { id: 'hope-2', category: 'hope', text: 'حتی روزهای آرام هم می‌توانند آغاز یک تغییر باشند.', action: 'کاری ساده را بدون عجله شروع کن.' },
  { id: 'hope-3', category: 'hope', text: 'امید فقط انتظار نیست؛ انتخابی است که با عمل همراه می‌شود.', action: 'یک امکان تازه را یادداشت کن.' },
  { id: 'hope-4', category: 'hope', text: 'راه تازه می‌تواند از جایی شروع شود که قبلاً توقف کرده‌ای.', action: 'به یکی از هدف‌هایت دوباره نگاه کن.' },
  { id: 'hope-5', category: 'hope', text: 'هر روز فرصتی است برای ساختن نسخه‌ای آگاه‌تر از دیروز.', action: 'یک چیز آموخته‌شده را ثبت کن.' },
  { id: 'balance-1', category: 'balance', text: 'استراحت عقب‌نشینی نیست؛ بخشی از ادامه‌دادن است.', action: 'برای یک استراحت کوتاه زمان بگذار.' },
  { id: 'balance-2', category: 'balance', text: 'قدرت پایدار از نظم و مراقبت در کنار هم می‌آید.', action: 'ظرفیت واقعی امروزت را بررسی کن.' },
  { id: 'balance-3', category: 'balance', text: 'ارزش تو به تعداد کارهای تمام‌شده وابسته نیست.', action: 'یک اولویت کافی برای امروز انتخاب کن.' },
  { id: 'balance-4', category: 'balance', text: 'با خودت صادق باش؛ برنامهٔ خوب با واقعیت زندگی هماهنگ است.', action: 'اگر لازم است، زمان یکی از کارها را بازبینی کن.' },
  { id: 'balance-5', category: 'balance', text: 'آرامش و بلندپروازی می‌توانند در یک مسیر همراه باشند.', action: 'پیش از شروع، یک نفس آرام بکش.' },
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
