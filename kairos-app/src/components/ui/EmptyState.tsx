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
      <div className="mb-3.5 flex h-12 w-12 items-center justify-center rounded-2xl border border-[#B69AEF]/18 bg-[#241936]/75 text-[#B69AEF]">
        {icon}
      </div>
      <h3 className="text-sm font-semibold text-[#F4F1F8]">{title}</h3>
      <p className="mt-1.5 max-w-[270px] text-xs leading-6 text-[#A79EB9]">{description}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
