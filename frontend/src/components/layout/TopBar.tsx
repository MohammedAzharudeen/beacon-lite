import { useEffect, useRef, useState } from "react";
import type { StoreResponse } from "../../types/beacon";
import { Icon } from "../common/Icon";
import { LocalTime } from "../common/LocalTime";

interface Props {
  stores: StoreResponse[];
  selected: StoreResponse | null;
  onSelect: (id: number) => void;
  onAdd: (url: string) => void;
  onRefresh: () => void;
  refreshing: boolean;
  adding: boolean;
  onAssumptions: () => void;
}

export function TopBar({
  stores,
  selected,
  onSelect,
  onAdd,
  onRefresh,
  refreshing,
  adding,
  onAssumptions,
}: Props) {
  const [url, setUrl] = useState("");
  const [addOpen, setAddOpen] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (addOpen) inputRef.current?.focus();
  }, [addOpen]);

  const status = refreshing
    ? "busy"
    : selected?.status === "FAILED"
      ? "bad"
      : selected?.currentSnapshotId
        ? "ok"
        : "busy";

  return (
    <header className="top">
      <div className="logo">
        <div className="mark" aria-hidden="true">
          <Icon name="activity" size={16} />
        </div>
        <span>
          Beacon <span className="grad-text">Lite</span>
        </span>
      </div>
      {stores.length > 0 && (
        <div className="store-pick">
          <span className={`dot ${status}`} aria-hidden="true" />
          <label className="sr-only" htmlFor="store-switcher">
            Store
          </label>
          <select
            id="store-switcher"
            className="select"
            value={selected?.id ?? ""}
            onChange={(e) => onSelect(Number(e.target.value))}
          >
            {stores.map((s) => (
              <option key={s.id} value={s.id}>
                {s.displayName}
              </option>
            ))}
          </select>
        </div>
      )}
      {selected?.lastCheckedAt && (
        <span className="meta">
          Updated <LocalTime iso={selected.lastCheckedAt} />
          {selected.nextCheckAt && (
            <>
              {" "}
              · next <LocalTime iso={selected.nextCheckAt} />
            </>
          )}
        </span>
      )}
      <div className="spacer" />
      {selected && (
        <button
          type="button"
          className="btn ghost"
          onClick={onRefresh}
          disabled={refreshing}
          aria-label={refreshing ? "Refreshing" : "Refresh now"}
        >
          <Icon name="refresh" className={refreshing ? "spin" : ""} />
          <span className="lbl">
            {refreshing ? "Refreshing…" : "Refresh now"}
          </span>
        </button>
      )}
      <button
        type="button"
        className="btn ghost"
        onClick={onAssumptions}
        aria-label="Assumptions"
      >
        <Icon name="sliders" />
        <span className="lbl">Assumptions</span>
      </button>
      {addOpen ? (
        <form
          className="addbox"
          onSubmit={(e) => {
            e.preventDefault();
            if (url.trim()) {
              onAdd(url.trim());
              setUrl("");
              setAddOpen(false);
            }
          }}
          onKeyDown={(e) => {
            if (e.key === "Escape") setAddOpen(false);
          }}
        >
          <label className="sr-only" htmlFor="add-store">
            Store address
          </label>
          <input
            id="add-store"
            ref={inputRef}
            className="input"
            placeholder="e.g. allbirds.com"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
          />
          <button type="submit" className="btn" disabled={adding}>
            Add store
          </button>
        </form>
      ) : (
        <button
          type="button"
          className="btn"
          onClick={() => setAddOpen(true)}
          disabled={adding}
          aria-label="Add store"
        >
          <Icon name="plus" />
          <span className="lbl">Add store</span>
        </button>
      )}
    </header>
  );
}
