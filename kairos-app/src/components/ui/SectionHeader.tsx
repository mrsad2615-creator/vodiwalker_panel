import type { ReactNode } from 'react';

interface SectionHeaderProps {
  /** Uppercase Latin voice of the room (TODAY / PLANNER / RITUALS …). */
  eyebrow?: string;
  title: ReactNode;
  /** One line of real numbers or context under the title. */
  meta?: ReactNode;
  /** Trailing control(s) — a button, a toggle, an icon. */
  action?: ReactNode;
  /** `page` is the screen title, `section` sits above a rail. */
  size?: 'page' | 'section';
  className?: string;
  id?: string;
}

/**
 * SectionHeader — the one way a screen or a rail announces itself.
 *
 * Flag­ship apps repeat exactly one header rhythm: eyebrow, title, optional real
 * metadata, one trailing action. Pages that invent their own header per screen
 * are what makes a product feel stitched together.
 */
export function SectionHeader({
  eyebrow,
  title,
  meta,
  action,
  size = 'page',
  className = '',
  id,
}: SectionHeaderProps) {
  const isPage = size === 'page';

  return (
    <header className={`flex items-start justify-between gap-3 ${className}`}>
      <div className="min-w-0">
        {eyebrow && <span className="eyebrow block leading-none">{eyebrow}</span>}
        <h1
          id={id}
          className={
            isPage
              ? `mt-1.5 text-[19px] font-bold leading-tight tracking-tight text-[var(--text)]`
              : `mt-1 text-[13px] font-bold leading-tight text-[var(--text)]`
          }
        >
          {title}
        </h1>
        {meta && (
          <p className="mt-1 flex flex-wrap items-center gap-1 text-[10.5px] leading-4 text-[var(--text-3)]">
            {meta}
          </p>
        )}
      </div>
      {action && <div className="flex shrink-0 items-center gap-1.5">{action}</div>}
    </header>
  );
}
