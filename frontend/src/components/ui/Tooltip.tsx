import { cloneElement, useId, type ReactElement } from 'react';

export interface TooltipProps {
  content: string;
  children: ReactElement<{ 'aria-describedby'?: string }>;
}

export function Tooltip({ content, children }: TooltipProps) {
  const id = useId();
  return (
    <span className="group relative inline-flex">
      {cloneElement(children, { 'aria-describedby': id })}
      <span
        id={id}
        role="tooltip"
        className="bg-ink-950 pointer-events-none absolute top-[calc(100%+0.5rem)] left-1/2 z-50 hidden w-max max-w-56 -translate-x-1/2 rounded-md px-2.5 py-1.5 text-center text-xs text-white shadow-md group-focus-within:block group-hover:block"
      >
        {content}
      </span>
    </span>
  );
}
