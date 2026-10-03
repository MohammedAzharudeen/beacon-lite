import { useState } from "react";
import type { BeaconApiError } from "../../api/beaconApi";
import type { JobResponse } from "../../types/beacon";
import { JobProgress } from "./JobProgress";

const DEMO_STORES = [
  { name: "Steve Madden", url: "www.stevemadden.com" },
  { name: "Reebok", url: "www.reebok.com" },
  { name: "Petal & Pup", url: "petalandpup.com" },
];

interface Props {
  onAdd: (url: string) => void;
  adding: boolean;
  job: JobResponse | null;
  error: BeaconApiError | null;
}

export function WelcomeAddStore({ onAdd, adding, job, error }: Props) {
  const [url, setUrl] = useState("");
  return (
    <div className="welcome">
      <div className="card wcard">
        <h2>See where any store is losing sales</h2>
        <p>
          Paste a store address. Beacon Lite reads its public catalog and pages
          (robots.txt first, one request at a time), finds sold-out best sellers
          and missing sizes, and ranks the fixes.
        </p>
        <form
          className="bigin"
          onSubmit={(e) => {
            e.preventDefault();
            if (url.trim()) onAdd(url.trim());
          }}
        >
          <label className="sr-only" htmlFor="welcome-url">
            Store address
          </label>
          <input
            id="welcome-url"
            className="input"
            placeholder="stevemadden.com"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
          />
          <button type="submit" className="btn" disabled={adding}>
            {adding ? "Checking…" : "Add store"}
          </button>
        </form>
        {error && (
          <div className="state error" role="alert">
            <b>{error.message}</b>
            {error.hint}
          </div>
        )}
        {job && <JobProgress job={job} />}
        <div className="demo">
          Or try a demo store:
          {DEMO_STORES.map((d) => (
            <button
              key={d.url}
              type="button"
              onClick={() => onAdd(d.url)}
              disabled={adding}
            >
              {d.name}
            </button>
          ))}
        </div>
        <div className="demo">Full support for standard Shopify stores.</div>
      </div>
    </div>
  );
}
