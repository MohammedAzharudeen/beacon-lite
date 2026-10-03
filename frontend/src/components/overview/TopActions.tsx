import type { Action, ActionStatus } from "../../types/beacon";
import { EmptyState } from "../common/States";
import { EstimateBadge } from "../common/EstimateBadge";
import { Money } from "../common/Money";
import { Tooltip } from "../common/Tooltip";
import { ActionStatusMenu } from "./ActionStatusMenu";

interface Props {
  actions: Action[];
  onStatus: (key: string, status: ActionStatus) => void;
  onOpen: (action: Action) => void;
}

export function TopActions({ actions, onStatus, onOpen }: Props) {
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
        {actions.map((a) => (
          <div
            key={a.actionKey}
            className={`act ${a.status === "DONE" ? "done" : ""}`}
          >
            <div className="n">{a.rank}</div>
            <div>
              <button
                type="button"
                className="t"
                style={{
                  background: "none",
                  border: "none",
                  padding: 0,
                  textAlign: "left",
                }}
                onClick={() => onOpen(a)}
              >
                {a.title}
              </button>{" "}
              <span className="pill mute">{a.category}</span>
              <div className="e">{a.evidence}</div>
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
        ))}
      </div>
    </section>
  );
}
