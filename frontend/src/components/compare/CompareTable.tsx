import { useBenchmark } from "../../hooks/useBeacon";
import { EstimateBadge } from "../common/EstimateBadge";
import { Money } from "../common/Money";
import { StatusPill } from "../common/StatusPill";
import { EmptyState, ErrorState, Skeleton } from "../common/States";
import { Tooltip } from "../common/Tooltip";

const yesNo = (v: boolean | null) =>
  v === null ? "Not checked" : v ? "Yes" : "No";

/** Stores side by side; journey scores use only checks that ran on every store. */
export function CompareTable() {
  const { data, loading, error, reload } = useBenchmark();
  return (
    <section className="card" aria-label="Compare stores">
      <div className="hd">
        <div>
          <h3>Compare stores</h3>
          <div className="sub">
            Journey scores recomputed on the {data?.commonChecks.length ?? "…"}{" "}
            checks that ran on every store
          </div>
        </div>
        <Tooltip text="A check skipped on one store (for example blocked by robots.txt) is left out for every store, so it never changes the others' scores." />
      </div>
      <div className="body">
        {loading && <Skeleton height={160} />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && data.stores.length < 2 && (
          <EmptyState title="Add another store to compare" />
        )}
        {data && data.stores.length >= 2 && (
          <div className="table-wrap">
            <table className="cmp">
              <thead>
                <tr>
                  <th>Metric</th>
                  {data.stores.map((s) => (
                    <th key={s.storeId}>{s.domain.replace(/^www\./, "")}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td>Sizes sold out</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>
                      <b>{s.sizesSoldOutPct}%</b>
                    </td>
                  ))}
                </tr>
                <tr>
                  <td>
                    $ at risk / week <EstimateBadge />
                  </td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>
                      <Money amount={s.atRiskPerWeek} currency={s.currency} />
                    </td>
                  ))}
                </tr>
                <tr>
                  <td>Journey score (common checks)</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>{s.comparableJourneyScore ?? "—"}</td>
                  ))}
                </tr>
                <tr>
                  <td>Compare-at = price</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>
                      {s.compareAtEqualsPrice.toLocaleString("en-US")}
                    </td>
                  ))}
                </tr>
                <tr>
                  <td>Wishlist app</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>{yesNo(s.wishlistApp)}</td>
                  ))}
                </tr>
                <tr>
                  <td>"Notify me" on sold-out pages</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>{yesNo(s.notifyMe)}</td>
                  ))}
                </tr>
                <tr>
                  <td>Pre-order items excluded</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>{s.preOrderExcluded}</td>
                  ))}
                </tr>
                <tr>
                  <td>Returns window</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>
                      {s.returnsDays === null
                        ? "Not stated / not checked"
                        : `${s.returnsDays} days`}
                    </td>
                  ))}
                </tr>
                <tr>
                  <td>Search test</td>
                  {data.stores.map((s) => (
                    <td key={s.storeId}>
                      <StatusPill status={s.searchTest} />
                    </td>
                  ))}
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}
