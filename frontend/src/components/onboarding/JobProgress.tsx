import type { JobResponse, JobStep } from "../../types/beacon";

const STEPS: { step: JobStep; label: string; detail: string }[] = [
  {
    step: "DETECT",
    label: "Detected platform",
    detail: "Reading robots.txt and checking the catalog feed",
  },
  {
    step: "FETCH_CATALOG",
    label: "Reading catalog",
    detail: "One request at a time, robots.txt obeyed",
  },
  {
    step: "FETCH_SIGNALS",
    label: "Checking store pages",
    detail: "Home page, policies or footer pages, search where allowed",
  },
  {
    step: "BUILD_INSIGHTS",
    label: "Building insights",
    detail: "Restock priorities, size gaps, journey scorecard",
  },
];

/** Step-by-step progress of a scan. */
export function JobProgress({ job }: { job: JobResponse | null }) {
  const current = job?.step ? STEPS.findIndex((s) => s.step === job.step) : -1;
  const finished = job?.status === "SUCCEEDED";
  return (
    <div className="steps" aria-live="polite">
      {STEPS.map((s, i) => {
        const state =
          finished || i < current ? "done" : i === current ? "run" : "wait";
        return (
          <div key={s.step} className="step">
            <span className={`ck ${state}`} aria-hidden="true">
              {state === "done" ? "✓" : i + 1}
            </span>
            <span className="tx">
              {s.label}
              <small>
                {s.step === "FETCH_CATALOG" && job && i === current
                  ? `${job.productsRead.toLocaleString("en-US")}${job.productsEstimate ? ` of ~${job.productsEstimate.toLocaleString("en-US")}` : ""} products`
                  : s.detail}
              </small>
            </span>
            {s.step === "FETCH_CATALOG" &&
            i === current &&
            job?.productsEstimate ? (
              <span className="prog" aria-hidden="true">
                <i
                  style={{
                    width: `${Math.min(100, (100 * job.productsRead) / job.productsEstimate)}%`,
                  }}
                />
              </span>
            ) : (
              <span className="sr-only">{state}</span>
            )}
          </div>
        );
      })}
      {job?.status === "FAILED" && job.error && (
        <div className="state error" role="alert">
          <b>{job.error.message}</b>
          {job.error.hint}
        </div>
      )}
    </div>
  );
}
