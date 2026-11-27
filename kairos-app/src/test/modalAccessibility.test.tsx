import { useState } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ModalSheet } from '@/components/ui/ModalSheet';

function Example() {
  const [open, setOpen] = useState(false);
  const [nested, setNested] = useState(false);
  return <>
    <button type="button" onClick={() => setOpen(true)}>بازکردن ویرایشگر</button>
    <ModalSheet open={open} onClose={() => setOpen(false)} title="ویرایشگر">
      <input aria-label="عنوان" />
      <button type="button" onClick={() => setNested(true)}>تأیید بیشتر</button>
      <ModalSheet open={nested} onClose={() => setNested(false)} title="تأیید">
        <button type="button" onClick={() => setNested(false)}>انصراف</button>
      </ModalSheet>
    </ModalSheet>
  </>;
}

describe('accessible modal sheets', () => {
  it('focuses close, contains keyboard navigation and restores focus', () => {
    render(<Example />);
    const opener = screen.getByRole('button', { name: 'بازکردن ویرایشگر' });
    opener.focus();
    fireEvent.click(opener);
    const dialog = screen.getByRole('dialog', { name: 'ویرایشگر' });
    const close = within(dialog).getByRole('button', { name: 'بستن' });
    const last = within(dialog).getByRole('button', { name: 'تأیید بیشتر' });
    expect(close).toHaveFocus();
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
    expect(last).toHaveFocus();
    fireEvent.keyDown(window, { key: 'Tab' });
    expect(close).toHaveFocus();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(opener).toHaveFocus();
  });

  it('Escape closes only the top sheet and restores focus to its opener', () => {
    render(<Example />);
    fireEvent.click(screen.getByRole('button', { name: 'بازکردن ویرایشگر' }));
    const opener = screen.getByRole('button', { name: 'تأیید بیشتر' });
    opener.focus();
    fireEvent.click(opener);
    expect(within(screen.getByRole('dialog', { name: 'تأیید' })).getByRole('button', { name: 'بستن' })).toHaveFocus();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(opener).toHaveFocus();
    expect(screen.getByRole('dialog', { name: 'ویرایشگر' })).toBeInTheDocument();
  });
});
