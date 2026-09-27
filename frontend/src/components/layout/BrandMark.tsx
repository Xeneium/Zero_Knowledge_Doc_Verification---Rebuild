import { cn } from '../../lib/utils/cn';

export function BrandMark({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 40 40"
      aria-hidden="true"
      className={cn('size-10', className)}
      fill="none"
    >
      <rect
        width="40"
        height="40"
        rx="11"
        fill="currentColor"
        className="text-accent-500"
      />
      <path
        d="M20 7.5 31 12v7.2c0 7-4.35 12.65-11 15.3-6.65-2.65-11-8.3-11-15.3V12l11-4.5Z"
        fill="currentColor"
        className="text-ink-950"
      />
      <path
        d="m14.5 20 3.7 3.7 7.3-8"
        stroke="white"
        strokeWidth="2.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
