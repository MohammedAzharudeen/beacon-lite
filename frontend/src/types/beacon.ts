// Mirrors the backend API. Field names match the Java records exactly.

export type Platform = "SHOPIFY" | "GENERIC" | "REPLAY";
export type StoreStatus = "ADDING" | "ACTIVE" | "FAILED";
export type JobType = "ADD_STORE" | "REFRESH" | "SCHEDULED";
export type JobStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type JobStep =
  "DETECT" | "FETCH_CATALOG" | "FETCH_SIGNALS" | "BUILD_INSIGHTS";
export type ActionStatus = "TODO" | "DONE" | "DISMISSED";
export type DemandSignal =
  | "BEST_SELLER_COLLECTION"
  | "PROMOTED"
  | "SELL_OUT_SPEED"
  | "FULL_PRICE"
  | "RECENTLY_LAUNCHED";
export type ExclusionReason =
  | "PRE_ORDER"
  | "BACK_ORDER"
  | "NON_PHYSICAL"
  | "BUNDLE"
  | "LIKELY_DISCONTINUED";
export type Confidence = "HIGH" | "MEDIUM" | "LOW";
export type CheckStatus =
  | "CHECKED"
  | "CHECKED_VIA_ALTERNATIVE"
  | "NOT_CHECKED_ROBOTS"
  | "NOT_VERIFIABLE"
  | "NOT_AVAILABLE";
export type JourneyStageKey =
  | "DISCOVER"
  | "BROWSE"
  | "PRODUCT_PAGE"
  | "SIZE_STOCK"
  | "CART_CHECKOUT"
  | "COME_BACK";
export type ChangeType =
  | "SOLD_OUT"
  | "RESTOCKED"
  | "PRICE_CHANGED"
  | "PRODUCT_ADDED"
  | "PRODUCT_REMOVED"
  | "VARIANT_ADDED"
  | "VARIANT_REMOVED";

export interface ApiError {
  status: number;
  code: string;
  message: string;
  hint?: string;
  details?: Record<string, unknown>;
}

export interface StoreResponse {
  id: number;
  domain: string;
  displayName: string;
  platform: Platform;
  currency: string | null;
  status: StoreStatus;
  currentSnapshotId: number | null;
  lastCheckedAt: string | null;
  nextCheckAt: string | null;
  lastFailure: { code: string; message: string; at: string } | null;
}

export interface AddStoreResponse {
  storeId: number;
  jobId: string;
  status: JobStatus;
}

export interface RefreshResponse {
  jobId: string;
  joinedExisting: boolean;
}

export interface JobResponse {
  jobId: string;
  storeId: number;
  type: JobType;
  status: JobStatus;
  step: JobStep | null;
  productsRead: number;
  productsEstimate: number | null;
  error?: { code: string; message: string; hint: string };
}

export interface Money {
  amount: string;
  currency: string | null;
}

export interface EstimatedMoney {
  amount: string;
  currency: string | null;
  estimate: true;
}

export interface Action {
  actionKey: string;
  rank: number;
  title: string;
  evidence: string;
  category: string;
  atRiskPerWeek: EstimatedMoney | null;
  status: ActionStatus;
}

export interface SizeCell {
  label: string;
  available: boolean;
  core: boolean;
}

export interface RestockRow {
  productId: number;
  title: string;
  productType: string | null;
  imageUrl: string | null;
  productUrl: string;
  sizes: SizeCell[];
  soldOutSizes: number;
  totalSizes: number;
  price: Money;
  signals: DemandSignal[];
  demandScore: number;
  gapScore: number;
  atRiskPerWeek: EstimatedMoney;
  confidence: Confidence;
  swymSignalLabel: string;
}

export interface SizeGapRow {
  productId: number;
  title: string;
  productType: string | null;
  imageUrl: string | null;
  productUrl: string;
  sizes: SizeCell[];
  coreRange: string | null;
  missingCore: number;
  soldOutSizes: number;
  totalSizes: number;
  onlyOneLeft: boolean;
}

export interface ProductRef {
  productId: number;
  title: string;
  productType: string | null;
  imageUrl: string | null;
  productUrl: string;
}

export interface JourneyCheck {
  key: string;
  label: string;
  status: CheckStatus;
  score: number | null;
  evidence: string;
  fix: string | null;
  reason: string | null;
}

export interface JourneyStage {
  stage: JourneyStageKey;
  label: string;
  score: number | null;
  checksRun: number;
  checksTotal: number;
  checks: JourneyCheck[];
}

export interface CatalogQuality {
  fewImages: number;
  fewImagesExamples: ProductRef[];
  minImages: number;
  altText: { sampled: number; productsWithAllAlt: number; text: string };
  thinDescriptions: number;
  thinTitles: number;
  blankProductType: number;
  untagged: number;
  caseVariantTypes: string[][];
  spreadsheetErrors: string[];
}

