import type { JobResponse, StoreResponse } from "../../types/beacon";
import { JobProgress } from "./JobProgress";

interface Props {
  store: StoreResponse;
  /** The running scan, when this page started it; null after a reload or a store switch. */
  job: JobResponse | null;
  onRetry: () => void;
}

/** First scan of a newly added store: names the store, shows progress, or explains a failure. */
export function AddingStore({ store, job, onRetry }: Props) {
  // A retry in progress shows its steps, even though the store still carries the last failure
  const running = job !== null && job.status !== "FAILED";
  const failure = running
    ? null
    : job?.status === "FAILED" && job.error
      ? job.error
      : store.status === "FAILED"
        ? store.lastFailure
        : null;

  if (failure) {
    return (
      <div className="welcome">
        <div className="card wcard" role="alert">
          <h2>Couldn't add {store.domain}</h2>
          <p>
            <b>{failure.message}.</b>{" "}
            {"hint" in failure && failure.hint ? failure.hint : ""}
          </p>
          <div className="demo">
            <button type="button" onClick={onRetry}>
              Try again
            </button>
          </div>
          <div className="demo">
            Or add a different store with the address bar above.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="welcome">
      <div className="card wcard" aria-label={`Adding ${store.domain}`}>
        <h2>Adding {store.displayName}</h2>
        <p>
          {store.domain} · reading the public catalog and store pages, one
          request at a time. This usually takes a few minutes; you can switch to
          another store from the store picker meanwhile.
        </p>
        <JobProgress job={job} />
        {!job && (
          <div className="demo" role="status">
            Scan in progress. This page updates when it finishes.
          </div>
        )}
      </div>
    </div>
  );
}
