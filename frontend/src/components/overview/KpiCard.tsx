import type { ReactNode } from "react";
import { Icon, type IconName } from "../common/Icon";
import { Sparkline } from "../common/Sparkline";
import { Tooltip } from "../common/Tooltip";

/** Change since the previous check; `better` decides the colour (lower is not always worse). */
export interface KpiDelta {
  text: string;
  better: boolean;
}

interface Props {
  label: string;
  icon?: IconName;
  value: ReactNode;
  unit?: string;
  tooltip: string;
  foot?: ReactNode;
  badge?: ReactNode;
  delta?: KpiDelta | null;
  trend?: { at: string; value: number }[];
}

export function KpiCard({
  label,
  icon,
  value,
  unit,
  tooltip,
  foot,
  badge,
  delta,
  trend,
}: Props) {
  return (
    <section className="card kpi" aria-label={label}>
      <div className="lab">
        {icon && (
          <span className="kpi-ic">
            <Icon name={icon} size={15} />
          </span>
        )}
        <span>{label}</span>
        <span className="spacer" />
        <Tooltip text={tooltip} label={`About ${label}`} />
      </div>
      <div className="val">
        {value}
        {unit && <small>{unit}</small>}
        {badge}
      </div>
      <div className="foot">
        {delta && (
          <span className={`delta ${delta.better ? "up" : "down"}`}>
            {delta.text}
          </span>
        )}
        {foot}
      </div>
      {trend && <Sparkline values={trend} label={label} />}
    </section>
  );
}
