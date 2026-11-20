import { useEffect, type ReactNode } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X } from 'lucide-react';

interface ModalSheetProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}

export function ModalSheet({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
}: ModalSheetProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center sm:items-center"
          role="dialog"
          aria-modal="true"
          aria-label={title}
        >
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            onClick={onClose}
            className="fixed inset-0 bg-[#050508]/78 backdrop-blur-[6px]"
          />

          <motion.div
            initial={{ y: 42, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            exit={{ y: 36, opacity: 0 }}
            transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}
            className="luxury-glass-sheet relative z-10 flex max-h-[90dvh] w-full max-w-[480px] flex-col rounded-t-[26px] sm:rounded-[26px] sm:border sm:border-[#A99BF0]/18"
          >
            {/* Mobile grab bar */}
            <div className="flex justify-center pt-2.5 pb-1 sm:hidden">
              <div className="h-1 w-10 rounded-full bg-[#A99BF0]/22" />
            </div>

            {/* Header */}
            <div className="flex items-start justify-between gap-3 border-b border-[#A99BF0]/10 px-5 py-3.5">
              <div>
                <h2 className="text-[1.02rem] font-bold text-[#ECECEF]">{title}</h2>
                {subtitle && (
                  <p className="mt-0.5 text-xs text-[#9C9CA6]">{subtitle}</p>
                )}
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="بستن"
                className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#1C1C21]/60 text-[#9C9CA6] transition hover:bg-[#1C1C21] hover:text-[#ECECEF]"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            {/* Scrollable Body */}
            <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>

            {/* Optional Footer */}
            {footer && (
              <div className="border-t border-[#A99BF0]/10 px-5 py-3.5">{footer}</div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
