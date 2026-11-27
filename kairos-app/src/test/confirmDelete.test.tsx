import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDeleteButton } from '@/components/ui/ConfirmDeleteButton';

describe('destructive confirmation', () => {
  it('does not delete on the first tap or cancellation; executes only after confirmation', async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    render(<ConfirmDeleteButton label="حذف مورد" description="این مورد و تاریخچه‌اش حذف می‌شوند." onConfirm={remove}>حذف</ConfirmDeleteButton>);
    fireEvent.click(screen.getByRole('button', { name: 'حذف مورد' }));
    const dialog = screen.getByRole('dialog', { name: 'تأیید حذف' });
    expect(remove).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: 'انصراف' }));
    expect(remove).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'حذف مورد' }));
    fireEvent.click(within(screen.getByRole('dialog', { name: 'تأیید حذف' })).getByRole('button', { name: 'تأیید حذف' }));
    await waitFor(() => expect(remove).toHaveBeenCalledTimes(1));
  });

  it('shows a failure callback rather than claiming a failed delete succeeded', async () => {
    const failed = vi.fn();
    render(<ConfirmDeleteButton label="حذف مورد" description="حذف؟" onConfirm={vi.fn().mockRejectedValue(new Error('storage'))} onError={failed}>حذف</ConfirmDeleteButton>);
    fireEvent.click(screen.getByRole('button', { name: 'حذف مورد' }));
    fireEvent.click(within(screen.getByRole('dialog', { name: 'تأیید حذف' })).getByRole('button', { name: 'تأیید حذف' }));
    await waitFor(() => expect(failed).toHaveBeenCalledTimes(1));
    expect(screen.getByRole('dialog', { name: 'تأیید حذف' })).toBeInTheDocument();
  });
});
