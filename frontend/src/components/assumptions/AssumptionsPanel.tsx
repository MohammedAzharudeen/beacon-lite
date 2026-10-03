import { useEffect, useRef } from "react";
import type { Assumptions } from "../../types/beacon";
import {
  useAssumptionValues,
  type DemandOverrides,
} from "./AssumptionsContext";

const WEIGHTS: {
  key: keyof Assumptions["demand"]["signalWeights"];
  label: string;
}[] = [
  { key: "bestSellerCollection", label: "In a best-seller collection" },
  { key: "promoted", label: "Promoted (home page, featured)" },
  { key: "sellOutSpeed", label: "Selling fast (sold out since last check)" },
  { key: "fullPrice", label: "Sold out at full price" },
  { key: "recentlyLaunched", label: "Recently launched" },
];

/** Every assumption, with the demand values editable to see the restock list re-rank instantly. */
export function AssumptionsPanel({ onClose }: { onClose: () => void }) {
  const { base, demand, edited, setDemand, reset } = useAssumptionValues();
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => closeRef.current?.focus(), []);
  if (!base || !demand) return null;

  const update = (next: Partial<DemandOverrides>) =>
    setDemand({ ...demand, ...next });

  return (
    <div
      className="drawer"
      role="dialog"
      aria-modal="true"
      aria-label="Assumptions"
      onKeyDown={(e) => e.key === "Escape" && onClose()}
    >
      <div className="panel">
        <div className="pagehd">
          <div>
            <h1 style={{ fontSize: 18 }}>Assumptions</h1>
            <p>
              Every judgment call behind the numbers. Version{" "}
              {base.version.slice(0, 12)}.
            </p>
          </div>
          <button
            ref={closeRef}
            type="button"
            className="btn ghost"
            onClick={onClose}
          >
            Close
          </button>
        </div>
        <h3 style={{ marginTop: 20 }}>Try different demand values</h3>
        <p className="sub">
          The restock list re-ranks instantly. Edits stay in this browser tab;
          change config/assumptions.yml to keep them.
        </p>
        <div className="kv">
          <label htmlFor="baseline">Units per week at full demand</label>
          <input
            id="baseline"
            className="input"
            type="number"
            min={0}
            value={demand.weeklyDemandBaselineUnits}
            onChange={(e) =>
              update({
                weeklyDemandBaselineUnits: Math.max(0, Number(e.target.value)),
              })
            }
          />
          {WEIGHTS.map((w) => (
            <WeightRow
              key={w.key}
              label={w.label}
              value={demand.signalWeights[w.key]}
              onChange={(v) =>
                update({
                  signalWeights: { ...demand.signalWeights, [w.key]: v },
                })
              }
            />
          ))}
        </div>
        {edited && (
          <button type="button" className="btn ghost small" onClick={reset}>
            Reset to saved values
          </button>
        )}
        <h3 style={{ marginTop: 24 }}>All values</h3>
        <pre
          style={{
            whiteSpace: "pre-wrap",
            fontSize: 12,
            background: "#F8FAFC",
            border: "1px solid var(--line)",
            borderRadius: 8,
            padding: 12,
            marginTop: 8,
          }}
        >
          {JSON.stringify(base, null, 2)}
        </pre>
      </div>
    </div>
  );
}

function WeightRow({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  const id = `w-${label}`;
  return (
    <>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        className="input"
        type="number"
        step={0.05}
        min={0}
        max={1}
        value={value}
        onChange={(e) =>
          onChange(Math.min(1, Math.max(0, Number(e.target.value))))
        }
      />
    </>
  );
}
