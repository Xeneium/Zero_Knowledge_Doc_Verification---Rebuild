import React from 'react';
import { cn } from '../../lib/utils/cn';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'text' | 'rectangular' | 'circular';
}

export const Skeleton: React.FC<SkeletonProps> = ({
  className,
  variant = 'rectangular',
  ...props
}) => {
  const variantStyles = {
    text: 'h-4 w-full rounded',
    rectangular: 'rounded-md',
    circular: 'rounded-full',
  };

  return (
    <div
      role="presentation"
      aria-busy="true"
      aria-hidden="true"
      className={cn(
        'animate-pulse bg-slate-200 transition-colors dark:bg-slate-800',
        variantStyles[variant],
        className,
      )}
      {...props}
    />
  );
};
