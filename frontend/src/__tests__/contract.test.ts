import sample from "./fixtures/sample-report.json";
import type { InsightReport, JourneyCheck, RestockRow } from "../types/beacon";

// Exhaustive key lists: TypeScript fails the build if a key is missing or extra versus the types.
const REPORT_KEYS: Record<keyof InsightReport, true> = {
  storeId: true,
  snapshotId: true,
  domain: true,
  assumptionsVersion: true,
  generatedAt: true,
  capturedAt: true,
  currency: true,
  catalog: true,
  categories: true,
  headline: true,
  kpis: true,
  topActions: true,
  actions: true,
  restock: true,
  restockTotal: true,
  exclusions: true,
  excludedProducts: true,
  sizeGaps: true,
  sizeGapTotal: true,
  sizeSummary: true,
  promotedSoldOuts: true,
  catalogQuality: true,
  pricing: true,
  journey: true,
  journeyScore: true,
  dataNotes: true,
};
const RESTOCK_KEYS: Record<keyof RestockRow, true> = {
  productId: true,
  title: true,
  productType: true,
  imageUrl: true,
  productUrl: true,
  sizes: true,
  soldOutSizes: true,
  totalSizes: true,
  price: true,
  signals: true,
  demandScore: true,
  gapScore: true,
  atRiskPerWeek: true,
  confidence: true,
  swymSignalLabel: true,
};
const CHECK_KEYS: Record<keyof JourneyCheck, true> = {
  key: true,
  label: true,
  status: true,
  score: true,
  evidence: true,
  fix: true,
  reason: true,
};

const sorted = (o: object) => Object.keys(o).sort();

describe("API contract (real report recorded from www.reebok.com)", () => {
  it("has exactly the report fields the UI types expect", () => {
    expect(sorted(sample)).toEqual(sorted(REPORT_KEYS));
  });

  it("has exactly the restock row fields the UI types expect", () => {
    expect(sorted(sample.restock[0])).toEqual(sorted(RESTOCK_KEYS));
  });

  it("has exactly the journey check fields the UI types expect", () => {
    expect(sorted(sample.journey[0].checks[0])).toEqual(sorted(CHECK_KEYS));
  });

  it("sends money as strings with the currency alongside", () => {
    expect(typeof sample.kpis.atRiskPerWeek.amount).toBe("string");
    expect(sample.kpis.atRiskPerWeek.estimate).toBe(true);
    expect(typeof sample.restock[0].price.amount).toBe("string");
    expect(sample.currency).toBe("USD");
  });
});
