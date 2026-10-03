import { useEffect, useRef, useState } from "react";
import { beaconApi } from "../api/beaconApi";
import type { JobResponse } from "../types/beacon";
import { useAsync } from "./useAsync";

export const useStores = () => useAsync(() => beaconApi.listStores(), []);

export const useReport = (storeId: number | null) =>
  useAsync(storeId === null ? null : () => beaconApi.report(storeId), [
    storeId,
  ]);

export const useRestock = (
  storeId: number | null,
  p: { category?: string; q?: string; limit?: number; offset?: number },
) =>
  useAsync(storeId === null ? null : () => beaconApi.restock(storeId, p), [
    storeId,
    p.category,
    p.q,
    p.limit,
    p.offset,
  ]);

export const useSizeGaps = (
  storeId: number | null,
  p: { category?: string; q?: string; limit?: number; offset?: number },
) =>
  useAsync(storeId === null ? null : () => beaconApi.sizeGaps(storeId, p), [
    storeId,
    p.category,
    p.q,
    p.limit,
    p.offset,
  ]);

export const useChanges = (storeId: number | null) =>
  useAsync(storeId === null ? null : () => beaconApi.changes(storeId), [
    storeId,
  ]);

export const useTrends = (storeId: number | null) =>
  useAsync(storeId === null ? null : () => beaconApi.trends(storeId), [
    storeId,
  ]);

export const useBenchmark = () => useAsync(() => beaconApi.benchmark(), []);

export const useAssumptions = () => useAsync(() => beaconApi.assumptions(), []);

export const useLlmStatus = () => useAsync(() => beaconApi.llmStatus(), []);

const POLL_MS = 1000;

/** Polls a job every second until it succeeds or fails. */
export function useJob(
  jobId: string | null,
  onDone?: (job: JobResponse) => void,
) {
  const [job, setJob] = useState<JobResponse | null>(null);
  const done = useRef(onDone);
  done.current = onDone;

  useEffect(() => {
    if (!jobId) {
      setJob(null);
      return;
    }
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const poll = async () => {
      try {
        const next = await beaconApi.job(jobId);
        if (stopped) return;
        setJob(next);
        if (next.status === "SUCCEEDED" || next.status === "FAILED") {
          done.current?.(next);
          return;
        }
      } catch {
        // transient error: keep polling
      }
      timer = setTimeout(poll, POLL_MS);
    };
    poll();
    return () => {
      stopped = true;
      if (timer) clearTimeout(timer);
    };
  }, [jobId]);

  return job;
}
