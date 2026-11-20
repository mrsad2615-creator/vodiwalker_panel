import type { HTMLAttributes, ReactNode } from 'react';

interface LuxuryCardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'hero' | 'subtle';
  children: ReactNode;
}

export function LuxuryCard({
  variant = 'default',
  className = '',
  children,
  ...rest
}: LuxuryCardProps) {
  const variantClass =
    variant === 'hero'
      ? 'luxury-card-hero'
      : variant === 'subtle'
      ? 'luxury-card-subtle'
      : 'luxury-card';

  return (
    <div className={`${variantClass} ${className}`} {...rest}>
      {children}
    </div>
  );
}
