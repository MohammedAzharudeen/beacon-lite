import { useState } from "react";
import type { StoreResponse } from "../../types/beacon";
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
  return (
    <header className="top">
      <div className="logo">
        <div className="mark" aria-hidden="true">
          B
        </div>
        Beacon Lite <small>merchant intelligence</small>
      </div>
      {stores.length > 0 && (
        <>
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
        </>
      )}
      {selected && (
        <span className="meta">
          Last checked <LocalTime iso={selected.lastCheckedAt} />
          {selected.nextCheckAt && (
            <>
              {" "}
              · next <LocalTime iso={selected.nextCheckAt} />
            </>
          )}
        </span>
      )}
      {selected && (
        <button
          type="button"
          className="btn ghost"
          onClick={onRefresh}
          disabled={refreshing}
        >
          {refreshing ? "Refreshing…" : "Refresh now"}
        </button>
      )}
      <button type="button" className="btn ghost" onClick={onAssumptions}>
        Assumptions
      </button>
      <div className="spacer" />
      <form
        className="addbox"
        onSubmit={(e) => {
          e.preventDefault();
          if (url.trim()) {
            onAdd(url.trim());
            setUrl("");
          }
        }}
      >
        <label className="sr-only" htmlFor="add-store">
          Store address
        </label>
        <input
          id="add-store"
          className="input"
          placeholder="Add a store, e.g. stevemadden.com"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        <button type="submit" className="btn" disabled={adding}>
          Add store
        </button>
      </form>
    </header>
  );
}
