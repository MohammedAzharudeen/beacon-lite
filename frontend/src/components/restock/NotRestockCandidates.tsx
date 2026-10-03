import type { InsightReport } from "../../types/beacon";
import { EmptyState } from "../common/States";

/** Items that only look sold out, or aren't stock, with the reason; nothing is dropped silently. */
export function NotRestockCandidates({ report }: { report: InsightReport }) {
  return (
    <section className="card" aria-label="Not restock candidates">
      <div className="hd">
        <div>
          <h3>Not restock candidates</h3>
          <div className="sub">
            Kept out of the restock list, with the reason
          </div>
        </div>
      </div>
      <div className="body">
        {report.exclusions.length === 0 && (
          <EmptyState title="Nothing excluded" />
        )}
        <div className="stat-list">
          {report.exclusions.map((e) => (
            <div key={e.reason}>
              <span>{e.description}</span>
              <b>{e.count.toLocaleString("en-US")}</b>
            </div>
          ))}
        </div>
        {report.excludedProducts.length > 0 && (
          <details style={{ marginTop: 12 }}>
            <summary>
              Show products ({report.excludedProducts.length} of{" "}
              {report.exclusions.reduce((n, e) => n + e.count, 0)} listed)
            </summary>
            <ul style={{ margin: "8px 0 0 18px" }}>
              {report.excludedProducts.map((p) => (
                <li key={p.productId}>
                  <a href={p.productUrl} target="_blank" rel="noreferrer">
                    {p.title}
                  </a>{" "}
                  · {p.reason.replace("_", " ").toLowerCase()}
                </li>
              ))}
            </ul>
          </details>
        )}
      </div>
    </section>
  );
}
