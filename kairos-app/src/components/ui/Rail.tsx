import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react';

/**
 * Rail — the only content container in KAIROS.
 *
 * A rail is a grouped surface with hairline-separated rows. It replaces the mix
 * of floating cards, hero cards and one-off borders that made the old screens
 * feel improvised: one radius, one border colour, one row rhythm, everywhere.
 */
export function Rail({
  className = '',
  children,
  ...rest
}: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return (
    <div className={`rail ${className}`} {...rest}>
      {children}
    </div>
  );
}

interface RailHeadProps {
  /** Optional uppercase Latin eyebrow. */
  eyebrow?: string;
  title: ReactNode;
  meta?: ReactNode;
  action?: ReactNode;
  className?: string;
}

/** The header strip inside a rail: title, optional meta, one action. */
export function RailHead({ eyebrow, title, meta, action, className = '' }: RailHeadProps) {
  return (
    <div className={`rail-head ${className}`}>
      <div className="min-w-0">
        {eyebrow && <span className="eyebrow block leading-none">{eyebrow}</span>}
        <div className="mt-1 flex items-baseline gap-2">
          <span className="truncate text-[12.5px] font-semibold text-[var(--text)]">{title}</span>
          {meta && <span className="shrink-0 text-[10px] text-[var(--text-3)]">{meta}</span>}
        </div>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

type RailRowAs = 'div' | 'button' | 'li';

interface RailRowProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'className'> {
  as?: RailRowAs;
  className?: string;
  children: ReactNode;
}

/**
 * One row inside a rail. Interactive rows are real buttons, so the whole row is
 * a 52px-plus touch target (Material 3 / HIG minimum is 44–48px).
 */
export function RailRow({ as = 'div', className = '', children, ...rest }: RailRowProps) {
  if (as === 'button') {
    return (
      <button type="button" className={`rail-row ${className}`} {...rest}>
        {children}
      </button>
    );
  }

  const Tag = as;
  return (
    <Tag className={`rail-row ${className}`} {...(rest as HTMLAttributes<HTMLElement>)}>
      {children}
    </Tag>
  );
}
