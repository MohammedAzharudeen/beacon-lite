import type { InsightReport } from "../../types/beacon";
import { LocalTime } from "../common/LocalTime";
import { Tooltip } from "../common/Tooltip";

export function HeadlineCard({ report }: { report: InsightReport }) {
  return (
    <section className="card headline" aria-label="Headline insight">
      <div className="ic" aria-hidden="true">
        !
      </div>
      <div style={{ flex: 1 }}>
        <b>{report.headline.text}</b>
        <p>
          From the store's public catalog of{" "}
          {report.catalog.products.toLocaleString("en-US")} products, captured{" "}
          <LocalTime iso={report.capturedAt} />. Snapshot {report.snapshotId}.
        </p>
      </div>
      <Tooltip text="The single most important finding, built from the latest snapshot. 'Missing core sizes' = a middle size of the size run is sold out while other sizes are still in stock. $ figures are estimates (see Assumptions)." />
    </section>
  );
}
