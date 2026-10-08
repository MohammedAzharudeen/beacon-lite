import type {
  ActionStatus,
  AddStoreResponse,
  ApiError,
  AssumptionsResponse,
  BenchmarkResponse,
  ChangesResponse,
  ChatRequest,
  ChatResponse,
  InsightReport,
  JobResponse,
  DemoProgress,
  LlmStatus,
  Page,
  RefreshResponse,
  RestockRow,
  SizeGapRow,
  StoreResponse,
  TrendsResponse,
} from "../types/beacon";

/** Thrown for any non-2xx response; carries the server's message and hint. */
export class BeaconApiError extends Error implements ApiError {
  status: number;
  code: string;
  hint?: string;
  details?: Record<string, unknown>;

  constructor(error: ApiError) {
    super(error.message);
    this.status = error.status;
    this.code = error.code;
    this.hint = error.hint;
    this.details = error.details;
  }
}

type Params = Record<string, string | number | boolean | undefined>;

function withParams(path: string, params?: Params): string {
  if (!params) return path;
  const query = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== "")
    .map(
      ([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`,
    )
    .join("&");
  return query ? `${path}?${query}` : path;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers:
        body === undefined ? undefined : { "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new BeaconApiError({
      status: 0,
      code: "NETWORK",
      message: "Can't reach Beacon Lite",
      hint: "Check that the app is running",
    });
  }
  if (!response.ok) {
    let error: ApiError = {
      status: response.status,
      code: "HTTP_" + response.status,
      message: "Something went wrong",
      hint: "Try again",
    };
    try {
      const parsed = (await response.json()) as Partial<ApiError>;
      error = { ...error, ...parsed, status: response.status };
    } catch {
      // body wasn't JSON; keep the generic message
    }
    throw new BeaconApiError(error);
  }
  return (await response.json()) as T;
}

const get = <T>(path: string, params?: Params) =>
  request<T>("GET", withParams(path, params));
const post = <T>(path: string, body?: unknown) =>
  request<T>("POST", path, body);
const put = <T>(path: string, body?: unknown) => request<T>("PUT", path, body);

export const beaconApi = {
  listStores: () => get<StoreResponse[]>("/api/stores"),
  store: (storeId: number) => get<StoreResponse>(`/api/stores/${storeId}`),
  addStore: (url: string) => post<AddStoreResponse>("/api/stores", { url }),
  refresh: (storeId: number) =>
    post<RefreshResponse>(`/api/stores/${storeId}/snapshot`),
  job: (jobId: string) => get<JobResponse>(`/api/jobs/${jobId}`),
  report: (storeId: number) =>
    get<InsightReport>(`/api/stores/${storeId}/report`),
  restock: (
    storeId: number,
    p: { category?: string; q?: string; limit?: number; offset?: number },
  ) => get<Page<RestockRow>>(`/api/stores/${storeId}/restock`, p),
  sizeGaps: (
    storeId: number,
    p: { category?: string; q?: string; limit?: number; offset?: number },
  ) => get<Page<SizeGapRow>>(`/api/stores/${storeId}/size-gaps`, p),
  restockCsvUrl: (storeId: number) => `/api/stores/${storeId}/restock.csv`,
  changes: (storeId: number) =>
    get<ChangesResponse>(`/api/stores/${storeId}/changes`),
  trends: (storeId: number) =>
    get<TrendsResponse>(`/api/stores/${storeId}/trends`),
  setActionStatus: (storeId: number, key: string, status: ActionStatus) =>
    put<{ actionKey: string; status: ActionStatus; updatedAt: string }>(
      `/api/stores/${storeId}/actions/${encodeURIComponent(key)}`,
      { status },
    ),
  briefUrl: (storeId: number) => `/api/stores/${storeId}/brief`,
  benchmark: (ids?: number[]) =>
    get<BenchmarkResponse>(
      "/api/benchmark",
      ids ? { storeIds: ids.join(",") } : undefined,
    ),
  chat: (req: ChatRequest) => post<ChatResponse>("/api/chat", req),
  assumptions: () => get<AssumptionsResponse>("/api/assumptions"),
  llmStatus: () => get<LlmStatus>("/api/llm/status"),
  demoProgress: () => get<DemoProgress>("/api/demo/progress"),
};
