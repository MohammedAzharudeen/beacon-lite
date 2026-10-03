import { useTrends } from "../../hooks/useBeacon";
import type { Action, ActionStatus, InsightReport } from "../../types/beacon";
import { EstimateBadge } from "../common/EstimateBadge";
import { Money } from "../common/Money";
import { JourneyScorecard } from "../journey/JourneyScorecard";
import { HeadlineCard } from "./HeadlineCard";
import { KpiCard } from "./KpiCard";
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
  return (
    <>
      <HeadlineCard report={report} />
      <div className="kpis">
        <KpiCard
          label="Sizes sold out"
          value={report.kpis.sizesSoldOutPct}
          unit="%"
          tooltip={`Share of all size/colour variants marked unavailable in the full catalog (${report.kpis.soldOutVariants.toLocaleString("en-US")} of ${report.catalog.variants.toLocaleString("en-US")}). Data from ${dataDate}.`}
          foot={`${report.kpis.soldOutVariants.toLocaleString("en-US")} of ${report.catalog.variants.toLocaleString("en-US")} variants`}
          trend={points.map((p) => ({ at: p.at, value: p.sizesSoldOutPct }))}
        />
        <KpiCard
          label="$ at risk / week"
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
