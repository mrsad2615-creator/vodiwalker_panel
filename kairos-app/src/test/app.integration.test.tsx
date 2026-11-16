import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from '@/App';
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

  it('boots into Dashboard, navigates across all 7 pages, and creates a real persisted task', async () => {
    render(<App />);

    // Wait for Dashboard to load after starter DB init
    await waitFor(() => {
      expect(screen.getByText('پیشرفت امروز')).toBeInTheDocument();
    });
    expect(screen.getByText('کارهای مهم امروز')).toBeInTheDocument();
    expect(screen.getByText('عادت‌های امروز')).toBeInTheDocument();
    expect(screen.getByText('اهداف فعال')).toBeInTheDocument();

    // Navigate to Tasks -> Kanban -> Eisenhower -> create a real task
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

    const titleInput = await screen.findByPlaceholderText('چه کاری باید انجام شود؟');
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
});
