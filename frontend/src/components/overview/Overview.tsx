import { useTrends } from "../../hooks/useBeacon";
import type { Action, ActionStatus, InsightReport } from "../../types/beacon";
import { EstimateBadge } from "../common/EstimateBadge";
import { Money } from "../common/Money";
import { JourneyScorecard } from "../journey/JourneyScorecard";
import { HeadlineCard } from "./HeadlineCard";
import { KpiCard, type KpiDelta } from "./KpiCard";
import { TopActions } from "./TopActions";

interface Props {
  report: InsightReport;
  onStatus: (key: string, status: ActionStatus) => void;
  onOpenAction: (action: Action) => void;
}

export function Overview({ report, onStatus, onOpenAction }: Props) {
  const trends = useTrends(report.storeId);
  const points = trends.data?.points ?? [];
  const changes = report.kpis.changedSinceLastCheck;
  const dataDate = new Date(report.capturedAt).toLocaleString();
  const last = points[points.length - 1];
  const prev = points[points.length - 2];
  // Change against the previous check, shown only when this report is the latest point
  const delta = (
    pick: (p: (typeof points)[number]) => number | null,
    format: (d: number) => string,
    higherIsBetter: boolean,
  ): KpiDelta | null => {
    if (!last || !prev || last.snapshotId !== report.snapshotId) return null;
    const a = pick(last);
    const b = pick(prev);
    if (a === null || b === null) return null;
    const d = a - b;
    if (Math.abs(d) < 1e-9) return { text: "No change", better: true };
    return {
      text: `${d > 0 ? "▲" : "▼"} ${format(Math.abs(d))}`,
      better: higherIsBetter ? d > 0 : d < 0,
    };
  };
  return (
    <>
      <HeadlineCard report={report} />
      <div className="kpis">
        <KpiCard
          label="Sizes sold out"
          icon="shirt"
          delta={delta(
            (p) => p.sizesSoldOutPct,
            (d) => `${d.toFixed(1)} pts`,
            false,
          )}
          value={report.kpis.sizesSoldOutPct}
          unit="%"
          tooltip={`Share of all size/colour variants marked unavailable in the full catalog (${report.kpis.soldOutVariants.toLocaleString("en-US")} of ${report.catalog.variants.toLocaleString("en-US")}). Data from ${dataDate}.`}
          foot={`${report.kpis.soldOutVariants.toLocaleString("en-US")} of ${report.catalog.variants.toLocaleString("en-US")} variants`}
          trend={points.map((p) => ({ at: p.at, value: p.sizesSoldOutPct }))}
        />
        <KpiCard
          label="$ at risk / week"
          icon="trendDown"
          delta={delta(
            (p) => Number(p.atRiskPerWeek),
            (d) => Math.round(d).toLocaleString("en-US"),
            false,
          )}
          value={
            <Money
              amount={report.kpis.atRiskPerWeek.amount}
              currency={report.kpis.atRiskPerWeek.currency}
            />
          }
          badge={<EstimateBadge />}
          tooltip="Estimate: for every restock candidate, price × demand score × baseline units per week × share of sizes sold out, summed. Public data has no sales; see Assumptions to change the values."
          foot={`${report.restockTotal.toLocaleString("en-US")} restock candidates`}
          trend={points.map((p) => ({
            at: p.at,
            value: Number(p.atRiskPerWeek),
          }))}
        />
        <KpiCard
          label="Journey score"
          icon="route"
          delta={delta(
            (p) => p.journeyScore,
            (d) => `${d}`,
            true,
          )}
          value={report.journeyScore ?? "—"}
          unit="/ 100"
          tooltip="Average of the six stage scores; each stage uses only the checks that ran. Checks blocked by robots.txt are listed, never guessed."
          foot="Six stages from discover to come back"
          trend={points
            .filter((p) => p.journeyScore !== null)
            .map((p) => ({ at: p.at, value: p.journeyScore as number }))}
        />
        <KpiCard
          label="Changed since last check"
          icon="activity"
          value={
            changes
              ? changes.soldOut + changes.restocked + changes.priceChanges
              : "—"
          }
          tooltip="Sizes that sold out or came back, and price changes, between the last two checks. Timing comes only from check times."
          foot={
            changes
              ? `${changes.soldOut} sold out · ${changes.restocked} back · ${changes.priceChanges} prices`
              : "Available after the next check (in ~6 h)"
          }
        />
      </div>
      <TopActions
        actions={report.topActions}
        restock={report.restock}
        onStatus={onStatus}
        onOpen={onOpenAction}
      />
      <JourneyScorecard stages={report.journey} overall={report.journeyScore} />
      <section className="card" aria-label="Data notes">
        <div className="hd">
          <h3>About this data</h3>
        </div>
        <ul
          className="body"
          style={{ paddingLeft: 34, color: "var(--mute)", lineHeight: 1.7 }}
        >
          {report.dataNotes.map((n) => (
            <li key={n}>{n}</li>
          ))}
          <li>
            Snapshot {report.snapshotId} · assumptions{" "}
            {report.assumptionsVersion.slice(0, 12)}
          </li>
        </ul>
      </section>
    </>
  );
}
