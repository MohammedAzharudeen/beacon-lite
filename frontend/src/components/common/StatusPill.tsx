import type { CheckStatus } from "../../types/beacon";

const LABELS: Record<
  CheckStatus,
  { text: string; tone: string; icon: string }
> = {
  CHECKED: { text: "Checked", tone: "good", icon: "✓" },
  CHECKED_VIA_ALTERNATIVE: {
    text: "Checked via alternative page",
    tone: "acc",
    icon: "↪",
  },
  NOT_CHECKED_ROBOTS: {
    text: "Not checked: blocked by robots.txt",
    tone: "mute",
    icon: "⦸",
  },
  NOT_VERIFIABLE: { text: "Not verifiable", tone: "warn", icon: "?" },
  NOT_AVAILABLE: { text: "Not available", tone: "mute", icon: "–" },
};

/** Check status with an icon and text (colour is never the only signal); reason on hover. */
export function StatusPill({
  status,
  reason,
}: {
  status: CheckStatus;
  reason?: string | null;
}) {
  const label = LABELS[status];
  return (
    <span className={`pill ${label.tone}`} title={reason ?? undefined}>
      <span aria-hidden="true">{label.icon}</span> {label.text}
    </span>
  );
}
