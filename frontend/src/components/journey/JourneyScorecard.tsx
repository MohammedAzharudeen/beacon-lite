import { useState } from "react";
import type { JourneyStage } from "../../types/beacon";
import { StatusPill } from "../common/StatusPill";
import { Tooltip } from "../common/Tooltip";

const LOW = 50;

export function JourneyScorecard({
  stages,
  overall,
}: {
  stages: JourneyStage[];
  overall: number | null;
}) {
  const [open, setOpen] = useState<string | null>(null);
  const selected = stages.find((s) => s.stage === open) ?? null;
  return (
    <section className="card" aria-label="Shopper journey scorecard">
      <div className="hd">
        <div>
          <h3>
            Shopper journey{" "}
            {overall !== null && (
              <span className="pill acc">{overall} / 100</span>
            )}
          </h3>
          <div className="sub">
            Each stage uses only the checks that ran; click a stage for evidence
            and fixes
          </div>
        </div>
        <Tooltip text="Stage score = average of its checks that ran (equal weights). Overall = average of stage scores. Checks blocked by robots.txt or not verifiable are listed but never guessed." />
      </div>
      <div className="journey">
        {stages.map((s) => (
          <button
            key={s.stage}
            type="button"
            className={`stage ${s.score !== null && s.score < LOW ? "low" : ""}`}
            aria-expanded={open === s.stage}
            onClick={() => setOpen(open === s.stage ? null : s.stage)}
          >
            <div className="nm">{s.label}</div>
            <div className="sc">{s.score ?? "—"}</div>
            <div className="bar" aria-hidden="true">
              <i
                className={
                  s.score === null
                    ? ""
                    : s.score >= 80
                      ? "good"
                      : s.score >= LOW
                        ? "warn"
                        : "bad"
                }
                style={{ width: `${s.score ?? 0}%` }}
              />
            </div>
            <div className="note">
              based on {s.checksRun} of {s.checksTotal} checks
            </div>
          </button>
        ))}
      </div>
      {selected && (
        <div className="checks" aria-label={`${selected.label} checks`}>
          {selected.checks.map((c) => (
            <div key={c.key} className="check">
              <div>
                <b>{c.label}</b>
                <div className="sub">
                  {c.score === null ? "No score" : `Score ${c.score}`}
                </div>
              </div>
              <div>
                <StatusPill status={c.status} reason={c.reason} />
              </div>
              <div>
                <div>{c.evidence}</div>
                {c.fix && <div className="fix">Fix: {c.fix}</div>}
                {c.reason && <div className="why">{c.reason}</div>}
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
