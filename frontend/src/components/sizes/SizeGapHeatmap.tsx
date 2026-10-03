import { useMemo, useState } from "react";
import { useSizeGaps } from "../../hooks/useBeacon";
import type { SizeGapRow } from "../../types/beacon";
import { EmptyState, ErrorState, Skeleton } from "../common/States";
import { Tooltip } from "../common/Tooltip";

const PAGE = 40;

interface Props {
  storeId: number;
  categories: string[];
  summary: {
    productsMissingCoreSizes: number;
    onlyOneSizeLeft: number;
    unrecognised: number;
    noSizeOption: number;
  };
}

/** Products × sizes in size order: green in stock, red sold out, dashed = size not offered. */
export function SizeGapHeatmap({ storeId, categories, summary }: Props) {
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [offset, setOffset] = useState(0);
  const { data, loading, error, reload } = useSizeGaps(storeId, {
    q,
    category,
    limit: PAGE,
    offset,
  });

  // Columns = union of sizes of the visible products, in the order they appear in the size runs
  const columns = useMemo(() => {
    const order: string[] = [];
    (data?.rows ?? []).forEach((row) =>
      row.sizes.forEach((s) => {
        if (!order.includes(s.label)) order.push(s.label);
      }),
    );
    return order;
  }, [data]);

  return (
    <section className="card" aria-label="Size gaps">
      <div className="hd">
        <div>
          <h3>
            Size gaps{" "}
            <span className="pill bad">
              {summary.productsMissingCoreSizes.toLocaleString("en-US")} missing
              core sizes
            </span>{" "}
            <span className="pill warn">
              {summary.onlyOneSizeLeft.toLocaleString("en-US")} with one size
              left
            </span>
          </h3>
          <div className="sub">
            Sizes sorted by size (the store's feed order isn't size order).{" "}
            {summary.unrecognised > 0 &&
              `${summary.unrecognised} products have size formats that weren't recognised and are left out, not guessed.`}
          </div>
        </div>
        <div className="toolbar">
          <label className="sr-only" htmlFor="gap-search">
            Search products
          </label>
          <input
            id="gap-search"
            className="input"
            placeholder="Search products"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setOffset(0);
            }}
          />
          <label className="sr-only" htmlFor="gap-category">
            Category
          </label>
          <select
            id="gap-category"
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
          <Tooltip text="Core sizes = the middle 50% of each product's sorted size run (configurable). Each run is separate, e.g. regular and wide. Outlined cells are core sizes." />
        </div>
      </div>
      <div className="heat">
        <div className="legend" style={{ marginBottom: 10 }}>
          <span>
            <i style={{ background: "var(--cell-in)" }} />
            In stock
          </span>
          <span>
            <i style={{ background: "var(--cell-out)" }} />
            Sold out (✕)
          </span>
          <span>
            <i style={{ border: "1px dashed #E5E7EB" }} />
            Not offered
          </span>
          <span>
            <i style={{ outline: "1px solid var(--ink2)" }} />
            Core size
          </span>
        </div>
        {loading && <Skeleton height={240} />}
        {error && <ErrorState error={error} onRetry={reload} />}
        {data && data.rows.length === 0 && (
          <EmptyState title="No size gaps match" />
        )}
        {data && data.rows.length > 0 && (
          <table>
            <thead>
              <tr>
                <th style={{ textAlign: "left" }}>Product</th>
                {columns.map((c) => (
                  <th key={c} scope="col">
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.rows.map((row) => (
                <HeatRow key={row.productId} row={row} columns={columns} />
              ))}
            </tbody>
          </table>
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

function HeatRow({ row, columns }: { row: SizeGapRow; columns: string[] }) {
  const byLabel = new Map(row.sizes.map((s) => [s.label, s]));
  return (
    <tr>
      <td className="name" title={row.title}>
        <a href={row.productUrl} target="_blank" rel="noreferrer">
          {row.title}
        </a>
        <div className="sub" style={{ fontWeight: 400 }}>
          {row.coreRange ? `Core ${row.coreRange}` : "No core range"} ·{" "}
          {row.totalSizes - row.soldOutSizes} of {row.totalSizes} left
        </div>
      </td>
      {columns.map((c) => {
        const s = byLabel.get(c);
        if (!s)
          return (
            <td key={c}>
              <div className="cell na" aria-label={`${c}: not offered`} />
            </td>
          );
        return (
          <td key={c}>
            <div
              className={`cell ${s.available ? "in" : "out"}`}
              style={s.core ? { outline: "1px solid var(--ink2)" } : undefined}
              aria-label={`${c}: ${s.available ? "in stock" : "sold out"}${s.core ? ", core size" : ""}`}
            >
              {s.available ? "" : "✕"}
            </div>
          </td>
        );
      })}
    </tr>
  );
}
