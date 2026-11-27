import { useEffect, useRef, type ReactNode } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { X } from '@/components/ui/icons';

interface ModalSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}

// Only the topmost sheet owns Escape and keyboard focus, including when a
// confirmation sheet is opened from an editor. Entries are removed on cleanup.
const openSheets: symbol[] = [];
const FOCUSABLE = 'button:not([disabled]), input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';

export function ModalSheet({ open, onClose, title, subtitle, children, footer }: ModalSheetProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  const sheetId = useRef(Symbol('sheet'));
  const reduceMotion = useReducedMotion();
  closeRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const id = sheetId.current;
    const dialog = dialogRef.current;
    if (!dialog) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    openSheets.push(id);
    // Focus the close button, not a text field: opening an editor should not
    // unexpectedly summon the phone keyboard.
    (dialog.querySelector<HTMLElement>('[data-sheet-close]') ?? dialog).focus();

    const onKey = (event: KeyboardEvent) => {
      if (openSheets[openSheets.length - 1] !== id) return;
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        closeRef.current();
      } else if (event.key === 'Tab') {
        const controls = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE))
          .filter((element) => !element.hidden && !element.closest('[hidden], [inert], [aria-hidden="true"]'));
        if (controls.length === 0) {
          event.preventDefault();
          dialog.focus();
          return;
        }
        const index = controls.indexOf(document.activeElement as HTMLElement);
        if (event.shiftKey && index <= 0) {
          event.preventDefault();
          controls[controls.length - 1].focus();
        } else if (!event.shiftKey && (index === -1 || index === controls.length - 1)) {
          event.preventDefault();
          controls[0].focus();
        }
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      const wasTop = openSheets[openSheets.length - 1] === id;
      const index = openSheets.lastIndexOf(id);
      if (index >= 0) openSheets.splice(index, 1);
      if (wasTop && previous?.isConnected) previous.focus();
    };
  }, [open]);

  return (
    <AnimatePresence>
      {open && (
        <div
          ref={dialogRef}
          tabIndex={-1}
          className="fixed inset-0 z-50 flex items-end justify-center sm:items-center"
          role="dialog"
          aria-modal="true"
          aria-label={title}
        >
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.18 }}
            onClick={onClose}
            className="fixed inset-0 bg-[color-mix(in_srgb,var(--bg)_84%,transparent)] backdrop-blur-[6px]"
          />

          <motion.div
            initial={reduceMotion ? false : { y: 42, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={reduceMotion ? { opacity: 0 } : { y: 36, opacity: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="luxury-glass-sheet app-safe-bottom-sheet relative z-10 flex max-h-[88dvh] w-full max-w-[480px] flex-col rounded-t-[26px] sm:rounded-[26px] sm:border sm:border-[var(--accent-fg)]/18"
          >
            <div className="flex justify-center pt-2.5 pb-1 sm:hidden" aria-hidden="true">
              <div className="h-1 w-10 rounded-full bg-[var(--accent-fg)]/22" />
            </div>
            <div className="flex items-start justify-between gap-3 border-b border-[var(--accent-fg)]/10 px-5 py-3.5">
              <div>
                <h2 className="text-[1.02rem] font-bold text-[var(--text)]">{title}</h2>
                {subtitle && <p className="mt-0.5 text-xs text-[var(--text-2)]">{subtitle}</p>}
              </div>
              <button
                type="button"
                data-sheet-close
                onClick={onClose}
                aria-label="بستن"
                className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-[var(--surface-3)]/60 text-[var(--text-2)] transition hover:bg-[var(--surface-3)] hover:text-[var(--text)] focus-visible:outline-2 focus-visible:outline-[var(--accent-fg)]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
            {footer && <div className="border-t border-[var(--accent-fg)]/10 px-5 py-3.5">{footer}</div>}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
