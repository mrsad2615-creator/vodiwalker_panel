import { useState } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '@/App';
import { NumericInput } from '@/components/ui/NumericInput';
import { db } from '@/lib/db';

describe('KAIROS Full Application End-to-End Flow', () => {
  beforeEach(async () => {
    window.localStorage.clear();
    await db.transaction(
      'rw',
      [db.tasks, db.habits, db.habitLogs, db.focusSessions, db.goals, db.settings],
      async () => {
        await Promise.all([
          db.tasks.clear(),
          db.habits.clear(),
          db.habitLogs.clear(),
          db.focusSessions.clear(),
          db.goals.clear(),
          db.settings.clear(),
        ]);
      }
    );
  });

  it('runs optimized mrsad splash, pro vertical/horizontal habits, and cinematic focus sanctuary cleanly', async () => {
    render(<App />);

    // Optimized CSS compositor mrsad splash intro renders immediately
    const splash = screen.getByRole('status', { name: /mrsad/i });
    expect(splash).toBeInTheDocument();
    expect(screen.getByText('KAIROS STUDIO')).toBeInTheDocument();

    // Tap splash to skip directly into Dashboard
    fireEvent.click(splash);
    await waitFor(() => {
      expect(screen.getByText('پیشرفت امروز')).toBeInTheDocument();
    });

    expect(screen.getByText('کارهای مهم امروز')).toBeInTheDocument();
    expect(screen.getByText('عادت‌های امروز')).toBeInTheDocument();
    expect(screen.getByText('اهداف فعال')).toBeInTheDocument();

    // Navigate to Tasks -> Kanban -> Eisenhower -> create a real task & test typing/deleting
    fireEvent.click(screen.getByRole('button', { name: 'وظایف' }));
    await waitFor(() => {
      expect(screen.getByText('مدیریت وظایف')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('button', { name: 'کانبان' }));
    expect(screen.getByText('In Progress')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'آیزنهاور' }));
    expect(screen.getByText('مهم و فوری')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'لیست' }));
    fireEvent.click(screen.getByRole('button', { name: 'وظیفه جدید' }));

    const titleInput = (await screen.findByPlaceholderText(
      'چه کاری باید انجام شود؟'
    )) as HTMLInputElement;
    fireEvent.change(titleInput, { target: { value: 'متن اشتباه' } });
    expect(titleInput.value).toBe('متن اشتباه');
    fireEvent.change(titleInput, { target: { value: '' } });
    expect(titleInput.value).toBe('');
    fireEvent.change(titleInput, { target: { value: 'انتشار نسخه نهایی کایروس' } });
    fireEvent.click(screen.getByRole('button', { name: 'ثبت وظیفه' }));

    await waitFor(() => {
      expect(screen.getByText('انتشار نسخه نهایی کایروس')).toBeInTheDocument();
    });

    // Navigate to Pro Habits -> test Vertical/Horizontal Heatmap & Compact/Grid views
    fireEvent.click(screen.getByRole('button', { name: 'عادت‌ها' }));
    await waitFor(() => {
      expect(screen.getByText('مدیریت حرفه‌ای عادت‌ها')).toBeInTheDocument();
    });
    expect(screen.getByText('تقویم حرارتی سالانه')).toBeInTheDocument();

    // Switch heatmap between Horizontal & Vertical orientations
    fireEvent.click(screen.getByRole('button', { name: 'نمای افقی تقویم' }));
    expect(screen.getByText('نمای افقی سالانه (365 روز)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'نمای عمودی تقویم' }));
    expect(screen.getByText('نمای عمودی (خوانا و لمسی)')).toBeInTheDocument();

    // Switch pro habit card layouts
    fireEvent.click(screen.getByRole('button', { name: 'نمای فشرده' }));
    fireEvent.click(screen.getByRole('button', { name: 'نمای کارتی' }));
    fireEvent.click(screen.getByRole('button', { name: 'نمای عمودی حرفه‌ای' }));

    // Navigate to Calendar (Jalali Shamsi)
    fireEvent.click(screen.getByRole('button', { name: 'تقویم' }));
    await waitFor(() => {
      expect(screen.getByText('تقویم و برنامه‌ریزی')).toBeInTheDocument();
    });

    // Navigate to Focus Mode -> test Cinematic Timer & Full-Screen Cinema Mode
    fireEvent.click(screen.getByRole('button', { name: 'تمرکز' }));
    await waitFor(() => {
      expect(screen.getByText('محیط تمرکز')).toBeInTheDocument();
    });
    expect(screen.getByText('فضای صوتی تمرکز (آفلاین)')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'ورود به حالت سینمایی تمام‌صفحه' }));
    expect(screen.getByText('KAIROS CINEMA')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'خروج از نمای سینمایی' }));
    expect(screen.queryByText('KAIROS CINEMA')).not.toBeInTheDocument();

    // Navigate to Analytics (Monthly Review + Annual Review)
    fireEvent.click(screen.getByRole('button', { name: 'تحلیل' }));
    await waitFor(() => {
      expect(screen.getByText('تحلیل هوشمند داده‌های واقعی')).toBeInTheDocument();
    });
    fireEvent.click(screen.getByRole('button', { name: 'مرور سالانه' }));
    await waitFor(() => {
      expect(screen.getByText('گزارش سالانه شمسی')).toBeInTheDocument();
    });

    // Navigate to Settings
    fireEvent.click(screen.getByRole('button', { name: 'تنظیمات' }));
    await waitFor(() => {
      expect(screen.getByText('ذخیره‌سازی آفلاین و پشتیبان‌گیری')).toBeInTheDocument();
    });
  });

  it('NumericInput allows full backspace clearing and converts Persian digits cleanly', () => {
    let latest = 25;
    function Harness() {
      const [val, setVal] = useState(25);
      return (
        <NumericInput
          ariaLabel="focus-min"
          value={val}
          min={1}
          max={180}
          fallback={25}
          onChange={(v) => {
            latest = v;
            setVal(v);
          }}
        />
      );
    }
    render(<Harness />);
    const input = screen.getByLabelText('focus-min') as HTMLInputElement;
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: '' } });
    expect(input.value).toBe('');
    fireEvent.change(input, { target: { value: '۴۵' } });
    expect(input.value).toBe('45');
    expect(latest).toBe(45);
    fireEvent.blur(input);
    expect(input.value).toBe('45');
  });
});
