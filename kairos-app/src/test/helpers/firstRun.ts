import { expect } from "vitest";
import { fireEvent, screen, waitFor } from '@testing-library/react';

/** Existing UI tests choose a clean workspace unless they specifically test setup. */
export async function completeFirstRun(visible = false): Promise<void> {
  await waitFor(() => {
    if (!screen.queryByRole('heading', { name: 'با داده‌های نمونه شروع کنیم؟' }) && !screen.queryByTestId('home-ready')) throw new Error('waiting for app');
  }, { timeout: 8000 });
  const heading = screen.queryByRole('heading', { name: 'با داده‌های نمونه شروع کنیم؟' });
  if (!heading) return;
  const checkbox = screen.getByRole('checkbox', { name: /نمایش دادهٔ نمونه/ });
  if ((checkbox as HTMLInputElement).checked !== visible) fireEvent.click(checkbox);
  fireEvent.click(screen.getByRole('button', { name: 'تأیید و ورود' }));
  await waitFor(() => expect(screen.queryByRole('heading', { name: 'با داده‌های نمونه شروع کنیم؟' })).not.toBeInTheDocument(), { timeout: 8000 });
}
