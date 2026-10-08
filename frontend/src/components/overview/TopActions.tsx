import type {
  Action,
  ActionStatus,
  Confidence,
  DemandSignal,
  RestockRow,
} from "../../types/beacon";
import { EmptyState } from "../common/States";
import { EstimateBadge } from "../common/EstimateBadge";
import { Icon, type IconName } from "../common/Icon";
import { Money } from "../common/Money";
import { Thumb } from "../common/Thumb";
import { Tooltip } from "../common/Tooltip";
import { SizeStrip } from "../restock/SizeStrip";
import { ActionStatusMenu } from "./ActionStatusMenu";

interface Props {
  actions: Action[];
  /** Restock rows, to show the product photo, sizes and signals of restock actions */
  restock?: RestockRow[];
  onStatus: (key: string, status: ActionStatus) => void;
  onOpen: (action: Action) => void;
}

const SIGNAL: Record<DemandSignal, { label: string; icon: IconName }> = {
  BEST_SELLER_COLLECTION: { label: "Best seller", icon: "star" },
  SELL_OUT_SPEED: { label: "Selling fast", icon: "zap" },
  PROMOTED: { label: "Promoted", icon: "flag" },
  FULL_PRICE: { label: "Full price", icon: "tag" },
  RECENTLY_LAUNCHED: { label: "New", icon: "sprout" },
};

const CATEGORY_ICON: Record<string, IconName> = {
  Restock: "shirt",
  Catalog: "tag",
  Discovery: "flag",
  Discover: "activity",
  Browse: "route",
  "Product page": "info",
  "Size & stock": "shirt",
  "Cart & checkout": "tag",
  "Come back": "refresh",
};

const CONFIDENCE_LABEL: Record<Confidence, string> = {
  HIGH: "High confidence",
  MEDIUM: "Medium confidence",
  LOW: "Low confidence",
};

function restockRow(action: Action, rows: RestockRow[]) {
  const id = action.actionKey.startsWith("RESTOCK:product:")
    ? Number(action.actionKey.slice("RESTOCK:product:".length))
    : null;
  return id === null ? undefined : rows.find((r) => r.productId === id);
}

export function TopActions({ actions, restock = [], onStatus, onOpen }: Props) {
  return (
    <section className="card" aria-label="Top actions">
      <div className="hd">
        <div>
          <h3>Top 5 actions</h3>
          <div className="sub">
            Restock items ranked by estimated $ at risk, plus the most severe
            catalog and journey findings
          </div>
        </div>
        <Tooltip text="Top 3 restock items by estimated weekly $ at risk, then the 2 most severe other findings. Dismissed actions leave this list but stay in the full lists." />
      </div>
      <div className="actions">
        {actions.length === 0 && <EmptyState title="No actions right now" />}
        {actions.map((a) => {
          const row = restockRow(a, restock);
          return (
            <div
              key={a.actionKey}
              className={`act ${a.status === "DONE" ? "done" : ""}`}
            >
              <div className="n">{a.rank}</div>
              {row ? (
                <Thumb src={row.imageUrl} />
              ) : (
                <div className="act-ic" aria-hidden="true">
                  <Icon name={CATEGORY_ICON[a.category] ?? "route"} />
                </div>
              )}
              <div className="act-body">
                <button type="button" className="t" onClick={() => onOpen(a)}>
                  {a.title}
                </button>
                {row ? (
                  <div className="act-meta">
                    <SizeStrip sizes={row.sizes} />
                    <span className="e">
                      {row.totalSizes - row.soldOutSizes} of {row.totalSizes}{" "}
                      sizes left
                    </span>
                    {row.signals.map((s) => (
                      <span key={s} className="chip">
                        <Icon name={SIGNAL[s].icon} size={12} />
                        {SIGNAL[s].label}
                      </span>
                    ))}
                    <span
                      className={`conf ${row.confidence.toLowerCase()}`}
                      title={CONFIDENCE_LABEL[row.confidence]}
                    >
                      <i aria-hidden="true" />
                      {CONFIDENCE_LABEL[row.confidence]}
                    </span>
                  </div>
                ) : (
                  <div className="act-meta">
                    <span className="chip soft">{a.category}</span>
                    <span className="e">{a.evidence}</span>
                  </div>
                )}
              </div>
              <div className="money">
                {a.atRiskPerWeek ? (
                  <>
                    <Money
                      amount={a.atRiskPerWeek.amount}
                      currency={a.atRiskPerWeek.currency}
                    />
                    <small>
                      per week <EstimateBadge />
                    </small>
                  </>
                ) : null}
              </div>
              <ActionStatusMenu
                status={a.status}
                onChange={(s) => onStatus(a.actionKey, s)}
              />
            </div>
          );
        })}
      </div>
    </section>
  );
}
