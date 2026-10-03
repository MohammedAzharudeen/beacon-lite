import { useCallback, useEffect, useRef, useState } from "react";
import { BeaconApiError } from "../api/beaconApi";

export interface AsyncState<T> {
  data: T | null;
  loading: boolean;
  error: BeaconApiError | null;
  reload: () => void;
}

/** Runs a request when its inputs change; ignores responses that arrive after newer requests. */
export function useAsync<T>(
  load: (() => Promise<T>) | null,
  deps: unknown[],
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState<boolean>(load !== null);
  const [error, setError] = useState<BeaconApiError | null>(null);
  const [tick, setTick] = useState(0);
  const latest = useRef(0);

  useEffect(() => {
    if (!load) {
      setData(null);
      setLoading(false);
      return;
    }
    const id = ++latest.current;
    setLoading(true);
    setError(null);
    load()
      .then((value) => {
        if (id === latest.current) setData(value);
      })
      .catch((e: unknown) => {
        if (id !== latest.current) return;
        setError(
          e instanceof BeaconApiError
            ? e
            : new BeaconApiError({
                status: 0,
                code: "UNKNOWN",
                message: "Something went wrong",
              }),
        );
      })
      .finally(() => {
        if (id === latest.current) setLoading(false);
      });
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, loading, error, reload };
}
