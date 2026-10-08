import type { ReactNode } from "react";
import type { InsightReport } from "../../types/beacon";
import { Icon } from "../common/Icon";
import { LocalTime } from "../common/LocalTime";
import { Tooltip } from "../common/Tooltip";

const NUMBER = /(\d[\d,]*(?:\.\d+)?%?(?: USD| [A-Z]{3}\b)?)/;

/** Wraps every figure in the sentence so it stands out; the text itself comes from the report. */
function emphasise(text: string): ReactNode[] {
  return text
    .split(NUMBER)
    .map((part, i) => (i % 2 === 1 ? <mark key={i}>{part}</mark> : part));
}

/** The briefing: the report's headline, what changed, and where to start. Built from report data only. */
export function HeadlineCard({ report }: { report: InsightReport }) {
  const changes = report.kpis.changedSinceLastCheck;
  const first = report.restock[0];
  const whole = (amount: string) =>
    Math.round(Number(amount)).toLocaleString("en-US");
  return (
    <section className="card briefing" aria-label="Headline insight">
      <div className="eyebrow">
        <span className="spark-ic">
          <Icon name="sparkles" size={14} />
        </span>
        Today's briefing
        <span className="sep">·</span>
        <span className="when">
          captured <LocalTime iso={report.capturedAt} /> · snapshot{" "}
          {report.snapshotId}
        </span>
        <span className="spacer" />
        <Tooltip text="The single most important finding, built from the latest snapshot. 'Missing core sizes' = a middle size of the size run is sold out while other sizes are still in stock. $ figures are estimates (see Assumptions)." />
      </div>
      <h2 className="lead">{emphasise(report.headline.text)}</h2>
      <p className="more">
        {changes &&
          changes.soldOut + changes.restocked + changes.priceChanges > 0 && (
            <>
              Since the last check, <b>{changes.soldOut}</b> sizes sold out and{" "}
              <b>{changes.restocked}</b> came back.{" "}
            </>
          )}
        {first && (
          <>
            Start with <b>{first.title}</b> (about{" "}
            {whole(first.atRiskPerWeek.amount)}{" "}
            {first.atRiskPerWeek.currency ?? ""} a week, estimate).
          </>
        )}
        {!first && (
          <>
            From the store's public catalog of{" "}
            {report.catalog.products.toLocaleString("en-US")} products.
          </>
        )}
      </p>
    </section>
  );
}
