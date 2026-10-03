import { fireEvent, render, screen } from "@testing-library/react";
import { Money } from "../components/common/Money";
import { StatusPill } from "../components/common/StatusPill";
import { EstimateBadge } from "../components/common/EstimateBadge";
import { KpiCard } from "../components/overview/KpiCard";
import { ActionStatusMenu } from "../components/overview/ActionStatusMenu";
import { TimeWindow } from "../components/common/LocalTime";

describe("Money", () => {
  it("shows the store currency", () => {
    render(<Money amount="2089.48" currency="USD" />);
    expect(screen.getByText("2,089 USD")).toBeInTheDocument();
  });

  it('says "Currency unknown" instead of assuming dollars', () => {
    render(<Money amount="10" currency={null} />);
    expect(screen.getByText("Currency unknown")).toBeInTheDocument();
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument();
  });
});

describe("StatusPill", () => {
  it.each([
    ["CHECKED", "Checked"],
    ["CHECKED_VIA_ALTERNATIVE", "Checked via alternative page"],
    ["NOT_CHECKED_ROBOTS", "Not checked: blocked by robots.txt"],
    ["NOT_VERIFIABLE", "Not verifiable"],
    ["NOT_AVAILABLE", "Not available"],
  ] as const)("shows text for %s, not just a colour", (status, text) => {
    render(
      <StatusPill status={status} reason="robots.txt: Disallow: /search" />,
    );
    const pill = screen.getByText(text, { exact: false });
    expect(pill).toBeInTheDocument();
    expect(pill.closest("span")).toHaveAttribute(
      "title",
      "robots.txt: Disallow: /search",
    );
  });
});

describe("KpiCard", () => {
  it("explains the number in a tooltip", async () => {
    render(
      <KpiCard
        label="Sizes sold out"
        value={33.9}
        unit="%"
        tooltip="Share of all variants marked unavailable"
      />,
    );
    fireEvent.click(
      screen.getByRole("button", { name: "About Sizes sold out" }),
    );
    expect(screen.getByRole("tooltip")).toHaveTextContent(
      "Share of all variants marked unavailable",
    );
  });

  it("carries the Estimate badge for estimates", () => {
    render(
      <KpiCard
        label="$ at risk / week"
        value="183,516 USD"
        tooltip="t"
        badge={<EstimateBadge />}
      />,
    );
    expect(screen.getByText("Estimate")).toBeInTheDocument();
  });

  it("says when the trend line will appear", () => {
    render(
      <KpiCard
        label="Journey score"
        value={76}
        tooltip="t"
        trend={[{ at: "2026-10-03T07:31:21Z", value: 76 }]}
      />,
    );
    expect(
      screen.getByText("Trend appears after the next check"),
    ).toBeInTheDocument();
  });
});

describe("ActionStatusMenu", () => {
  it("lets the merchant mark an action done or dismiss it", async () => {
    const onChange = vi.fn();
    render(<ActionStatusMenu status="TODO" onChange={onChange} />);
    fireEvent.click(screen.getByRole("button", { name: /To do/ }));
    fireEvent.click(screen.getByRole("menuitemradio", { name: "Dismiss" }));
    expect(onChange).toHaveBeenCalledWith("DISMISSED");
  });
});

describe("TimeWindow", () => {
  it("shows a window, never a single exact time", () => {
    render(
      <TimeWindow start="2026-10-03T08:00:00Z" end="2026-10-03T14:00:00Z" />,
    );
    expect(screen.getByText("→", { exact: false })).toBeInTheDocument();
    expect(document.querySelectorAll("time")).toHaveLength(2);
  });
});
