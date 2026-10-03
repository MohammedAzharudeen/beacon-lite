import { useMemo, useState } from "react";
import { beaconApi } from "../../api/beaconApi";
import { useRestock } from "../../hooks/useBeacon";
import type { DemandSignal, RestockRow } from "../../types/beacon";
import {
  estimateAtRisk,
  useAssumptionValues,
} from "../assumptions/AssumptionsContext";
import { EstimateBadge } from "../common/EstimateBadge";
import { Money } from "../common/Money";
import { EmptyState, ErrorState, Skeleton } from "../common/States";
import { Tooltip } from "../common/Tooltip";
import { SizeStrip } from "./SizeStrip";
import { Thumb } from "../common/Thumb";

const PAGE = 50;

export const SIGNAL_LABEL: Record<DemandSignal, string> = {
  BEST_SELLER_COLLECTION: "Best-seller collection",
  PROMOTED: "Promoted",
  SELL_OUT_SPEED: "Selling fast",
  FULL_PRICE: "Full price",
  RECENTLY_LAUNCHED: "New",
};

interface Props {
  storeId: number;
  categories: string[];
}

export function RestockTable({ storeId, categories }: Props) {
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [offset, setOffset] = useState(0);
  const { data, loading, error, reload } = useRestock(storeId, {
    q,
    category,
    limit: PAGE,
    offset,
  });
  const { demand, edited } = useAssumptionValues();

  const rows = useMemo(() => {
    const list = data?.rows ?? [];
    if (!edited)
      return list.map((r) => ({
        row: r,
        atRisk: Number(r.atRiskPerWeek.amount),
      }));
    return list
      .map((r) => ({ row: r, atRisk: estimateAtRisk(r, demand) }))
      .sort((a, b) => b.atRisk - a.atRisk);
  }, [data, demand, edited]);

  return (
    <section className="card" aria-label="Restock list">
      <div className="hd">
        <div>
          <h3>
            Restock priorities{" "}
            {data && (
              <span className="pill mute">
                {data.total.toLocaleString("en-US")} products
              </span>
            )}
          </h3>
          <div className="sub">
            Demand × Gap × Value. A product needs at least one demand signal and
            a fully sold-out size.
            {edited && " Re-ranked with your edited assumptions."}
          </div>
        </div>
        <div className="toolbar">
          <label className="sr-only" htmlFor="restock-search">
            Search products
          </label>
          <input
            id="restock-search"
            className="input"
            placeholder="Search products"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setOffset(0);
            }}
          />
          <label className="sr-only" htmlFor="restock-category">
            Category
          </label>
          <select
            id="restock-category"
            className="select"
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setOffset(0);
            }}
          >
            <option value="">All categories</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <a
            className="btn ghost"
            href={beaconApi.restockCsvUrl(storeId)}
            download
          >
            Download CSV
          </a>
        </div>
      </div>
      <div className="body">
        {loading && <Skeleton height={200} />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && rows.length === 0 && (
          <EmptyState title="No restock candidates match" />
        )}
        {data && rows.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Sizes</th>
                  <th>Signals</th>
                  <th className="num">Price</th>
                  <th className="num">
                    Est. $ at risk / wk{" "}
                    <Tooltip text="price × demand score (sum of signal weights) × baseline units/week × share of sizes sold out (core sizes weigh more). An estimate from public data." />
                  </th>
                  <th>Confidence</th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ row, atRisk }) => (
                  <Row key={row.productId} row={row} atRisk={atRisk} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {data && data.total > PAGE && (
        <div className="pager">
          <span>
            {offset + 1}–{Math.min(offset + PAGE, data.total)} of{" "}
            {data.total.toLocaleString("en-US")}
          </span>
          <button
            type="button"
            className="btn ghost small"
            disabled={offset === 0}
            onClick={() => setOffset(Math.max(0, offset - PAGE))}
          >
            Previous
          </button>
          <button
            type="button"
            className="btn ghost small"
            disabled={offset + PAGE >= data.total}
            onClick={() => setOffset(offset + PAGE)}
          >
            Next
          </button>
        </div>
      )}
    </section>
  );
}

function Row({ row, atRisk }: { row: RestockRow; atRisk: number }) {
  return (
    <tr>
      <td>
        <div className="prod">
          <Thumb src={row.imageUrl} />
          <div>
            <a href={row.productUrl} target="_blank" rel="noreferrer">
              <b>{row.title}</b>
            </a>
            <div className="sub">{row.productType ?? "No type"}</div>
          </div>
        </div>
      </td>
      <td>
        <SizeStrip sizes={row.sizes} />
        <div className="sub">
          {row.totalSizes - row.soldOutSizes} of {row.totalSizes} sizes left
        </div>
      </td>
      <td>
        <div className="mini" style={{ gap: 4 }} title={row.swymSignalLabel}>
          {row.signals.map((s) => (
            <span key={s} className="pill acc">
              {SIGNAL_LABEL[s]}
            </span>
          ))}
        </div>
      </td>
      <td className="num">
        <Money
          amount={row.price.amount}
          currency={row.price.currency}
          decimals={2}
        />
      </td>
      <td className="num">
        <b>
          <Money amount={atRisk} currency={row.atRiskPerWeek.currency} />
        </b>{" "}
        <EstimateBadge />
      </td>
      <td>
        <span
          className={`pill ${row.confidence === "HIGH" ? "good" : row.confidence === "MEDIUM" ? "warn" : "mute"}`}
        >
          {row.confidence}
        </span>
      </td>
    </tr>
  );
}
