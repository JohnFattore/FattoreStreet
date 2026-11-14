import { useEffect, useState } from "react";

// Returns `value` once it has stopped changing for `delayMs`, so a query keyed
// on it fires once per pause in typing rather than once per keystroke
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
