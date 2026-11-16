import { useState } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '@/App';
import { NumericInput } from '@/components/ui/NumericInput';
import { db } from '@/lib/db';

describe('KAIROS Full Application End-to-End Flow', () => {
  beforeEach(async () => {
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

  it('shows the mrsad motion-graphic splash intro, transitions cleanly, and supports full typing/backspacing', async () => {
    render(<App />);

    // Motion-graphic mrsad splash intro renders immediately
    const splash = screen.getByRole('status', { name: /mrsad/i });
    expect(splash).toBeInTheDocument();
    expect(screen.getByText('KAIROS STUDIO')).toBeInTheDocument();

    // Wait for DB ready, then tap splash to skip straight into Dashboard
    await waitFor(() => {
      expect(screen.getByText('پیشرفت امروز')).toBeInTheDocument();
    });
    fireEvent.click(splash);

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
    // Verify clearing / backspacing works cleanly
    fireEvent.change(titleInput, { target: { value: '' } });
    expect(titleInput.value).toBe('');
    fireEvent.change(titleInput, { target: { value: 'انتشار نسخه نهایی کایروس' } });
    fireEvent.click(screen.getByRole('button', { name: 'ثبت وظیفه' }));

    await waitFor(() => {
      expect(screen.getByText('انتشار نسخه نهایی کایروس')).toBeInTheDocument();
    });

    // Navigate to Habits & 365-Day Heatmap
    fireEvent.click(screen.getByRole('button', { name: 'عادت‌ها' }));
    await waitFor(() => {
      expect(screen.getByText('تقویم حرارتی سالانه')).toBeInTheDocument();
    });

    // Navigate to Calendar (Jalali Shamsi)
    fireEvent.click(screen.getByRole('button', { name: 'تقویم' }));
    await waitFor(() => {
      expect(screen.getByText('تقویم و برنامه‌ریزی')).toBeInTheDocument();
    });

    // Navigate to Focus Mode
    fireEvent.click(screen.getByRole('button', { name: 'تمرکز' }));
    await waitFor(() => {
      expect(screen.getByText('محیط تمرکز')).toBeInTheDocument();
    });

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
    // Clearing entire value with Backspace must stay empty while editing (not snap back!)
    fireEvent.change(input, { target: { value: '' } });
    expect(input.value).toBe('');
    // Typing Persian digits ۴۵ converts cleanly to 45
    fireEvent.change(input, { target: { value: '۴۵' } });
    expect(input.value).toBe('45');
    expect(latest).toBe(45);
    fireEvent.blur(input);
    expect(input.value).toBe('45');
  });
});
