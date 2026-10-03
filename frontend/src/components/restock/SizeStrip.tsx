import type { SizeCell } from "../../types/beacon";

/** Sizes in order: green in stock, red sold out, outlined = core size. Labels are in the tooltip. */
export function SizeStrip({ sizes }: { sizes: SizeCell[] }) {
  const soldOut = sizes.filter((s) => !s.available).map((s) => s.label);
  return (
    <div
      className="mini"
      role="img"
      aria-label={`Sold out: ${soldOut.join(", ") || "none"}`}
      title={sizes
        .map(
          (s) =>
            `${s.label}${s.available ? "" : " (sold out)"}${s.core ? " · core" : ""}`,
        )
        .join(", ")}
    >
      {sizes.map((s) => (
        <i
          key={s.label}
          className={`${s.available ? "" : "x"} ${s.core ? "core" : ""}`}
        />
      ))}
    </div>
  );
}
