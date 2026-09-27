import {
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { cn } from '../../lib/utils/cn';

export interface TabItem {
  id: string;
  label: string;
  content: ReactNode;
}

export function Tabs({
  items,
  defaultTab,
}: {
  items: TabItem[];
  defaultTab?: string;
}) {
  const baseId = useId();
  const [active, setActive] = useState(defaultTab || items[0]?.id);
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const selected = items.find((item) => item.id === active);

  function moveFocus(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let nextIndex: number | undefined;
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % items.length;
    if (event.key === 'ArrowLeft')
      nextIndex = (index - 1 + items.length) % items.length;
    if (event.key === 'Home') nextIndex = 0;
    if (event.key === 'End') nextIndex = items.length - 1;
    if (nextIndex === undefined) return;

    event.preventDefault();
    const next = items[nextIndex];
    if (next) setActive(next.id);
    tabRefs.current[nextIndex]?.focus();
  }

  return (
    <div>
      <div role="tablist" className="border-ink-200 flex gap-1 border-b">
        {items.map((item, index) => (
          <button
            key={item.id}
            ref={(element) => {
              tabRefs.current[index] = element;
            }}
            id={`${baseId}-${item.id}-tab`}
            role="tab"
            type="button"
            aria-selected={item.id === active}
            aria-controls={`${baseId}-${item.id}-panel`}
            tabIndex={item.id === active ? 0 : -1}
            onClick={() => setActive(item.id)}
            onKeyDown={(event) => moveFocus(event, index)}
            className={cn(
              'min-h-11 border-b-2 px-4 text-sm font-semibold',
              item.id === active
                ? 'border-accent-600 text-accent-700'
                : 'text-ink-600 hover:text-ink-900 border-transparent',
            )}
          >
            {item.label}
          </button>
        ))}
      </div>
      {selected ? (
        <div
          id={`${baseId}-${selected.id}-panel`}
          role="tabpanel"
          aria-labelledby={`${baseId}-${selected.id}-tab`}
          className="pt-4"
        >
          {selected.content}
        </div>
      ) : null}
    </div>
  );
}