export interface PricingInsights {
  compareAtEqualsPrice: number;
  discounted: number;
  discountBuckets: Record<string, number>;
  deepDiscount: number;
  deepDiscountPercent: number;
  deadStock: number;
  freeShipping: {
    text: string | null;
    threshold: number | null;
    medianPrice: number | null;
    ratio: number | null;
    source: string | null;
  };
}

export interface InsightReport {
  storeId: number;
  snapshotId: number;
  domain: string;
  assumptionsVersion: string;
  generatedAt: string;
  capturedAt: string;
  currency: string | null;
  catalog: {
    products: number;
    variants: number;
    capped: boolean;
    sampled: boolean;
  };
  categories: string[];
  headline: { text: string; metric: number; evidenceRef: string };
  kpis: {
    sizesSoldOutPct: number;
    soldOutVariants: number;
    atRiskPerWeek: EstimatedMoney;
    journeyScore: number | null;
    changedSinceLastCheck: {
      soldOut: number;
      restocked: number;
      priceChanges: number;
    } | null;
  };
  topActions: Action[];
  actions: Action[];
  restock: RestockRow[];
  restockTotal: number;
  exclusions: { reason: ExclusionReason; count: number; description: string }[];
  excludedProducts: {
    productId: number;
    title: string;
    productUrl: string;
    reason: ExclusionReason;
  }[];
  sizeGaps: SizeGapRow[];
  sizeGapTotal: number;
  sizeSummary: {
    productsAnalysed: number;
    productsMissingCoreSizes: number;
    onlyOneSizeLeft: number;
    fullySoldOut: number;
    noSizeOption: number;
    unrecognised: number;
  };
  promotedSoldOuts: {
    products: ProductRef[];
    promotedChecked: number;
    notice: string | null;
  };
  catalogQuality: CatalogQuality;
  pricing: PricingInsights;
  journey: JourneyStage[];
  journeyScore: number | null;
  dataNotes: string[];
}

export interface Page<T> {
  rows: T[];
  total: number;
  currency: string | null;
}

export interface ChangeView {
  id: number;
  type: ChangeType;
  productId: number | null;
  productTitle: string | null;
  productUrl: string | null;
  size: string | null;
  oldValue: string | null;
  newValue: string | null;
  windowStart: string;
  windowEnd: string;
}

export interface ChangesResponse {
  events: ChangeView[];
  windows: { start: string; end: string }[];
}

export interface TrendsResponse {
  points: {
    snapshotId: number;
    at: string;
    sizesSoldOutPct: number;
    journeyScore: number | null;
    atRiskPerWeek: string;
    soldOutVariants: number;
  }[];
}

export interface StoreMetrics {
  storeId: number;
  domain: string;
  currency: string | null;
  sizesSoldOutPct: number;
  atRiskPerWeek: string;
  atRiskIsEstimate: boolean;
  comparableJourneyScore: number | null;
  compareAtEqualsPrice: number;
  wishlistApp: boolean | null;
  notifyMe: boolean | null;
  preOrderExcluded: number;
  returnsDays: number | null;
  searchTest: CheckStatus;
}

export interface BenchmarkResponse {
  stores: StoreMetrics[];
  commonChecks: string[];
}

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

export interface ChatRequest {
  storeId: number;
  message: string;
  history: ChatTurn[];
}

export interface ChatResponse {
  answer: string;
  toolsUsed: string[];
  numbersVerified: number;
  validatorFallback: boolean;
  confidence: "HIGH" | "ESTIMATE" | "NOT_AVAILABLE";
  provider: "LLM" | "RULES" | "SCOPE";
  snapshotId: number | null;
}

export interface Assumptions {
  version: string;
  coreSizes: { rule: string; percent: number };
  exclusions: Record<string, unknown>;
  bestSellerCollectionPattern: string;
  demand: {
    weeklyDemandBaselineUnits: number;
    signalWeights: {
      bestSellerCollection: number;
      promoted: number;
      sellOutSpeed: number;
      fullPrice: number;
      recentlyLaunched: number;
    };
    recentlyLaunchedDays: number;
    confidence: { high: number; medium: number };
  };
  gapWeights: { core: number; edge: number };
  samples: Record<string, number>;
  search: Record<string, unknown>;
  catalog: Record<string, number>;
  pricing: Record<string, unknown>;
  signals: Record<string, number>;
  journeyThresholds: Record<string, { best: number; worst: number }>;
  detection: Record<string, unknown>;
}

export interface AssumptionsResponse {
  version: string;
  values: Assumptions;
}

/** Loading of the recorded demo stores at startup (demo mode). */
export interface DemoProgress {
  loading: boolean;
  loaded: number;
  total: number;
}

export interface LlmStatus {
  provider: string;
  baseUrl: string;
  model: string;
  reachable: boolean;
}
