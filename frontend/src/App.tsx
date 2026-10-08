import { useCallback, useEffect, useMemo, useState } from "react";
import { beaconApi, BeaconApiError } from "./api/beaconApi";
import { AssumptionsPanel } from "./components/assumptions/AssumptionsPanel";
import { AssumptionsProvider } from "./components/assumptions/AssumptionsContext";
import { CatalogQuality } from "./components/catalog/CatalogQuality";
import { ChangesTimeline } from "./components/changes/ChangesTimeline";
import { CompareTable } from "./components/compare/CompareTable";
import { EmptyState, ErrorState, Skeleton } from "./components/common/States";
import { ChatPanel } from "./components/layout/ChatPanel";
import { TopBar } from "./components/layout/TopBar";
import { JobProgress } from "./components/onboarding/JobProgress";
import { Icon } from "./components/common/Icon";
import { AddingStore } from "./components/onboarding/AddingStore";
import { WelcomeAddStore } from "./components/onboarding/WelcomeAddStore";
import { Overview } from "./components/overview/Overview";
import { NotRestockCandidates } from "./components/restock/NotRestockCandidates";
import { RestockTable } from "./components/restock/RestockTable";
import { SizeGapHeatmap } from "./components/sizes/SizeGapHeatmap";
import {
  useAssumptions,
  useDemoProgress,
  useJob,
  useReport,
  useStores,
} from "./hooks/useBeacon";
import type { Action, ActionStatus } from "./types/beacon";

const TABS = [
  "Overview",
  "Restock",
  "Sizes",
  "Changes",
  "Catalog",
  "Compare",
] as const;
type Tab = (typeof TABS)[number];

