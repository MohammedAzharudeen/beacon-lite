import { useChanges } from "../../hooks/useBeacon";
import type { ChangeType, InsightReport } from "../../types/beacon";
import { TimeWindow } from "../common/LocalTime";
import { EmptyState, ErrorState, Skeleton } from "../common/States";

const LABEL: Record<ChangeType, { text: string; tone: string; icon: string }> =
  {
    SOLD_OUT: { text: "Sold out", tone: "bad", icon: "↓" },
    RESTOCKED: { text: "Back in stock", tone: "good", icon: "↑" },
    PRICE_CHANGED: { text: "Price changed", tone: "acc", icon: "$" },
    PRODUCT_ADDED: { text: "New product", tone: "good", icon: "+" },
    PRODUCT_REMOVED: { text: "Removed", tone: "mute", icon: "−" },
    VARIANT_ADDED: { text: "Size added", tone: "good", icon: "+" },
    VARIANT_REMOVED: { text: "Size removed", tone: "mute", icon: "−" },
  };

/** What changed between checks, shown as time windows (never an exact time). */
export function ChangesTimeline({
  storeId,
  report,
}: {
  storeId: number;
  report: InsightReport;
}) {
  const { data, loading, error, reload } = useChanges(storeId);
  return (
    <section className="card" aria-label="What changed">
      <div className="hd">
        <div>
          <h3>What changed</h3>
          <div className="sub">
            Timing comes only from checks: "sold out between 08:00 and 14:00",
            never an exact time
          </div>
        </div>
      </div>
      <div className="tl">
        {loading && <Skeleton height={160} />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data &&
          report.kpis.changedSinceLastCheck === null &&
          data.events.length === 0 && (
            <EmptyState title="Available after the next check">
              The first check is the baseline; changes appear about 6 hours
              later.
            </EmptyState>
          )}
        {data &&
          report.kpis.changedSinceLastCheck !== null &&
          data.events.length === 0 && (
            <EmptyState title="No changes between the last checks" />
          )}
        {data?.events.map((e) => {
          const l = LABEL[e.type];
          return (
            <div key={e.id} className="ev">
              <span className="tm">
                <TimeWindow start={e.windowStart} end={e.windowEnd} />
              </span>
              <span className={`pill ${l.tone}`} aria-hidden="true">
                {l.icon}
              </span>
              <span>
                <b>{l.text}</b>{" "}
                {e.productUrl ? (
                  <a href={e.productUrl} target="_blank" rel="noreferrer">
                    {e.productTitle}
                  </a>
                ) : (
                  e.productTitle
                )}
                {e.size && ` · size ${e.size}`}
              </span>
              <span className="sub">
                {e.type === "PRICE_CHANGED"
                  ? `${e.oldValue} → ${e.newValue}`
                  : ""}
              </span>
            </div>
          );
        })}
      </div>
    </section>
  );
}
