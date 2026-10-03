import {
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip as ChartTooltip,
  YAxis,
} from "recharts";

interface Props {
  values: { at: string; value: number }[];
  label: string;
}

/** Small trend line across snapshots; hidden until there are two points. */
export function Sparkline({ values, label }: Props) {
  if (values.length < 2) {
    return <div className="foot">Trend appears after the next check</div>;
  }
  return (
    <div
      className="spark"
      aria-label={`${label} trend across ${values.length} checks`}
      role="img"
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={values}>
          <YAxis hide domain={["dataMin", "dataMax"]} />
          <ChartTooltip
            formatter={(v: number) => [v, label]}
            labelFormatter={(_, payload) =>
              payload?.[0]
                ? new Date(payload[0].payload.at).toLocaleString()
                : ""
            }
          />
          <Line
            type="monotone"
            dataKey="value"
            stroke="#4F46E5"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
