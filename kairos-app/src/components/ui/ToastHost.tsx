import { AnimatePresence, motion } from 'framer-motion';
import { Bell, CheckCircle2, Info, AlertTriangle, X } from 'lucide-react';
import type { ToastMessage } from '@/types/domain';

interface ToastHostProps {
  toasts: ToastMessage[];
  onDismiss: (id: string) => void;
}

export function ToastHost({ toasts, onDismiss }: ToastHostProps) {
  return (
    <div className="pointer-events-none fixed inset-x-0 top-3 z-[70] mx-auto flex w-full max-w-[440px] flex-col gap-2 px-4">
      <AnimatePresence>
        {toasts.map((toast) => {
          const Icon =
            toast.tone === 'success'
              ? CheckCircle2
              : toast.tone === 'warning'
              ? AlertTriangle
              : toast.tone === 'reminder'
              ? Bell
              : Info;
          const accent =
            toast.tone === 'warning'
              ? 'text-[#EB9BAB]'
              : toast.tone === 'reminder'
              ? 'text-[#B69AEF]'
              : 'text-[#B69AEF]';

          return (
            <motion.div
              key={toast.id}
              initial={{ opacity: 0, y: -16, scale: 0.96 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -12, scale: 0.96 }}
              transition={{ duration: 0.2 }}
              className="luxury-glass-dock pointer-events-auto flex items-start gap-3 rounded-2xl px-4 py-3"
            >
              <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${accent}`} />
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold text-[#F4F1F8]">{toast.title}</p>
                {toast.description && (
                  <p className="mt-0.5 text-[11px] leading-5 text-[#A79EB9]">
                    {toast.description}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => onDismiss(toast.id)}
                className="text-[#6E6680] hover:text-[#F4F1F8]"
                aria-label="بستن پیام"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
}
