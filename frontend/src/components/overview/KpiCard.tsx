import type { ReactNode } from "react";
import { Sparkline } from "../common/Sparkline";
import { Tooltip } from "../common/Tooltip";

interface Props {
  label: string;
  value: ReactNode;
  unit?: string;
  tooltip: string;
  foot?: ReactNode;
  badge?: ReactNode;
  trend?: { at: string; value: number }[];
}

export function KpiCard({
  label,
  value,
  unit,
  tooltip,
  foot,
  badge,
  trend,
}: Props) {
  return (
    <section className="card kpi" aria-label={label}>
      <div className="lab">
        <span>{label}</span>
        <Tooltip text={tooltip} label={`About ${label}`} />
      </div>
      <div className="val">
        {value}
        {unit && <small>{unit}</small>}
        {badge}
      </div>
      {foot && <div className="foot">{foot}</div>}
      {trend && <Sparkline values={trend} label={label} />}
    </section>
  );
}
