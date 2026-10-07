import { useCallback, useEffect, useRef, useState, type DependencyList } from "react";

export interface AsyncState<T> {
  data: T | null;
  error: unknown;
  loading: boolean;
  /** Re-runs the loader; `silent` keeps the current data visible while reloading. */
  reload: (silent?: boolean) => void;
}

function sameDeps(a: DependencyList, b: DependencyList): boolean {
  return a.length === b.length && a.every((value, index) => Object.is(value, b[index]));
}

/**
 * Runs an async loader when `deps` change; ignores results of superseded runs. New inputs start
 * from an empty state so the previous result is never shown under the new label; reloads keep it.
 */
export function useAsync<T>(loader: () => Promise<T>, deps: DependencyList): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);
  const silentRef = useRef(false);
  const loaderRef = useRef(loader);
  const previousDeps = useRef<DependencyList | null>(null);
  loaderRef.current = loader;

  useEffect(() => {
    let cancelled = false;
    const changed = previousDeps.current !== null && !sameDeps(previousDeps.current, deps);
    previousDeps.current = deps;
    if (changed) {
      setData(null);
      setError(null);
    }
    if (changed || !silentRef.current) setLoading(true);
    silentRef.current = false;
    loaderRef
      .current()
      .then((result) => {
        if (cancelled) return;
        setData(result);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (cancelled) return;
        setError(cause);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [...deps, tick]);

  const reload = useCallback((silent = false) => {
    silentRef.current = silent;
    setTick((value) => value + 1);
  }, []);

  return { data, error, loading, reload };
}
