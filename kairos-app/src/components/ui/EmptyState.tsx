import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}

export function EmptyState({ icon, title, description, action }: EmptyStateProps) {
  return (
    <div className="luxury-card-subtle flex flex-col items-center justify-center px-6 py-10 text-center">
      <div className="mb-3.5 flex h-12 w-12 items-center justify-center rounded-2xl border border-[#B9A8FF]/18 bg-[#1C1C21]/75 text-[#B9A8FF]">
        {icon}
      </div>
      <h3 className="text-sm font-semibold text-[#ECECEF]">{title}</h3>
      <p className="mt-1.5 max-w-[270px] text-xs leading-6 text-[#9C9CA6]">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