export default function App() {
  const stores = useStores();
  const assumptions = useAssumptions();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [tab, setTab] = useState<Tab>("Overview");
  const [jobId, setJobId] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [addError, setAddError] = useState<BeaconApiError | null>(null);
  const [showAssumptions, setShowAssumptions] = useState(false);
  const [openAction, setOpenAction] = useState<Action | null>(null);

  const list = useMemo(() => stores.data ?? [], [stores.data]);
  const selected = list.find((s) => s.id === selectedId) ?? null;
  const report = useReport(selected?.currentSnapshotId ? selected.id : null);

  useEffect(() => {
    if (selectedId === null && list.length > 0) setSelectedId(list[0].id);
  }, [list, selectedId]);

  // A store still on its first scan, without a tracked job (page reloaded or store switched):
  // poll the store list until the scan finishes
  const firstScanUntracked =
    selected !== null &&
    !selected.currentSnapshotId &&
    selected.status === "ADDING" &&
    jobId === null;
  const reloadStores = stores.reload;
  useEffect(() => {
    if (!firstScanUntracked) return;
    const t = setInterval(reloadStores, 3000);
    return () => clearInterval(t);
  }, [firstScanUntracked, reloadStores]);

  // Demo mode loads its recorded history at startup; show progress, then load the finished data
  const demo = useDemoProgress(() => {
    stores.reload();
    report.reload();
  });

  const job = useJob(jobId, (done) => {
    stores.reload();
    if (done.status === "SUCCEEDED") {
      setJobId(null);
      report.reload();
    }
  });

  const add = useCallback(
    async (url: string) => {
      setAdding(true);
      setAddError(null);
      try {
        const res = await beaconApi.addStore(url);
        setJobId(res.jobId);
        setSelectedId(res.storeId);
        stores.reload();
      } catch (e) {
        const err = e as BeaconApiError;
        const existing = err.details?.storeId;
        if (
          err.code === "STORE_ALREADY_TRACKED" &&
          typeof existing === "number"
        ) {
          setSelectedId(existing);
        } else {
          setAddError(err);
        }
      } finally {
        setAdding(false);
      }
    },
    [stores],
  );

  const refresh = useCallback(async () => {
    if (!selected) return;
    try {
      const res = await beaconApi.refresh(selected.id);
      setJobId(res.jobId);
    } catch (e) {
      setAddError(e as BeaconApiError);
    }
  }, [selected]);

  const setStatus = useCallback(
    async (key: string, status: ActionStatus) => {
      if (!selected) return;
      await beaconApi.setActionStatus(selected.id, key, status);
      report.reload();
    },
    [selected, report],
  );

  if (stores.loading && !stores.data) {
    return (
      <div className="wrap">
        <Skeleton height={300} />
      </div>
    );
  }
  if (stores.error) {
    return (
      <div className="wrap">
        <ErrorState error={stores.error} onRetry={stores.reload} />
      </div>
    );
  }

  return (
    <AssumptionsProvider
      base={
        assumptions.data?.values
          ? { ...assumptions.data.values, version: assumptions.data.version }
          : null
      }
    >
      <TopBar
        stores={list}
        selected={selected}
        onSelect={(id) => {
          setSelectedId(id);
          setJobId(null);
        }}
        onAdd={add}
        onRefresh={refresh}
        refreshing={jobId !== null}
        adding={adding}
        onAssumptions={() => setShowAssumptions(true)}
      />
      <div className="banner">
        <span>
          <b>Public data only.</b> robots.txt is obeyed on every request; $
          figures are estimates until intent data (Swym) is connected.
        </span>
        <a href="/swagger-ui.html" style={{ color: "#CBD5E1" }}>
          API
        </a>
      </div>
      {addError && list.length > 0 && (
        <div className="notice" role="alert">
          <b>{addError.message}.</b> {addError.hint}{" "}
          <button
            type="button"
            className="btn ghost small"
            onClick={() => setAddError(null)}
          >
            Dismiss
          </button>
        </div>
      )}
      {selected?.lastFailure && selected.currentSnapshotId && (
        <div className="notice" role="status">
          <b>Last refresh failed:</b> {selected.lastFailure.message}. The
          results below are from the previous successful check.
        </div>
      )}
      {demo?.loading ? (
        <div className="welcome">
          <div className="card wcard" role="status" aria-live="polite">
            <h2>Loading demo history</h2>
            <p>
              Replaying the recorded snapshots of the demo stores, oldest first,
              so changes and trends are ready. {demo.loaded} of {demo.total}{" "}
              snapshots loaded.
            </p>
            <div className="prog wide" aria-hidden="true">
              <i
                style={{
                  width: `${demo.total ? (100 * demo.loaded) / demo.total : 0}%`,
                }}
              />
            </div>
          </div>
        </div>
      ) : list.length === 0 ? (
        <WelcomeAddStore
          onAdd={add}
          adding={adding}
          job={job}
          error={addError}
        />
      ) : selected && !selected.currentSnapshotId ? (
        <AddingStore
          store={selected}
          job={job?.storeId === selected.id ? job : null}
          onRetry={refresh}
        />
      ) : (
        <div className="wrap">
          <main className="main">
            <div className="pagehd">
              <div>
                <h1>{selected?.displayName}</h1>
                <p>
                  {selected?.domain} ·{" "}
                  {report.data
                    ? `${report.data.catalog.products.toLocaleString("en-US")} products`
                    : "…"}
                  {report.data?.catalog.capped &&
                    " · catalog capped at 25,000 (public feed limit)"}
                </p>
              </div>
              <div role="tablist" aria-label="Sections" className="tabs">
                {TABS.map((t) => (
                  <button
                    key={t}
                    type="button"
                    role="tab"
                    aria-selected={tab === t}
                    onClick={() => setTab(t)}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>
            {jobId && job && (
              <section className="card" aria-label="Refresh progress">
                <div className="body">
                  <JobProgress job={job} />
                </div>
              </section>
            )}
            {report.loading && !report.data && <Skeleton height={400} />}
            {report.error && (
              <ErrorState error={report.error} onRetry={report.reload} />
            )}
            {report.data && selected && (
              <div
                // A new snapshot remounts the tab, so trends, restock and size data are fetched again
                key={`${selected.id}-${report.data.snapshotId}`}
                role="tabpanel"
                aria-label={tab}
                style={{ display: "flex", flexDirection: "column", gap: 20 }}
              >
                {tab === "Overview" && (
                  <Overview
                    report={report.data}
                    onStatus={setStatus}
                    onOpenAction={(a) => setOpenAction(a)}
                  />
                )}
                {tab === "Restock" && (
                  <>
                    <RestockTable
                      storeId={selected.id}
                      categories={report.data.categories}
                    />
                    <NotRestockCandidates report={report.data} />
                  </>
                )}
                {tab === "Sizes" && (
                  <SizeGapHeatmap
                    storeId={selected.id}
                    categories={report.data.categories}
                    summary={report.data.sizeSummary}
                  />
                )}
                {tab === "Changes" && (
                  <ChangesTimeline storeId={selected.id} report={report.data} />
                )}
                {tab === "Catalog" && <CatalogQuality report={report.data} />}
                {tab === "Compare" && <CompareTable />}
                <div className="toolbar">
                  <a
                    className="btn ghost"
                    href={beaconApi.briefUrl(selected.id)}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <Icon name="sparkles" />
                    Open Insight Brief (print-ready)
                  </a>
                </div>
              </div>
            )}
            {!report.data && !report.loading && !report.error && (
              <EmptyState title="No report yet" />
            )}
          </main>
          {selected && (
            <ChatPanel storeId={selected.id} storeName={selected.displayName} />
          )}
        </div>
      )}
      {showAssumptions && (
        <AssumptionsPanel onClose={() => setShowAssumptions(false)} />
      )}
      {openAction && (
        <div
          className="drawer"
          role="dialog"
          aria-modal="true"
          aria-label="Action evidence"
          onClick={() => setOpenAction(null)}
        >
          <div className="panel" onClick={(e) => e.stopPropagation()}>
            <div className="pagehd">
              <h1 style={{ fontSize: 18 }}>{openAction.title}</h1>
              <button
                type="button"
                className="btn ghost"
                onClick={() => setOpenAction(null)}
                autoFocus
              >
                Close
              </button>
            </div>
            <p style={{ marginTop: 12 }}>
              <span className="pill mute">{openAction.category}</span>
            </p>
            <p style={{ marginTop: 12, lineHeight: 1.6 }}>
              {openAction.evidence}
            </p>
            {openAction.actionKey.startsWith("RESTOCK:") && report.data && (
              <p style={{ marginTop: 12 }}>
                <a
                  href={
                    report.data.restock.find(
                      (r) =>
                        `RESTOCK:product:${r.productId}` ===
                        openAction.actionKey,
                    )?.productUrl
                  }
                  target="_blank"
                  rel="noreferrer"
                >
                  Open the live product page
                </a>
              </p>
            )}
            <p className="sub" style={{ marginTop: 12 }}>
              From snapshot {report.data?.snapshotId}.
            </p>
          </div>
        </div>
      )}
    </AssumptionsProvider>
  );
}
