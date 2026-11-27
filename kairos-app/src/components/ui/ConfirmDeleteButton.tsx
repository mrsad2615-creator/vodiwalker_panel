import { useEffect, useRef, useState, type ReactNode } from 'react';

/** A destructive action never runs on the first tap. */
export function ConfirmDeleteButton({ label, description, onConfirm, onError, className, children }: {
  label: string;
  description: string;
  onConfirm: () => Promise<void>;
  onError?: () => void;
  className?: string;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const opener = useRef<HTMLButtonElement>(null);
  const cancel = useRef<HTMLButtonElement>(null);
  useEffect(() => { if (open) cancel.current?.focus(); }, [open]);
  const close = () => { setOpen(false); opener.current?.focus(); };
  return <>
    <button ref={opener} type="button" aria-label={label} onClick={() => setOpen(true)} className={className}>{children}</button>
    {open && <div role="dialog" aria-modal="true" aria-label="تأیید حذف" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/75 p-4" onKeyDown={(event) => {
      if (event.key === 'Escape' && !busy) { event.stopPropagation(); close(); }
    }}>
      <div className="w-full max-w-sm space-y-4 rounded-2xl border border-[var(--border)] bg-[var(--surface-1)] p-5 text-sm leading-7 text-[var(--text)]">
        <p>{description}</p>
        <div className="flex gap-2">
          <button type="button" disabled={busy} onClick={async () => {
            setBusy(true);
            try { await onConfirm(); close(); }
            catch { onError?.(); }
            finally { setBusy(false); }
          }} className="min-h-11 rounded-xl bg-[var(--danger)] px-4 font-bold text-[var(--bg)] disabled:opacity-50">تأیید حذف</button>
          <button ref={cancel} type="button" disabled={busy} onClick={close} className="btn-ghost-luxury min-h-11 rounded-xl px-4">انصراف</button>
        </div>
      </div>
    </div>}
  </>;
}
