import { fireEvent, render, screen, within } from "@testing-library/react";
import sample from "./fixtures/sample-report.json";
import type {
  ChatResponse,
  InsightReport,
  StoreResponse,
} from "../types/beacon";
import { AddingStore } from "../components/onboarding/AddingStore";
import { JourneyScorecard } from "../components/journey/JourneyScorecard";
import { TopActions } from "../components/overview/TopActions";
import { ChatPanel } from "../components/layout/ChatPanel";
import { CatalogQuality } from "../components/catalog/CatalogQuality";
import { NotRestockCandidates } from "../components/restock/NotRestockCandidates";

const report = sample as unknown as InsightReport;

function mockFetch(routes: Record<string, unknown>) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      const key = Object.keys(routes).find((k) => url.startsWith(k));
      return new Response(JSON.stringify(key ? routes[key] : {}), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }),
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("JourneyScorecard", () => {
  it("shows coverage and robots-blocked checks as not checked", async () => {
    render(
      <JourneyScorecard
        stages={report.journey}
        overall={report.journeyScore}
      />,
    );
    expect(screen.getByText("based on 2 of 3 checks")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Browse/ }));
    const checks = screen.getByLabelText("Browse checks");
    expect(
      within(checks).getByText("Not checked: blocked by robots.txt", {
        exact: false,
      }),
    ).toBeInTheDocument();
    expect(
      within(checks).getByText("robots.txt: Disallow: /search"),
    ).toBeInTheDocument();
  });
});

describe("TopActions", () => {
  it("lists five actions with estimates on restock items", () => {
    render(
      <TopActions
        actions={report.topActions}
        onStatus={() => {}}
        onOpen={() => {}}
      />,
    );
    expect(screen.getAllByText("Estimate").length).toBeGreaterThanOrEqual(3);
    expect(screen.getByText(report.topActions[0].title)).toBeInTheDocument();
  });
});

describe("CatalogQuality", () => {
  it("reports sampled alt text and compare-at clean-up without overclaiming", () => {
    render(<CatalogQuality report={report} />);
    expect(
      screen.getByText("30 of 30 sampled products have alt text on all images"),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/all products are missing alt text/i),
    ).not.toBeInTheDocument();
  });
});

describe("NotRestockCandidates", () => {
  it("lists every exclusion reason with its count", () => {
    render(<NotRestockCandidates report={report} />);
    report.exclusions.forEach((e) =>
      expect(screen.getByText(e.description)).toBeInTheDocument(),
    );
  });
});

describe("ChatPanel", () => {
  it("shows evidence pills: numbers verified and tools used", async () => {
    const answer: ChatResponse = {
      answer: "Restock these first: Premier Road Ultra LTD Shoes.",
      toolsUsed: ["get_restock_priorities"],
      numbersVerified: 4,
      validatorFallback: false,
      confidence: "ESTIMATE",
      provider: "RULES",
      snapshotId: 1,
    };
    mockFetch({
      "/api/llm/status": {
        provider: "ollama",
        baseUrl: "x",
        model: "qwen2.5:7b",
        reachable: false,
      },
      "/api/chat": answer,
    });
    render(<ChatPanel storeId={2} storeName="Reebok" />);
    fireEvent.click(
      screen.getByRole("button", { name: "What should I restock first?" }),
    );
    expect(await screen.findByText("✓ 4 numbers verified")).toBeInTheDocument();
    expect(screen.getByText("Used: restock priorities")).toBeInTheDocument();
    expect(screen.getByText("Includes estimates")).toBeInTheDocument();
    expect(
      screen.getByText("AI model offline: rule-based answers"),
    ).toBeInTheDocument();
  });

  it("renders store text as plain text, never HTML", async () => {
    const answer: ChatResponse = {
      answer: '<img src=x onerror="alert(1)"> Title',
      toolsUsed: ["get_store_overview"],
      numbersVerified: 0,
      validatorFallback: true,
      confidence: "HIGH",
      provider: "LLM",
      snapshotId: 1,
    };
    mockFetch({
      "/api/llm/status": {
        provider: "ollama",
        baseUrl: "x",
        model: "m",
        reachable: true,
      },
      "/api/chat": answer,
    });
    render(<ChatPanel storeId={2} storeName="Reebok" />);
    fireEvent.change(screen.getByLabelText("Ask a question"), {
      target: { value: "hi" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Ask" }));
    expect(await screen.findByText(/<img src=x/)).toBeInTheDocument();
    expect(document.querySelector("img")).toBeNull();
    expect(
      screen.getByText("Safe answer: AI draft failed the number check"),
    ).toBeInTheDocument();
  });
});

describe("AddingStore", () => {
  const store: StoreResponse = {
    id: 4,
    domain: "kith.com",
    displayName: "Kith",
    platform: "SHOPIFY",
    currency: null,
    status: "ADDING",
    currentSnapshotId: null,
    lastCheckedAt: null,
    nextCheckAt: null,
    lastFailure: null,
  };

  it("names the store being added and shows the scan steps", () => {
    render(<AddingStore store={store} job={null} onRetry={() => {}} />);
    expect(
      screen.getByRole("heading", { name: "Adding Kith" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/kith\.com/)).toBeInTheDocument();
    expect(screen.getByText("Reading catalog")).toBeInTheDocument();
  });

  it("explains a failed first scan and offers a retry", () => {
    const onRetry = vi.fn();
    render(
      <AddingStore
        store={{
          ...store,
          status: "FAILED",
          lastFailure: {
            code: "STORE_BLOCKS_AUTOMATION",
            message: "This store blocks automated tools",
            at: "2026-10-04T10:00:00Z",
          },
        }}
        job={null}
        onRetry={onRetry}
      />,
    );
    expect(
      screen.getByRole("heading", { name: "Couldn't add kith.com" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("This store blocks automated tools", { exact: false }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(onRetry).toHaveBeenCalled();
  });
});
