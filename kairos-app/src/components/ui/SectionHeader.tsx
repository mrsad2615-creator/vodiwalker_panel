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
  const Heading = isPage ? 'h1' : 'h2';

  return (
    <header className={`section-header ${isPage ? 'section-header--page' : 'section-header--section'} ${className}`}>
      <div className="section-header__copy min-w-0">
        {eyebrow && <span className="eyebrow block leading-none">{eyebrow}</span>}
        <Heading
          id={id}
          className={
            isPage
              ? `section-header__title mt-1.5 font-bold text-[var(--text)]`
              : `mt-1 text-sm font-bold leading-tight text-[var(--text)]`
          }
        >
          {title}
        </Heading>
        {meta && (
          <p className="section-header__meta mt-1.5 flex flex-wrap items-center gap-1 text-[var(--text-2)]">
            {meta}
          </p>
        )}
      </div>
      {action && <div className="section-header__action flex items-center gap-1.5">{action}</div>}
    </header>
  );
}
