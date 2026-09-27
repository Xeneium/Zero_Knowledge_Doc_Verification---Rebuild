import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';
import type { HTMLAttributes } from 'react';
import { cn } from '../../lib/utils/cn';

type AlertTone = 'info' | 'success' | 'warning' | 'danger';

export interface AlertProps extends HTMLAttributes<HTMLDivElement> {
  tone?: AlertTone;
  title: string;
}

const styles: Record<AlertTone, string> = {
  info: 'border-info-100 bg-info-50 text-info-700',
  success: 'border-success-100 bg-success-50 text-success-700',
  warning: 'border-warning-100 bg-warning-50 text-warning-700',
  danger: 'border-danger-100 bg-danger-50 text-danger-700',
};

const icons = {
  info: Info,
  success: CheckCircle2,
  warning: TriangleAlert,
  danger: AlertCircle,
};

export function Alert({
  tone = 'info',
  title,
  children,
  className,
  ...props
}: AlertProps) {
  const Icon = icons[tone];
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn(
        'flex gap-3 rounded-lg border p-4',
        styles[tone],
        className,
      )}
      {...props}
    >
      <Icon className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
      <div>
        <p className="font-semibold">{title}</p>
        {children ? (
          <div className="mt-1 text-sm opacity-90">{children}</div>
        ) : null}
      </div>
    </div>
  );
}
