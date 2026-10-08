# Beacon Lite – Technical Specification

> **Source of truth for the build.** This DEVSPEC was written before any code (3 Oct 2026) and the build followed it checkpoint by checkpoint (Section 13). Sections 1–18 describe the plan as approved; what changed during the build is recorded in the Change Log (v1.5). This is the public copy: assignment logistics and links to private planning notes were removed; all technical content is unchanged.

---

## Document Information

| Field | Value |
|-------|-------|
| **Feature Name** | Beacon Lite: merchant intelligence from public storefront data |
| **Status** | Implemented |
| **Version** | 1.5 |
| **Date** | 2026-10-03 (approved) · 2026-10-09 (v1.5) |
| **Author** | Mohammed |
| **Reference** | Store data verified on the live stores, 3 Oct 2026 (Section 18.3, 18.7) |
| **Implementation Branch** | `main` in the public repo `MohammedAzharudeen/beacon-lite` (Q8) |
| **QA Document** | Section 15 of this document (no separate QA doc) |
| **Test Specification** | None: decided not to write a separate TESTSPEC. The test plan lives in this document (FR acceptance criteria, Section 9.3, Section 13 verification, Section 17). |

---

## Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.5 | 2026-10-09 | Mohammed | **Built and published.** Implemented as planned, with these changes found during the build: 19 journey checks in 6 stages (Discover 2, Browse 3, Product page 5, Size & stock 2, Cart & checkout 4, Come back 3). Demo mode replays 20 real snapshots of the three stores taken 3–8 Oct (6–7 per store, `src/main/resources/snapshots/<domain>/`), oldest first, behind a "Loading demo history" screen (`GET /api/demo/progress`); recordings are decoded one at a time and report history is streamed, so memory stays flat. A snapshot and its report are saved in one transaction; snapshots cut short by a stop are marked failed at startup and the demo load resumes from the last complete one. Dashboard refresh: gradient design tokens, icon set, briefing card, KPI deltas from trends, size strips, tooltips rendered in a portal, journey score bars. Back-test of "selling fast" run on the recordings (`docs/ACCURACY.md`): hit rate 8.5–15.7%, 1.7–7× the baseline of other partly sold-out products. Docs added: `docs/HOW_IT_WORKS.md`, `docs/USER_GUIDE.md`, `docs/ACCURACY.md`. This public copy published as `docs/DEVSPEC.md`. |
| 1.4 | 2026-10-03 | Mohammed | **Users and access decided** (Section 1, "Users and Access"): this version is a Swym-team tool that can read any public store; the planned merchant version shows a store's team only their own store (ownership via Swym Shopify app install), with anonymous benchmarks instead of named competitors. README and user guide updated. Demo store Meshki replaced by **Petal & Pup** (Meshki shows a Cloudflare bot check, now reported as `STORE_BLOCKS_AUTOMATION`, never worked around). Implementation notes: long text columns mapped as `LONG32VARCHAR` (CLOB on H2, TEXT on PostgreSQL) instead of `@Lob`; H2 `DATABASE_TO_LOWER` removed so the app restarts on an existing database; `/` always forwards to the dashboard. Accuracy evidence started in `docs/ACCURACY.md` (5 of 5 live spot-checks matched; back-test waiting for snapshots). |
| 1.3 | 2026-10-03 | Mohammed | Added `beacon-lite/docs/CODING_STANDARDS.md` (project structure, Java, TypeScript/React, tests, Git conventions, per-change definition of done) at the user's request; Section 14.2 and 17.4 reference it. Project skeleton and one-time dependency setup created (CP-0). |
| 1.2 | 2026-10-03 | Mohammed | **Approved.** All 25 pending values accepted with their proposed defaults (Section 15.2 → Decided). Scope confirmed: full support for standard Shopify storefronts (demo stores Steve Madden, Reebok, Meshki); GenericAdapter kept minimal (first in the cut order after store comparison). |
| 1.1 | 2026-10-03 | Mohammed | Store-coverage verification on 12 more stores (Section 18.7): Shopify feed capped at page 100 (25,000 products); headless Shopify stores (Gymshark 403, Fashion Nova 404) have no public feed; non-Shopify stores often lack usable structured data (ASOS offers empty, CamelBak none); `/products.json` can return HTTP 200 HTML on non-Shopify sites (ASOS); size option values include volumes, sets, bedding, combos and bra band × cup. FR-2, FR-7, Executive Summary, success criteria, edge cases and Section 15.2 (Q23–Q25) updated. Review fixes from v1.0 cross-check applied. |
| 1.0 | 2026-10-03 | Mohammed | Initial DEVSPEC consolidating all planning decisions. **Supersedes** the earlier planning notes: (1) storage changed from JSON snapshot files to **H2 embedded database** (Postgres-compatible schema, optional `postgres` profile) with raw snapshots kept as gzip files; (2) frontend changed from plain HTML/JS + Chart.js to **React + TypeScript + Vite + Recharts**; (3) added **Spring Validation, springdoc OpenAPI, Liquibase migrations, Vitest + React Testing Library**; (4) Actuator not used (not in local Maven cache for Boot 3); own `/api/health` and `/api/metrics` instead. |

---

## Executive Summary

**Purpose:** Beacon Lite is a working, small version of Swym's Beacon ("merchant intelligence layer that converts shopping intent into actionable insights") built only from data stores make public. A user pastes a store URL (full support for standard Shopify storefronts; partial or no support for headless Shopify and many non-Shopify stores, shown honestly; see Section 18.7); Beacon Lite reads the store's public catalog and pages, re-checks them every 6 hours, finds where the store is losing sales (sold-out best sellers, missing core sizes, sold-out items still promoted, catalog and journey friction), ranks fixes by estimated $ at risk, shows them on a dashboard, and answers plain-English questions through an AI chat that only uses verified data. In one line: Beacon Lite watches a store, finds where it's losing money, shows the fixes, and answers questions about it; swap the public data for Swym's intent data and it becomes Beacon.

### Key Deliverables

| Component | Description | Priority | Estimate |
|-----------|-------------|----------|----------|
| Watcher | Store adapters (Shopify, Generic, Replay), robots.txt compliance, polite fetching, snapshots every 6 h, diff engine, background jobs | P0 | Day 1 (Sat 3 Oct) |
| Insights engine | Size parsing, exclusions, restock priority + $ at risk, size gaps, promoted sold-outs, catalog quality, pricing, changes | P0 | Day 2 (Sun 4 Oct) |
| Journey scorecard | 6 stages, per-check status, coverage, robots-aware alternatives | P0 | Day 2 (Sun 4 Oct) |
| AI chat ("Ask Beacon") | `LlmProvider` (OpenAI-format, Ollama + Qwen2.5 7B), 10 tools, number validator, scope check, rule-based fallback | P0 | Day 3 (Mon 5 Oct) |
| Dashboard | React + TS + Vite single-page app matching the mockups | P0 | Day 3 (Mon 5 Oct) |
| CLI scan + Insight Brief | `java -jar beacon.jar scan <url>` → Markdown + print-ready HTML brief; committed sample for Steve Madden | P0 | Day 4 (Tue 6 Oct) |
| Store comparison | Side-by-side metrics on checks common to all compared stores | P1 | Day 3–4 |
| Packaging & delivery | Maven wrapper, prebuilt jar, docker-compose (app + Ollama, optional Postgres), GitHub Actions CI, recorded demo data | P1 | Day 4 (Tue 6 Oct) |
| Accuracy evidence | Back-test hit rate of "selling fast" flags; spot-check screenshots vs live sites | P1 | Day 4 (Tue 6 Oct) |

Cut order if time is short (decided): store comparison → GenericAdapter depth → Docker. **Never cut the core loop or the docs.**

### Success Criteria

- [ ] Adding `stevemadden.com` produces a complete report (catalog of ~2,500 products read) without manual steps, with live progress shown.
- [ ] Every store in Section 18.7 gets the expected support level and message (full, capped, partial or not supported); none crashes or shows a guessed number.
- [ ] Restock list excludes 100% of products tagged pre-order/back-order/coming soon and all non-physical types listed in Appendix 18.4.
- [ ] Every number shown in the UI, CLI brief and chat traces to a snapshot ID + assumptions version (reproducible).
- [ ] Every AI chat answer passes the number validator, or is replaced by a deterministic answer and flagged; out-of-scope questions (conversion rate, units left, sales, customers) are declined.
- [ ] Every robots.txt-disallowed path is skipped and shown as "Not checked: blocked by robots.txt"; verified against the three real robots.txt files.
- [ ] The app runs with one command (`./mvnw spring-boot:run`) and in `demo` profile with no network.
- [ ] All tests pass in GitHub Actions on every push.
- [ ] The golden question set (~30 questions) passes against the rule-based router and a mocked model.
- [ ] Back-test hit rate of "selling fast" flags is reported in the docs (whatever the value).

### Dependencies

| Dependency | Type | Status | Impact |
|------------|------|--------|--------|
| Java 21 (Temurin 21.0.4 via SDKMAN, not default) | Tooling | ✅ Installed, verified 3 Oct | Required to build/run (Spring Boot 3) |
| Ollama + `qwen2.5:7b` | Tooling | ✅ Pulled, verified 3 Oct ("Hello, nice to meet you.") | AI chat; rule-based fallback if absent |
| Maven dependencies not yet in local cache (H2 2.x, Liquibase 4.x for Boot 3, Spring Validation 3.3.3, springdoc 2.x, jsoup current, Maven jar plugin 3.4.2, frontend-maven-plugin) | External | 🟡 One-time download on the Mac (bootstrap step, CP-0) | Blocks compile/test in the build sandbox |
| npm packages (React, Vite, TypeScript, Recharts, Vitest, RTL, @fontsource/inter) | External | 🟡 One-time `npm install` on the Mac (CP-0) | Blocks frontend build; sandbox cannot reach npm registry |
| Node.js on the Mac | Tooling | 🟡 Not yet checked (Q9) | Needed for `npm install` in CP-0 (frontend-maven-plugin can also download Node) |
| Public storefront access | External | ✅ Reachable from the Mac; ❌ not from build sandbox | Live runs on the Mac; development tests use recorded real snapshots |
| GitHub repository | External | 🟡 To create: `beacon-lite`, public, branch `main` (Q8 decided) | Hosting + CI |

---

## 1. Goals & Objectives

<details open>
<summary><strong>Click to expand</strong></summary>

### Primary Goals

1. **Deliver a clear, actionable insight from public data**: headline for the hero store Steve Madden, verified 3 Oct 2026: *"771 products are missing their core sizes; three styles have only one size left."* ($ figure added after the first live run, labeled Estimate.)
2. **Identify friction across the shopper journey**: 6-stage scorecard with evidence and a fix per finding.
3. **Ship a working tool *and* script**: web app + CLI scan + committed real Insight Brief.
4. **Answer merchant questions live, safely**: AI chat that answers "Which products should I restock first?" with tool calling, verified numbers and honest refusals.
5. **Show the path to Beacon**: every demand signal labeled "public stand-in → Swym signal"; `IntentSignalProvider` interface where Swym intent data plugs in.
6. **Production-quality engineering** (engineering-first role): security (SSRF guard, prompt-injection handling), reliable fetching, data integrity, observability, tests, CI.

### Users and Access

| | Who | What they can see | How access works |
|---|---|---|---|
| **This version (assessment)** | Super admin (internal team: sales, customer success) | Any public Shopify store | Runs locally, one user, no login |
| **Merchant version (planned)** | A store's own team | Only their own store | Installing the Swym Shopify app; Shopify OAuth confirms store ownership. A merchant cannot add another store |

- **Why Swym-first:** Beacon reads only public data, so it needs no access to the store. That lets Swym research a prospect before the first conversation ("789 products are missing core sizes; no back-in-stock alerts") and lead with the finding.
- **Why merchants see only their own store:** a merchant dashboard that lists any competitor's stock problems by name invites misuse and complaints, and it is not what the merchant pays for.
- **Comparisons in the merchant version:** anonymous benchmarks ("stores like yours have 20% of sizes sold out; you have 34%"), never a named competitor's data.
- **Data upgrade in the merchant version:** Swym intent data (wishlist adds, back-in-stock sign-ups) replaces the public demand stand-ins, so $ at risk becomes measured instead of estimated (`IntentSignalProvider`).
- **Still required in every version:** robots.txt obeyed, public pages only, polite fetching, and an opt-out contact for store owners.

### Non-Goals

- Real Swym or merchant private data (only how it plugs in is designed).
- Login, multiple users, multi-tenancy, cloud hosting.
- Platform adapters beyond Shopify and Generic (the design shows how to add one).
- Anything that touches checkout or creates carts (including the "add 9,999 to cart" stock trick).
- Alerts (email/Slack); documented as production work (Section 16).
- Bypassing robots.txt; disallowed paths are never fetched.
- Exact stock quantities, sales, traffic or conversion rate (not public).

### What This Replaces

| Before (Current) | After (New) |
|------------------|-------------|
| Merchant guesses what to restock from spreadsheets | Ranked restock list with $ at risk, evidence and confidence |
| Sold-out best sellers and missing sizes go unnoticed | Size-gap grid and headline insight |
| Friction is discovered anecdotally | 6-stage journey scorecard with coverage |
| Questions need an analyst | AI chat with verified answers |

</details>

---

## 2. Functional Requirements

<details open>
<summary><strong>Click to expand</strong></summary>

### FR-1: Add a store

**Description:** The user adds a store by URL from the top-bar box or the first-launch screen. The app validates the URL, detects the platform and runs the first snapshot as a background job with visible progress.

**Requirement:**
```
POST /api/stores {url}
1. Normalise input: trim; add https:// if missing; lowercase host; strip path/query.
2. UrlGuard.check(url)  (FR-21): http/https only; resolve DNS; reject private,
   loopback, link-local, multicast; cap redirects/size.
3. If store with same domain exists → 409 STORE_ALREADY_TRACKED (return its id).
4. AdapterRegistry.detect(url):
     try ShopifyAdapter: GET /products.json?limit=1 must return HTTP 200 AND a JSON content-type AND a
       body with a "products" array (status alone is not enough: ASOS returns 200 HTML)
     if Shopify markers in home HTML but the feed is 403/404 (headless Shopify, e.g. Gymshark, Fashion Nova)
       → try GenericAdapter; if it can't read prices/availability → 422 CATALOG_FEED_UNAVAILABLE with hint
       "This Shopify store uses a custom storefront that doesn't publish its catalog"
     else GenericAdapter (sitemap + server-rendered JSON-LD Product with offers)
     else → 422 PLATFORM_NOT_SUPPORTED with hint.
5. Persist store (status = ADDING), create job ADD_STORE, return 202 {storeId, jobId}.
6. Job runs FR-4 capture; progress via GET /api/jobs/{jobId}.
```

**Acceptance Criteria:**
- [ ] `stevemadden.com`, `www.reebok.com`, `www.meshki.us` are each detected as Shopify.
- [ ] `localhost`, `127.0.0.1`, `169.254.169.254`, `10.0.0.5` and a hostname resolving to a private IP are rejected with `URL_NOT_ALLOWED`.
- [ ] Progress shows steps: Detected platform → Reading catalog (n of ~N products) → Checking store pages → Building insights.
- [ ] Adding an already-tracked domain returns 409 with the existing store id; the UI opens that store.
- [ ] A non-store URL shows a friendly message with a next step, not a stack trace.

**Example:**
```bash
curl -X POST http://127.0.0.1:8080/api/stores -H "Content-Type: application/json" -d '{"url":"stevemadden.com"}'
# 202 {"storeId":1,"jobId":"7f3c…","status":"QUEUED"}
```

---

### FR-2: Store adapters

**Description:** A `StoreAdapter` interface with three implementations; adding a platform = one new class. Auto-detection through `AdapterRegistry`.

**Requirement:**
```
interface StoreAdapter {
  Platform platform();
  boolean supports(StoreUrl url, FetchContext ctx);   // detection
  CatalogSnapshotData fetchCatalog(StoreUrl url, FetchContext ctx, ProgressListener p);
  StorefrontSignals fetchSignals(StoreUrl url, CatalogSnapshotData catalog, FetchContext ctx);
}
ShopifyAdapter reads (each only if robots.txt allows):
  /robots.txt (first)
  /products.json?limit=250&page=N   until an empty page, or page 100 (Shopify returns HTTP 400 after
     page 100, i.e. 25,000 products max; verified on Culture Kings). If the cap is hit → report
     "catalog capped at 25,000 products (public feed limit)" and label all store-wide totals as partial
     (handling of larger catalogs → 🟢 Q24)
  /collections.json?limit=250  → best-seller collections by handle match (FR-9) → /collections/<handle>/products.json
  /  (home page HTML): currency (Shopify.currency), installed apps, product links, collection links,
     footer /pages/* links, page weight, script count, free-shipping text
  sample of sold-out product pages (HTML): notify-me / wishlist presence, size guide
  sample of /products/<handle>.json: image alt text
  /search/suggest.json (only where allowed)
  /policies/refund-policy, /policies/shipping-policy, else footer pages /pages/* matching
     returns|refund|exchange|shipping|delivery
GenericAdapter: robots.txt Sitemap lines → product sitemap → SAMPLE of product URLs (size → 🟢 Q23) →
  server-rendered schema.org Product JSON-LD (name, price, availability, size where present).
  Works only where JSON-LD includes offers with price and availability in the server HTML. Verified
  limits: ASOS has Product JSON-LD but empty offers (price/stock load via JavaScript) and ~1M product URLs;
  CamelBak (Magento) has no JSON-LD at all. Where offers are missing → report "This store doesn't publish
  stock data in a readable form" and run only the checks that don't need stock (journey page checks).
  All Generic results are labelled "Sampled: N products".
ReplayAdapter: reads recorded snapshot files (demo profile, tests).
```

**Acceptance Criteria:**
- [ ] Steve Madden: 2,513 products / 24,122 variants read (as of 3 Oct 2026; counts will drift).
- [ ] Pagination stops at the first empty page.
- [ ] Each fetched URL is logged with robots decision, status and duration.
- [ ] ReplayAdapter produces identical reports from recorded files with network disabled.

**Example:**
```
[FETCH] store=stevemadden.com path=/products.json?page=3 robots=ALLOWED status=200 ms=412 bytes=1.9MB
```

---

### FR-3: robots.txt compliance

**Description:** robots.txt is read first for every store and obeyed for every request. Verified facts (3 Oct 2026): all three stores block `/collections/*sort_by*` and `/cart`; Reebok blocks `/policies/` and `/search`; Meshki blocks `/policies/` and `/search`; Steve Madden allows `/policies/` and `/search`. Reebok has **two separate `User-agent: *` groups**.

**Requirement:**
```
RobotsPolicy.parse(text):
  - collect ALL groups whose User-agent is "*" (and our UA token if listed) and merge them
  - support "*" wildcard and "$" end anchor
  - longest matching rule wins; on equal length, Allow wins
RobotsPolicy.isAllowed(path) → ALLOWED | DISALLOWED
Missing robots.txt (404) → all allowed; robots.txt fetch error (5xx/timeout) → treat as disallow-all
  for that run and report "robots.txt unavailable".
The best-selling SORT ORDER (/collections/all?sort_by=best-selling) is never used. Also never used:
  /cart, /cart.js, /recommendations/products (blocked on the stores checked).
The 5xx → disallow-all and the Allow-wins-ties rules follow RFC 9309.
Wildcard rules such as Reebok's "Disallow: /collections/*+*" must match correctly.
```

**Acceptance Criteria:**
- [ ] Unit tests on the three real robots.txt files: Reebok `/policies/refund-policy` = DISALLOWED; Meshki `/search/suggest.json` = DISALLOWED; Steve Madden `/policies/refund-policy` = ALLOWED; all three `/collections/all?sort_by=best-selling` = DISALLOWED; `/products.json` = ALLOWED.
- [ ] A disallowed check appears in the report as `NOT_CHECKED_ROBOTS` with the matching rule.
- [ ] Reebok `/collections/shoes+men` = DISALLOWED (wildcard `*+*`).

**Example:**
```
Reebok: "Disallow: /policies/" → /policies/refund-policy DISALLOWED → use /pages/returns-exchanges (allowed)
```

---

### FR-4: Polite, reliable fetching

**Description:** One HTTP client for all store traffic with rate limiting, retries and safety limits.

**Requirement:**
```
PoliteHttpClient:
  - one request at a time per host; delay between requests = beacon.fetch.delay-ms (Q1)
  - clear User-Agent: "BeaconLite/1.0 (+contact in README)"
  - connect/read timeouts (Q10 proposes values)
  - retries with exponential backoff on network errors and 5xx (max attempts configurable)
  - HTTP 429: honour Retry-After, then retry; repeated 429 → job fails with STORE_RATE_LIMITED
  - response size cap per request (configurable); larger → abort that request, log, continue
  - redirects capped; every redirect target re-checked by UrlGuard (FR-21) and RobotsPolicy
  - tolerant JSON parsing: a malformed product is logged and skipped, never crashes the run
```

**Acceptance Criteria:**
- [ ] Stub-server tests: 429 with Retry-After, 503 then 200, timeout, malformed JSON, oversize body, redirect to private IP, robots.txt-blocked path (never requested).
- [ ] No two concurrent requests to the same host.

**Example:**
```
[FETCH] store=reebok.com path=/products.json?page=2 status=429 retryAfter=5s attempt=1
```

---

### FR-5: Snapshots, scheduler and jobs

**Description:** A snapshot is a complete, dated copy of a store's catalog and signals. Taken on add, on "Refresh now", and every 6 hours.

**Requirement:**
```
Scheduler (Spring @Scheduled): every beacon.snapshot.interval (default 6h) → refresh each store.
Per-store lock: only one run per store; a second request JOINS the running job (returns same jobId).
Capture:
  1. job.step = DETECT → FETCH_CATALOG (progress n products) → FETCH_SIGNALS → BUILD_INSIGHTS
  2. Build normalised CatalogSnapshotData (schemaVersion = 1)
  3. Write raw snapshot: data/snapshots/<storeId>/<timestamp>.json.gz via temp file + atomic rename
  4. Upsert products/variants (latest state), insert change events (FR-6), insert snapshot row COMPLETE
  5. Generate InsightReport (FR-7…FR-15) → persist with snapshotId + assumptionsVersion
  6. Only on success does the new snapshot become current; on failure: snapshot FAILED with
     errorCode/message, previous snapshot stays current.
Retention: keep every raw snapshot 7 days, then one per day (oldest of each day kept).
Graceful shutdown waits for in-flight writes.
```

**Acceptance Criteria:**
- [ ] Killing the app mid-write never leaves a corrupt snapshot file (temp + rename).
- [ ] Two simultaneous refresh requests produce one run and the same jobId.
- [ ] A failed run leaves the previous report visible with a "Last refresh failed: <reason>" notice.
- [ ] Retention job keeps the right files (unit test with fake clock).
- [ ] Every snapshot file contains `schemaVersion`; reader rejects unknown versions with a clear error.

**Example:**
```
data/snapshots/1/2026-10-03T08-00-00Z.json.gz
```

---

### FR-6: Change detection (diff)

**Description:** Compare the new snapshot with the previous one. Timing comes **only** from snapshots: `updated_at` is not used for timing (verified: all 1,948 Meshki variants in the newest 250 products shared one `updated_at` hour, i.e. bulk syncs).

**Requirement:**
```
Match variants by (storeId, external variant id), products by (storeId, external product id); never by title.
Events: SOLD_OUT (available true→false), RESTOCKED (false→true), PRICE_CHANGED (price or compare-at),
        PRODUCT_ADDED, PRODUCT_REMOVED, VARIANT_ADDED, VARIANT_REMOVED
Each event stores windowStart = previous snapshot time, windowEnd = new snapshot time.
First snapshot: no events; UI shows "Available after the next check (in ~6 h)".
```

**Acceptance Criteria:**
- [ ] Renamed product with same id produces no add/remove pair.
- [ ] UI shows windows ("sold out between 08:00 and 14:00"), never an exact time.

**Example:**
```json
{"type":"SOLD_OUT","productTitle":"SLINKY30 Black","size":"12","windowStart":"2026-10-03T08:00:00Z","windowEnd":"2026-10-03T14:00:00Z"}
```

---

### FR-7: Size parsing and core sizes

**Description:** Size option detection, size-aware sorting and the core-size rule. Verified real size formats: letters (XXS…3XL, 2XL), numeric shoe (5…15, halves), wide (5.0W…12.0W), unisex dual ("M 7.5 / W 9"), kids (1, 1.5…3), waist (22–36), EU (35–41), "ONE SIZE". Feed order is not size order (Steve Madden lists `…11, 12, 10.5, 11.5, 13…`).

**Requirement:**
```
Size option = first option whose name contains "size" (case-insensitive); covers "Size", "SIZE",
  "Men's Reebok x F45 Training Coach T-Shirt (Size)".
No size option → skip size analysis for that product (e.g. 13 Reebok products such as memberships).
SizeComparator: classify each value (LETTER, NUMERIC, WIDE, DUAL, ONE_SIZE, COMBO e.g. "S/M", VOLUME e.g.
  "0.5 oz / 15 ml", SET e.g. "3 piece set", BEDDING e.g. "Full/Queen", UNKNOWN) and sort:
  LETTER by ladder XXS<XS<S<M<L<XL<XXL(=2XL)<3XL<…; NUMERIC/WIDE by number; DUAL per Q18.
Unknown kinds → product listed as "size format not recognised" (not guessed). Handling of mixed kinds
  in one product (e.g. regular + wide) and DUAL sort basis → 🟢 Q18.
Core sizes = middle 50% of the product's sorted size run (assumption, configurable). Core sizes apply only
  to fit sizes (LETTER, NUMERIC, WIDE, DUAL, COMBO); for VOLUME, SET and BEDDING the gap is the unweighted
  share sold out (🟢 Q25). Products with two size options (e.g. SKIMS "Band Size" × "Cup Size") → 🟢 Q25.
  Size option name variants seen: "Size", "SIZE", "size", "Size " (trailing space), "SIZE (VARIANT)",
  "Band Size", "Cup Size".
```

**Acceptance Criteria:**
- [ ] Steve Madden ordering bug fixed: `[11, 12, 10.5, 11.5, 13]` sorts to `[10.5, 11, 11.5, 12, 13]`.
- [ ] "2XL" and "XXL" sort equal; "ONE SIZE" products have no core-size gap.
- [ ] With the 3 Oct snapshot and default rule, Steve Madden yields 771 products missing core sizes (regression fixture).

---

### FR-8: Exclusions

**Description:** Items that only look sold out, or aren't physical stock, are excluded from restock logic and **listed separately with the reason** ("Not restock candidates").

**Requirement:**
```
PRE_ORDER: tag matches pre-order|preorder|coming soon|coming-soon|purple-dot
BACK_ORDER: tag matches back-order|backorder
NON_PHYSICAL: product_type or handle matches gift card, gift cards, gift-card, gwp, membership, sgdonation, trashie
BUNDLE: product_type "Bundle"/"Bundles"  (stock follows the items inside; shown as its own reason, as in
  mockup 02)
LIKELY_DISCONTINUED: rule 🟢 Q19 (notes give two variants: "fully sold out + not a best seller + was
  discounted" and "sold out for a long time + not a best seller, or heavily marked down before selling out")
All patterns live in config/assumptions.yml.
Pre-order variants often show available=true (Meshki: mostly purchasable) → still excluded via tags.
```

**Acceptance Criteria:**
- [ ] Steve Madden 3 Oct fixture: 284 products tagged pre-order/back-order excluded; Gift Cards, Bundle, sgdonation, TRASHIE excluded.
- [ ] Reebok: Membership and Bundles excluded; Meshki: `gwp` excluded.
- [ ] Excluded counts appear in the UI with reasons; nothing dropped silently.

---

### FR-9: Restock priority and $ at risk

**Description:** Zero stock alone is not a restock signal. Score = **Demand × Gap × Value**, after exclusions.

**Requirement:**
```
Demand (0–1), from whichever signals the store offers, in order:
  1. in the store's own best-seller collection (handle pattern 🟢 Q17;
     e.g. Steve Madden best-sellers-all-products 348, Meshki best-sellers 92; Reebok has none)
  2. linked from the home page or a featured collection
  3. sell-out speed (in stock last snapshot, sold out now; needs ≥ 2 snapshots)
  4. sold out at full price (not after heavy discounting)
  5. recently launched (published_at within N days)
  Signal weights → Q11. Each product records WHICH signals were available.
Gap (0–1) = size-weighted share of variants sold out; core sizes weigh more than edge sizes (weights → Q12).
Value = price (in store currency).
Confidence: HIGH / MEDIUM / LOW by number of demand signals available (thresholds → Q11).
$ at risk / week ≈ price × estimated weekly demand × share of demand blocked
  estimated weekly demand = demandScore × baseline units/week (Q4); labelled "Estimate", assumptions
  visible and adjustable in the Assumptions panel.
Every row labels its signals "public stand-in → Swym signal" (Appendix 18.5).
```

**Acceptance Criteria:**
- [ ] Rows show product photo, link to live product page, sizes strip, signals used, price, est. $ at risk/wk, confidence.
- [ ] Changing the baseline in the Assumptions panel re-ranks without refetching.
- [ ] No row is labelled "Best seller" unless it is in a best-seller collection (the sort order is never used).
- [ ] CSV export contains the same rows and columns as the table.

**Example:**
```
Illustrative row (signals and confidence depend on the live run):
POSSESSION Black · Women's Shoes · 1 of 17 sizes left · $79.99 · signals: […] · est. $X/wk · confidence …
```

---

### FR-10: Size gaps

**Description:** Grid of products × sizes (sorted), sold-out cells highlighted, sizes not offered shown dashed; core-size range noted.

**Acceptance Criteria:**
- [ ] Uses FR-7 sorting; columns are the union of sizes for the visible products.
- [ ] Filter by category; search by product name.

---

### FR-11: Sold-out items still promoted

**Requirement:**
```
Promoted set = product links in home page HTML ∪ products in best-seller collections ∪ featured
collections linked from the home page (how a "featured collection" is identified → 🟢 Q13). Fully sold-out (non-excluded) products in that set are reported.
If the home page yields fewer than beacon.signals.min-home-links product links (Steve Madden yields 2,
Reebok 6, Meshki 136), report "Home page links mostly load via JavaScript: checked collections only"
instead of "0 promoted sold-outs".
```

**Acceptance Criteria:**
- [ ] Steve Madden report carries the low-link notice.

---

### FR-12: Catalog quality

**Requirement:**
```
- Few images: products with < catalog.minImages images (verified 3 Oct: 14 Steve Madden products have < 3 images)
- Alt text: /products.json has NO image alt field (verified); sample N products via
  /products/<handle>.json (Q3) and report "X of N sampled have alt text on all images"
- Thin descriptions: stripped body_html length < threshold
- Category hygiene: blank product_type; case-variant duplicates ("Tops" vs "TOPS");
  spreadsheet errors (e.g. "#REF!" on Meshki)
```

**Acceptance Criteria:**
- [ ] The UI never states "all products are missing alt text" from `/products.json`.
- [ ] Meshki fixture flags `#REF!` and case-variant types.

---

### FR-13: Pricing insights

**Requirement:**
```
- Compare-at = price: variants where compare_at_price == price. Reported as
  "compare-at price equals selling price: catalog clean-up"; whether a strike-through shows depends
  on the theme (not claimed). Verified counts: Steve Madden 1,342 products, Reebok 1, Meshki 2.
- Discount depth distribution (compare_at > price); products discounted above a threshold (🟢 Q13).
- Free-shipping threshold (from home page / shipping page text, e.g. Meshki "Free Shipping On Orders
  Over $130 USD") vs median product price. If no clear amount → "Not found" (Reebok mentions free
  shipping without a parseable amount).
- Dead stock: in stock, old, discounted (thresholds 🟢 Q13).
```

---

### FR-14: Journey friction scorecard

**Description:** 6 stages scored 0–100 with evidence and a fix. Each check has a status; scores use only checks that ran and show coverage.

**Requirement:**
```
Stages and checks:
  Discover:        home page HTML size; script count; (Lighthouse page-load metrics: optional, run locally)
  Browse:          search test (only where robots allows; terms → Q5) flagging zero or irrelevant results
                   (irrelevant = no result whose title or type contains the term); catalog findability (blank or
                   inconsistent product types, thin titles, untagged products); sold-out items in
                   best-seller collections
  Product page:    image count; alt text (sampled); description depth; reviews app present; size guide
                   present (sampled sold-out product pages)
  Size & stock:    share of variants sold out; products missing core sizes
  Cart & checkout: free-shipping threshold vs median price; BNPL present (Afterpay/Klarna);
                   Shop Pay present; returns window and free returns (policy page or footer page)
  Come back:       wishlist app detected (Swym or other); "notify me" on sold-out pages; email capture
                   (Klaviyo) detected — 🟢 Q20 (new check, not in the notes)
Check status: CHECKED | CHECKED_VIA_ALTERNATIVE | NOT_CHECKED_ROBOTS | NOT_VERIFIABLE | NOT_AVAILABLE
Stage score = mean of check scores that ran (equal weights per check within a stage);
  per-check scoring thresholds → Q13. Display coverage, e.g. "Browse 74 · based on 2 of 3 checks".
Overall journey score = mean of stage scores (equal weight).
Returns window: /policies/* if allowed, else footer pages (verified: Reebok /pages/returns-exchanges
  30 days; Meshki /pages/returns 14 days; Steve Madden /policies/refund-policy 30 days).
Notify-me: detected in page HTML (Steve Madden: Swym script + "notify me" text); JS-only →
  NOT_VERIFIABLE.
```

**Acceptance Criteria:**
- [ ] Reebok and Meshki show Search test = NOT_CHECKED_ROBOTS; returns window = CHECKED_VIA_ALTERNATIVE.
- [ ] Every stage card lists its checks with status, evidence and a suggested fix.

---

### FR-15: Headline, top 5 actions, action status

**Requirement:**
```
Headline = highest-impact finding as one sentence with its number.
Top 5 actions: mix of restock items (ranked by est. $ at risk) and catalog/journey findings with a
  category badge (e.g. Catalog, Discovery), as in mockup 01; the exact mixing rule is 🟢 Q16.
  Each action has a stable actionKey.
Action status: TODO (default) | DONE | DISMISSED, saved per store; DISMISSED items stop reappearing
  in the top 5 (still visible in full lists).
```

**Acceptance Criteria:**
- [ ] Status survives restart (database).
- [ ] Clicking an action opens its evidence (products, sizes, snapshot window).

---

### FR-16: Store comparison

**Requirement:**
```
Compare 2+ tracked stores on: sizes sold out (same definition per store, 🟢 Q15), journey score, $ at risk
(Estimate), compare-at = price count, wishlist app, "notify me" on sold-out pages, pre-order items
excluded, returns window, search test.
Scores in comparison are recomputed using ONLY checks that ran on every compared store.
```

**Acceptance Criteria:**
- [ ] A check skipped on one store (robots) never changes the others' comparison score.

---

### FR-17: AI chat ("Ask Beacon")

**Description:** Tool calling over pre-computed insights. The model picks a tool and parameters; Java runs it; the model writes the answer from the JSON; the number validator checks every figure. No vector DB / RAG, no SQL agent (decided; reasons in Section 15 decision log).

**Requirement:**
```
LlmProvider (OpenAI-format /v1/chat/completions with tools): default Ollama base URL, model qwen2.5:7b;
  same code for vLLM / Groq / OpenAI / Claude via config (base URL, model, optional API key).
10 tools (read-only; storeId always injected by the server, never taken from the model):
  get_store_overview, get_restock_priorities(limit, category?), get_size_gaps(limit, category?),
  get_promoted_sold_outs, get_journey_friction(stage?), get_recent_changes(since?),
  get_pricing_insights, search_products(query?, category?, min_price?, max_price?, in_stock?, size?,
  discounted?), get_catalog_health, compare_stores(store_ids?)
Loop: max 4 tool calls per question; model call timeout (Q7) → fallback.
NumberValidator: every number in the draft must appear in tool output, with rules for currency,
  percentages, rounding (±1 in last digit), counts, dates and sizes ("size 9" is not a quantity).
  Fail → deterministic answer built from the JSON, flagged.
Scope check: questions about conversion rate, traffic, sales/revenue actuals, units left, customers,
  profit → honest refusal: "Public data can't show that. It needs your store's sales data or Swym's
  intent data." A check skipped by robots → "not checked on this store: its robots.txt blocks …".
Prompt injection: store text (titles, descriptions) passed as data only; long text truncated; tools
  read-only; validator still applies.
Rule-based fallback (Ollama off or timeout): keyword router → tool → template answer.
Response carries: answer, toolsUsed, numbersVerified count, validatorFallback, confidence
  (HIGH / ESTIMATE / NOT_AVAILABLE), provider (LLM | RULES | SCOPE — SCOPE = ScopeGuard refusal, no model
  call), snapshotId.
Answer categories: answered fully · answered with a caveat (estimates; trends such as "What's trending?"
  need a few days of snapshots) · honestly refused.
UI: suggested question chips (e.g. "What should I restock first?", "Which sizes are missing in boots?",
  "What sold out since yesterday?", "Compare me with Reebok"); "Thinking…" state with elapsed time.
Golden question set (~30 questions, expected tool + facts) in tests.
Prompts (system prompt, tool descriptions) in src/main/resources/prompts/ (submitted).
```

**Acceptance Criteria:**
- [ ] "Which products should I restock first?" → get_restock_priorities; all numbers verified.
- [ ] "What's my conversion rate?" → refusal, no number.
- [ ] Ollama stopped → same question answered by RULES provider.
- [ ] A product description containing "ignore your instructions" does not change behaviour (test).

**Example:**
```json
{"answer":"Restock these three first…","toolsUsed":["get_restock_priorities"],"numbersVerified":9,"validatorFallback":false,"confidence":"ESTIMATE","provider":"LLM","snapshotId":12}
```

---

### FR-18: Dashboard (React)

**Description:** Single-page app matching the planning mockups (not in this repo; design system: Inter font, one accent colour, consistent cards/tables/badges).

**Requirement:**
```
Layout: top bar (logo, store switcher, last checked + next check, Refresh now, Add store box) ·
  main area · right chat panel.
Tabs: Overview · Restock · Sizes · Changes · Catalog · Compare.
Overview: headline card; 4 KPI cards (sizes sold out — definition 🟢 Q15, $ at risk/week [Estimate ⓘ], journey score,
  changed since last check) with trend lines across snapshots (Recharts); top 5 actions; journey
  scorecard.
First launch: centred "See where any store is losing sales" with big URL box, step progress, and
  "Or open a demo store" chips (Steve Madden, Reebok, Meshki).
Every number has a tooltip: meaning, calculation, data date. Estimates carry an "Estimate" badge.
Product photos + links to live product pages; search boxes on restock and size tables.
Currency from the store (never assumed); "Currency unknown" when not found.
Times stored UTC, displayed in the viewer's local time zone.
Responsive: cards stack on small screens.
Accessibility: keyboard navigation, visible focus, colour never the only signal, contrast checked.
States: loading progress, empty, error with hint, "Not checked"/"Not available" with reason.
Assumptions panel: shows every value from assumptions.yml; editable values re-rank instantly.
Insight Brief: print-ready HTML page (browser "Save as PDF").
Fonts and charts bundled (no CDN) so the demo works offline.
```

**Acceptance Criteria:**
- [ ] Screens visually match the four mockups (dashboard, restock & sizes, add store, changes & compare),
      with two deliberate differences: the restock table's Confidence column shows HIGH/MEDIUM/LOW (the
      $ column carries the Estimate badge), and "Nearly sold out" is shown as a size-gap label, not a demand
      signal. Mockups will be updated to match.
- [ ] Works with network disabled in `demo` profile.
- [ ] Lighthouse-style accessibility checks pass for contrast and focus (manual check list in Section 17).

---

### FR-19: CLI scan and Insight Brief

**Requirement:**
```
java -jar beacon.jar scan <store-url> [--offline <snapshot-file>]
  runs FR-1..FR-15 once, prints a terminal summary, writes
  reports/<store>-insight-brief.md and reports/<store>-insight-brief.html (print-ready)
Brief = headline · evidence (products, sizes, dates) · what to do · expected impact (Estimate, assumptions listed)
A real generated brief for Steve Madden is committed to the repo.
```

**Example:**
```
$ java -jar beacon.jar scan stevemadden.com
Beacon Lite · scanning stevemadden.com
✓ Shopify detected · 2,513 products · 24,122 variants read
✓ Store pages checked (home, search, sold-out pages, policies)
HEADLINE  771 products are missing their core sizes; three styles have only one size left.
TOP ACTIONS
 1. Restock POSSESSION Black   1 of 17 sizes left · $79.99
 …
Excluded: 284 pre-order / back-order items
Report saved: reports/stevemadden-insight-brief.md
```

---

### FR-20: Assumptions in one place

**Requirement:**
```
config/assumptions.yml holds every judgment call (core-size rule, exclusion patterns,
discontinued rule, weekly-demand baseline, signal and size weights, scoring thresholds, samples).
assumptionsVersion = hash of the file; stored with every report.
Operational settings (snapshot interval, fetch delay, timeouts, LLM settings) live in application.yml.
GET /api/assumptions returns the effective values; dashboard Assumptions panel displays them.
```

---

### FR-21: Security controls

See Section 8 (URL guard, localhost binding, prompt injection, secrets).

---

### FR-22: Health and metrics

**Requirement:**
```
GET /api/health  → app status, each store's last run (time, status), AI model reachable
GET /api/metrics → fetch durations, fetch failures, jobs run/failed, AI latency, validator pass/fail
GET /api/llm/status → provider, model, reachable
(Spring Boot Actuator is the production upgrade; not used here because Boot 3 Actuator isn't in the
local Maven cache — decided.)
```

---

### FR-23: Run modes and demo data

**Requirement:**
```
Profiles: demo (ReplayAdapter, recorded snapshots of Steve Madden, Reebok, Meshki; no network) ·
          live (real stores) · postgres (PostgreSQL via docker-compose instead of H2)
Demo stores preloaded on first launch.
Recorded snapshots live in src/main/resources/snapshots/ and double as test fixtures.
Snapshot collection for the three stores starts on Day 1 so about a week of real changes exists by the end of the build.
```

</details>

---

## 3. Architecture & Design

<details open>
<summary><strong>Click to expand</strong></summary>

### 3.1 System Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  REACT DASHBOARD (TypeScript + Vite + Recharts) · served by Spring Boot      │
│  Overview · Restock · Sizes · Changes · Catalog · Compare · Ask Beacon chat  │
└──────────────────────────────────────────────────────────────────────────────┘
                 │ REST (JSON)                          ▲
                 ▼                                      │
┌──────────────────────────────────────────────────────────────────────────────┐
│  WEB LAYER: controllers, validation, global error handler, OpenAPI          │
│  CLI: `scan` command (same services) → Insight Brief (.md + .html)           │
└──────────────────────────────────────────────────────────────────────────────┘
        │                         │                          │
        ▼                         ▼                          ▼
┌───────────────────┐   ┌──────────────────────┐   ┌──────────────────────────┐
│ STORE & JOBS      │   │ INSIGHTS             │   │ CHAT                     │
│ StoreService      │   │ SizeParser           │   │ ChatService (tool loop)  │
│ JobService        │   │ ExclusionRules       │   │ 10 read-only tools       │
│ SnapshotScheduler │   │ RestockService       │   │ NumberValidator          │
│ per-store lock    │   │ SizeGapService       │   │ ScopeGuard               │
└───────────────────┘   │ PromotedService      │   │ RuleBasedRouter          │
        │               │ CatalogService       │   │ LlmProvider ─────────────┼──► Ollama
        ▼               │ PricingService       │   │  (OpenAI-format)         │   (qwen2.5:7b)
┌───────────────────┐   │ JourneyService       │   └──────────────────────────┘
│ WATCHER           │   │ ReportService        │
│ AdapterRegistry   │   └──────────────────────┘
│ ShopifyAdapter    │            ▲
│ GenericAdapter    │            │ reads latest state + diff events
│ ReplayAdapter     │            │
│ RobotsPolicy      │   ┌──────────────────────────────────────────────────────┐
│ UrlGuard          │──►│ PERSISTENCE                                          │
│ PoliteHttpClient  │   │ H2 (file) via JPA + Liquibase  │ raw snapshots .json.gz│
└───────────────────┘   │ (postgres profile: PostgreSQL) │ data/snapshots/…      │
        │               └──────────────────────────────────────────────────────┘
        ▼
  Public storefronts (robots.txt first; /products.json, collections, pages)

  Future (designed, not built): IntentSignalProvider ← Swym wishlist / back-in-stock events
```

**Components:**
- **Watcher:** adapters, robots.txt policy, URL guard, polite HTTP client; produces normalised snapshots.
- **Store & jobs:** store lifecycle, background jobs with progress, scheduler, per-store locking, retention.
- **Persistence:** H2 file database (Postgres-compatible schema) for structured state; gzip files for raw snapshots (audit, replay, back-test).
- **Insights:** pure, deterministic computations over the latest snapshot + change events; output an `InsightReport` stored with `snapshotId` + `assumptionsVersion`.
- **Chat:** tool calling over the report; validator, scope guard, fallback.
- **Web/CLI:** REST API for the React app; CLI for one-shot scans.

**Interfaces (decided):** `StoreAdapter`, `SnapshotStore`, `LlmProvider`, `IntentSignalProvider`.

### 3.2 Data Flow

```
1. User adds store URL (or scheduler fires every 6 h, or "Refresh now")
           ↓
2. UrlGuard → AdapterRegistry.detect → job created (202 + jobId)
           ↓
3. RobotsPolicy loaded → catalog paged from /products.json → collections → home page →
   sampled product pages / product JSON → policy or footer pages → search (if allowed)
           ↓
4. Normalised CatalogSnapshotData → raw .json.gz written atomically
           ↓
5. Upsert products/variants; DiffService writes change events (window = previous → current snapshot)
           ↓
6. ReportService runs insights + journey → InsightReport persisted (snapshotId, assumptionsVersion)
           ↓
7. Dashboard / CLI / Chat read the latest report
```

### 3.3 Component Interactions

**Ask Beacon (chat):**
```
1. POST /api/chat {storeId, message, history}
           │
           ▼
2. ScopeGuard: out-of-scope? ──Yes──► refusal (no LLM call)
           │ No
           ▼
3. LLM reachable? ──No──► RuleBasedRouter → tool → template answer (provider=RULES)
           │ Yes
           ▼
4. LlmProvider: question + 10 tool schemas  →  tool call(s)  (max 4)
           │
           ▼
5. Tool executes against latest InsightReport (storeId injected server-side) → JSON
           │
           ▼
6. LLM drafts answer from JSON
           │
           ▼
7. NumberValidator: all numbers found in tool JSON?
     ┌─────┴─────┐
    Yes          No
     │            │
     ▼            ▼
 answer +     deterministic answer from JSON, flagged
 evidence     (provider=LLM, validatorFallback=true)
```

**Add store / refresh:**
```
POST /api/stores ─► UrlGuard ─► detect ─► JobService.start (per-store lock; joins if running)
     │                                          │
     ▼                                          ▼
 202 {storeId, jobId}              steps: DETECT → FETCH_CATALOG → FETCH_SIGNALS → BUILD_INSIGHTS
                                                │
                              success ─► snapshot COMPLETE, report current
                              failure ─► snapshot FAILED (code, message); previous report stays current
```

</details>

---

## 4. Data Model

<details open>
<summary><strong>Click to expand</strong></summary>

> Storage decision: **H2 in file mode** (`./data/beacon-db`), schema written to be **PostgreSQL-compatible** (optional `postgres` profile + docker-compose). Raw snapshots stay as gzip files referenced from `snapshot.raw_path`. Types below are written for PostgreSQL; H2 runs in PostgreSQL compatibility mode.

### 4.1 Entity Relationships

```
STORE (1) ──────< SNAPSHOT (many)
  │ 1                │ 1
  │                  └────────< INSIGHT_REPORT (1 per completed snapshot)
  ├──────< PRODUCT (many) ──────< VARIANT (many)
  ├──────< CHANGE_EVENT (many)  >── PRODUCT / VARIANT (optional refs)
  ├──────< JOB (many)
  └──────< ACTION_STATUS (many)
```

### 4.2 Table: `store`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | Store id |
| `domain` | VARCHAR(255) | NOT NULL, UNIQUE | Normalised host, e.g. `stevemadden.com` |
| `display_name` | VARCHAR(255) | NOT NULL | Shown in UI |
| `platform` | VARCHAR(20) | NOT NULL, CHECK IN ('SHOPIFY','GENERIC','REPLAY') | Detected adapter |
| `currency` | VARCHAR(3) | NULL | From storefront; NULL = unknown |
| `status` | VARCHAR(20) | NOT NULL, CHECK IN ('ADDING','ACTIVE','FAILED') | Lifecycle |
| `current_snapshot_id` | BIGINT | NULL, FK snapshot(id) | Latest COMPLETE snapshot |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `uq_store_domain` (unique on `domain`).

### 4.3 Table: `snapshot`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `schema_version` | INT | NOT NULL | Raw file format version (1) |
| `status` | VARCHAR(20) | NOT NULL, CHECK IN ('RUNNING','COMPLETE','FAILED') | |
| `started_at` | TIMESTAMPTZ | NOT NULL | |
| `finished_at` | TIMESTAMPTZ | NULL | |
| `product_count` | INT | NULL | |
| `variant_count` | INT | NULL | |
| `raw_path` | VARCHAR(512) | NULL | `data/snapshots/<storeId>/<ts>.json.gz` |
| `error_code` | VARCHAR(50) | NULL | On FAILED |
| `error_message` | VARCHAR(1000) | NULL | On FAILED |

**Indexes:** `idx_snapshot_store_started` on (`store_id`, `started_at` DESC).

### 4.4 Table: `product` (latest known state)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `external_id` | BIGINT | NOT NULL | Platform product id |
| `handle` | VARCHAR(255) | NOT NULL | For product URL |
| `title` | VARCHAR(500) | NOT NULL | |
| `product_type` | VARCHAR(255) | NULL | Raw value (may be blank / `#REF!`) |
| `vendor` | VARCHAR(255) | NULL | |
| `tags` | TEXT | NULL | Pipe-separated raw tags |
| `image_url` | VARCHAR(1000) | NULL | First image `src` |
| `image_count` | INT | NOT NULL DEFAULT 0 | |
| `description_length` | INT | NOT NULL DEFAULT 0 | Stripped body length |
| `published_at` | TIMESTAMPTZ | NULL | |
| `in_best_seller_collection` | BOOLEAN | NOT NULL DEFAULT FALSE | |
| `promoted` | BOOLEAN | NOT NULL DEFAULT FALSE | Home page / featured |
| `exclusion` | VARCHAR(30) | NULL | PRE_ORDER, BACK_ORDER, NON_PHYSICAL, BUNDLE, LIKELY_DISCONTINUED |
| `removed` | BOOLEAN | NOT NULL DEFAULT FALSE | Absent from latest snapshot |
| `last_snapshot_id` | BIGINT | NOT NULL, FK snapshot(id) | |

**Constraints:** `uq_product_store_external` UNIQUE (`store_id`, `external_id`). **Indexes:** `idx_product_store_type` on (`store_id`, `product_type`).

### 4.5 Table: `variant` (latest known state)

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `product_id` | BIGINT | NOT NULL, FK product(id) | |
| `external_id` | BIGINT | NOT NULL | Platform variant id |
| `title` | VARCHAR(500) | NOT NULL | e.g. "BLACK PATENT / 7 / 018" |
| `option1` / `option2` / `option3` | VARCHAR(255) | NULL | Raw options |
| `size_label` | VARCHAR(100) | NULL | Value of the size option |
| `size_kind` | VARCHAR(20) | NULL | LETTER, NUMERIC, WIDE, DUAL, ONE_SIZE, UNKNOWN |
| `size_rank` | INT | NULL | Position in sorted size run |
| `is_core_size` | BOOLEAN | NOT NULL DEFAULT FALSE | |
| `sku` | VARCHAR(255) | NULL | |
| `price` | NUMERIC(12,2) | NOT NULL | Store currency |
| `compare_at_price` | NUMERIC(12,2) | NULL | |
| `available` | BOOLEAN | NOT NULL | Public in-stock flag |
| `removed` | BOOLEAN | NOT NULL DEFAULT FALSE | |
| `last_snapshot_id` | BIGINT | NOT NULL, FK snapshot(id) | |

**Constraints:** `uq_variant_store_external` UNIQUE (`store_id`, `external_id`). **Indexes:** `idx_variant_product` on `product_id`; `idx_variant_store_available` on (`store_id`, `available`).

### 4.6 Table: `change_event`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `from_snapshot_id` | BIGINT | NOT NULL, FK snapshot(id) | |
| `to_snapshot_id` | BIGINT | NOT NULL, FK snapshot(id) | |
| `product_id` | BIGINT | NULL, FK product(id) | |
| `variant_id` | BIGINT | NULL, FK variant(id) | |
| `type` | VARCHAR(30) | NOT NULL, CHECK IN ('SOLD_OUT','RESTOCKED','PRICE_CHANGED','PRODUCT_ADDED','PRODUCT_REMOVED','VARIANT_ADDED','VARIANT_REMOVED') | |
| `old_value` | VARCHAR(255) | NULL | e.g. old price |
| `new_value` | VARCHAR(255) | NULL | |
| `window_start` | TIMESTAMPTZ | NOT NULL | Previous snapshot time |
| `window_end` | TIMESTAMPTZ | NOT NULL | New snapshot time |

**Indexes:** `idx_change_store_window` on (`store_id`, `window_end` DESC); `idx_change_variant` on `variant_id`.

### 4.7 Table: `job`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY | jobId returned to clients |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `type` | VARCHAR(20) | NOT NULL, CHECK IN ('ADD_STORE','REFRESH','SCHEDULED') | |
| `status` | VARCHAR(20) | NOT NULL, CHECK IN ('QUEUED','RUNNING','SUCCEEDED','FAILED') | |
| `step` | VARCHAR(30) | NULL | DETECT, FETCH_CATALOG, FETCH_SIGNALS, BUILD_INSIGHTS |
| `products_read` | INT | NOT NULL DEFAULT 0 | |
| `products_estimate` | INT | NULL | |
| `error_code` | VARCHAR(50) | NULL | |
| `message` | VARCHAR(1000) | NULL | User-facing hint |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Indexes:** `idx_job_store_status` on (`store_id`, `status`).

### 4.8 Table: `action_status`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `action_key` | VARCHAR(200) | NOT NULL | Stable key, e.g. `RESTOCK:product:7307477418117` |
| `status` | VARCHAR(20) | NOT NULL, CHECK IN ('TODO','DONE','DISMISSED') | |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT NOW() | |

**Constraints:** `uq_action_store_key` UNIQUE (`store_id`, `action_key`).

### 4.9 Table: `insight_report`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | |
| `store_id` | BIGINT | NOT NULL, FK store(id) | |
| `snapshot_id` | BIGINT | NOT NULL, UNIQUE, FK snapshot(id) | |
| `assumptions_version` | VARCHAR(64) | NOT NULL | Hash of assumptions.yml |
| `generated_at` | TIMESTAMPTZ | NOT NULL | |
| `payload` | TEXT | NOT NULL | Serialised `InsightReport` JSON |

**Indexes:** `idx_report_store_generated` on (`store_id`, `generated_at` DESC).

> KPI trend lines read historical `insight_report` rows (sizes sold out %, journey score per snapshot).

### 4.10 Raw snapshot file (gzip JSON, schemaVersion 1)

```json
{
  "schemaVersion": 1,
  "store": {"domain": "stevemadden.com", "platform": "SHOPIFY", "currency": "USD"},
  "capturedAt": "2026-10-03T08:00:00Z",
  "robots": {"fetched": true, "disallowedChecks": ["SEARCH_TEST"]},
  "products": [{"externalId": 7307477418117, "handle": "geronimo-black-patent", "title": "GERONIMO BLACK PATENT",
    "productType": "Men's Shoes", "tags": ["Back-Order"], "images": [{"src": "https://cdn.shopify.com/…"}],
    "descriptionLength": 578, "publishedAt": "…",
    "options": [{"name": "Size", "values": ["7", "7.5"]}],
    "variants": [{"externalId": 41771062853765, "title": "BLACK PATENT / 7 / 018", "option1": "BLACK PATENT",
      "option2": "7", "option3": "018", "sku": "GERONIMO", "available": true, "price": "179.95", "compareAtPrice": "179.95"}]}],
  "collections": {"bestSeller": [{"handle": "best-sellers-all-products", "productExternalIds": []}]},
  "signals": {"homePage": {"bytes": 1060000, "scripts": 157, "productLinks": 2, "apps": ["SWYM","KLAVIYO"],
    "freeShippingText": null}, "policies": {"returnsDays": 30, "source": "/policies/refund-policy"},
    "search": {"status": "CHECKED", "results": {}}, "productPages": [], "altTextSample": {}}
}
```

### 4.11 Database Migrations

**Tool:** Liquibase (matches the template's changelog convention). **Files:** `src/main/resources/db/changelog/db.changelog-master.yaml` including `db.changelog-set01_initial_schema.xml`.

```sql
-- set01: initial schema (abridged; full columns per 4.2–4.9)
CREATE TABLE store (id BIGSERIAL PRIMARY KEY, domain VARCHAR(255) NOT NULL UNIQUE, …);
CREATE TABLE snapshot (id BIGSERIAL PRIMARY KEY, store_id BIGINT NOT NULL REFERENCES store(id), …);
ALTER TABLE store ADD CONSTRAINT fk_store_current_snapshot FOREIGN KEY (current_snapshot_id) REFERENCES snapshot(id);
CREATE TABLE product (…, CONSTRAINT uq_product_store_external UNIQUE (store_id, external_id));
CREATE TABLE variant (…, CONSTRAINT uq_variant_store_external UNIQUE (store_id, external_id));
CREATE TABLE change_event (…); CREATE TABLE job (…); CREATE TABLE action_status (…); CREATE TABLE insight_report (…);
CREATE INDEX idx_snapshot_store_started ON snapshot(store_id, started_at DESC);
-- + remaining indexes from 4.2–4.9
```

**Rollback Strategy:**
```sql
DROP TABLE IF EXISTS insight_report, action_status, job, change_event, variant, product;
ALTER TABLE store DROP CONSTRAINT IF EXISTS fk_store_current_snapshot;
DROP TABLE IF EXISTS snapshot, store;
```
Local prototype: deleting `./data/` resets everything (demo stores re-seed on next start).

</details>

---

## 5. API Specifications

<details open>
<summary><strong>Click to expand</strong></summary>

**Common:** base URL `http://127.0.0.1:8080`, JSON, **Authentication: None** (local single user, bound to localhost; Section 8). OpenAPI/Swagger UI at `/swagger-ui.html`. Errors use the format in Section 9.2. Monetary values are strings with `currency` alongside; times are ISO-8601 UTC.

| # | Method & Path | Purpose |
|---|---|---|
| 5.1 | `GET /api/stores` | List tracked stores |
| 5.2 | `POST /api/stores` | Add a store (async) |
| 5.3 | `GET /api/stores/{storeId}` | Store details + current snapshot |
| 5.4 | `POST /api/stores/{storeId}/snapshot` | Refresh now (async; joins running job) |
| 5.5 | `GET /api/jobs/{jobId}` | Job progress |
| 5.6 | `GET /api/stores/{storeId}/report` | Full insight report (latest) |
| 5.7 | `GET /api/stores/{storeId}/restock` | Restock rows (filter/search) |
| 5.8 | `GET /api/stores/{storeId}/restock.csv` | CSV export |
| 5.9 | `GET /api/stores/{storeId}/changes` | Change events |
| 5.10 | `GET /api/stores/{storeId}/trends` | KPI series across snapshots |
| 5.11 | `PUT /api/stores/{storeId}/actions/{actionKey}` | Set action status |
| 5.12 | `GET /api/stores/{storeId}/brief` | Print-ready Insight Brief (HTML) |
| 5.13 | `GET /api/benchmark` | Store comparison |
| 5.14 | `POST /api/chat` | Ask Beacon |
| 5.15 | `GET /api/assumptions` | Effective assumptions + version |
| 5.16 | `GET /api/llm/status` | AI provider status |
| 5.17 | `GET /api/health` | Health |
| 5.18 | `GET /api/metrics` | Metrics |

### 5.2 Endpoint: POST /api/stores

**Purpose:** Add a store and start the first snapshot. **Authentication:** None.

**Request Body / Schema:**
```json
{ "url": "stevemadden.com" }
```
```typescript
interface AddStoreRequest { url: string; }
```
```java
public record AddStoreRequest(@NotBlank @Size(max = 2048) String url) {}
```

**Response (202 Accepted):**
```json
{ "storeId": 1, "jobId": "7f3c2b8e-…", "status": "QUEUED" }
```

**Error Responses:**
| Status | Code | Message | Cause |
|--------|------|---------|-------|
| 400 | INVALID_URL | "That doesn't look like a web address" | Unparseable / non-http(s) |
| 400 | URL_NOT_ALLOWED | "This address can't be scanned" | Private/loopback/link-local/multicast target |
| 409 | STORE_ALREADY_TRACKED | "This store is already tracked" | Same domain (body includes `storeId`) |
| 422 | PLATFORM_NOT_SUPPORTED | "We couldn't read a product catalog from this store" | Neither Shopify nor Generic detected |
| 502 | STORE_NOT_REACHABLE | "The store didn't respond" | DNS/connection failure |

**Example Request:**
```bash
curl -X POST http://127.0.0.1:8080/api/stores -H "Content-Type: application/json" -d '{"url":"www.meshki.us"}'
```

### 5.4 Endpoint: POST /api/stores/{storeId}/snapshot
**Response (202):** `{ "jobId": "…", "joinedExisting": false }` · **404** NOT_FOUND.

### 5.5 Endpoint: GET /api/jobs/{jobId}
```typescript
interface JobResponse {
  jobId: string; storeId: number; type: 'ADD_STORE' | 'REFRESH' | 'SCHEDULED';
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
  step: 'DETECT' | 'FETCH_CATALOG' | 'FETCH_SIGNALS' | 'BUILD_INSIGHTS' | null;
  productsRead: number; productsEstimate: number | null;
  error?: { code: string; message: string; hint: string };
}
```

### 5.6 Endpoint: GET /api/stores/{storeId}/report
```typescript
interface InsightReport {
  storeId: number; snapshotId: number; assumptionsVersion: string; generatedAt: string; currency: string | null;
  catalog: { products: number; variants: number };
  headline: { text: string; metric: number; evidenceRef: string };
  kpis: { sizesSoldOutPct: number; soldOutVariants: number; atRiskPerWeek: Money & { estimate: true };
          journeyScore: number; changedSinceLastCheck: { soldOut: number; restocked: number; priceChanges: number } | null };
  topActions: Action[];            // max 5, DISMISSED excluded
  restock: RestockRow[];           // full list
  exclusions: { reason: ExclusionReason; count: number; description: string }[];
  sizeGaps: SizeGapRow[];
  promotedSoldOuts: { products: ProductRef[]; notice?: string };
  catalogQuality: CatalogQuality; pricing: PricingInsights;
  journey: JourneyStage[];
  dataNotes: string[];             // e.g. "updated_at not used for timing"
}
interface Money { amount: string; currency: string | null; }
interface Action { actionKey: string; rank: number; title: string; evidence: string; category: string;
                   atRiskPerWeek?: Money; status: 'TODO' | 'DONE' | 'DISMISSED'; }
interface RestockRow { productId: number; title: string; productType: string | null; imageUrl: string | null;
  productUrl: string; sizes: { label: string; available: boolean; core: boolean }[]; soldOutSizes: number;
  totalSizes: number; price: Money; signals: DemandSignal[]; demandScore: number; gapScore: number;
  atRiskPerWeek: Money; confidence: 'HIGH' | 'MEDIUM' | 'LOW'; swymSignalLabel: string; }
type DemandSignal = 'BEST_SELLER_COLLECTION' | 'PROMOTED' | 'SELL_OUT_SPEED' | 'FULL_PRICE' | 'RECENTLY_LAUNCHED';
type ExclusionReason = 'PRE_ORDER' | 'BACK_ORDER' | 'NON_PHYSICAL' | 'BUNDLE' | 'LIKELY_DISCONTINUED';
interface JourneyStage { stage: 'DISCOVER' | 'BROWSE' | 'PRODUCT_PAGE' | 'SIZE_STOCK' | 'CART_CHECKOUT' | 'COME_BACK';
  score: number | null; checksRun: number; checksTotal: number; checks: JourneyCheck[]; }
interface JourneyCheck { key: string; label: string;
  status: 'CHECKED' | 'CHECKED_VIA_ALTERNATIVE' | 'NOT_CHECKED_ROBOTS' | 'NOT_VERIFIABLE' | 'NOT_AVAILABLE';
  score: number | null; evidence: string; fix: string | null; reason?: string; }
```
**Errors:** 404 NOT_FOUND (store) · 409 REPORT_NOT_READY (first job still running; body includes `jobId`).

### 5.7 Endpoint: GET /api/stores/{storeId}/restock
**Query:** `category` (optional), `q` (optional, product name search), `limit` (optional, default 50, max 500), `offset` (optional).
**Response:** `{ "rows": RestockRow[], "total": number, "currency": "USD" }`.

### 5.8 Endpoint: GET /api/stores/{storeId}/restock.csv
`text/csv`; same columns as the table: rank, product, type, product_url, sold_out_sizes, total_sizes, sold_out_size_list, price, currency, signals, demand_score, gap_score, est_at_risk_per_week, confidence, snapshot_id, assumptions_version.

### 5.9 Endpoint: GET /api/stores/{storeId}/changes
**Query:** `type` (optional), `limit` (default 100). **Response:** `{ "events": ChangeEvent[], "windows": {start,end}[] }`.

### 5.10 Endpoint: GET /api/stores/{storeId}/trends
**Response:** `{ "points": [{ "snapshotId": 12, "at": "…", "sizesSoldOutPct": 33.7, "journeyScore": 58 }] }`.

### 5.11 Endpoint: PUT /api/stores/{storeId}/actions/{actionKey}
```java
public record ActionStatusRequest(@NotNull @Pattern(regexp = "TODO|DONE|DISMISSED") String status) {}
```
**Response (200):** `{ "actionKey": "…", "status": "DONE", "updatedAt": "…" }` · 400 INVALID_INPUT · 404 NOT_FOUND.

### 5.12 Endpoint: GET /api/stores/{storeId}/brief
`text/html`, print-ready Insight Brief (headline, evidence, actions, impact, assumptions, snapshot id).

### 5.13 Endpoint: GET /api/benchmark
**Query:** `storeIds` (optional, comma-separated; default all). **Response:** metrics per store + `commonChecks` list used for scores.

### 5.14 Endpoint: POST /api/chat
```java
public record ChatRequest(@NotNull Long storeId,
                          @NotBlank @Size(max = 1000) String message,
                          @Size(max = 20) List<ChatTurn> history) {}
public record ChatTurn(@Pattern(regexp = "user|assistant") String role, @Size(max = 4000) String content) {}
```
**Response (200):**
```json
{ "answer": "…", "toolsUsed": ["get_restock_priorities"], "numbersVerified": 9,
  "validatorFallback": false, "confidence": "ESTIMATE", "provider": "LLM", "snapshotId": 12 }
// provider: "LLM" | "RULES" | "SCOPE"
```
**Errors:** 400 INVALID_INPUT · 404 NOT_FOUND · 409 REPORT_NOT_READY. (LLM unavailable is **not** an error: RULES provider answers.)

### 5.15–5.18
- `GET /api/assumptions` → `{ "version": "sha256…", "values": { … } }`
- `GET /api/llm/status` → `{ "provider": "ollama", "baseUrl": "http://localhost:11434/v1", "model": "qwen2.5:7b", "reachable": true }`
- `GET /api/health` → `{ "status": "UP", "stores": [{ "storeId": 1, "lastRun": "…", "lastStatus": "SUCCEEDED" }], "llmReachable": true }`
- `GET /api/metrics` → counters and timings listed in Section 10.2

</details>

---

## 6. Backend Implementation

<details open>
<summary><strong>Click to expand</strong></summary>

**Base package:** `com.beacon` (🟢 Q6). Java 21 records for DTOs (no Lombok, decided). One class per responsibility; comments explain *why*.

```
src/main/java/com/beacon/
├── BeaconApplication.java        @SpringBootApplication @EnableScheduling
├── config/                       BeaconProperties, AssumptionsLoader, OpenApiConfig, AsyncConfig
├── security/                     UrlGuard
├── fetch/                        PoliteHttpClient, FetchContext, FetchResult, RetryPolicy
├── robots/                       RobotsPolicy, RobotsParser, RobotsDecision
├── adapter/                      StoreAdapter, AdapterRegistry, ShopifyAdapter, GenericAdapter, ReplayAdapter,
│                                 model/ (CatalogSnapshotData, ProductData, VariantData, StorefrontSignals)
├── store/                        Store (entity), StoreRepository, StoreService
├── snapshot/                     Snapshot (entity), SnapshotRepository, SnapshotService, SnapshotStore,
│                                 GzipFileSnapshotStore, SnapshotScheduler, RetentionService
├── catalog/                      Product, Variant (entities), ProductRepository, VariantRepository, CatalogUpsertService
├── diff/                         ChangeEvent (entity), ChangeEventRepository, DiffService
├── job/                          Job (entity), JobRepository, JobService, StoreLockRegistry
├── size/                         SizeParser, SizeComparator, SizeKind, CoreSizeRule
├── insight/                      ExclusionRules, DemandModel, RestockService, SizeGapService, PromotedService,
│                                 CatalogQualityService, PricingService, HeadlineService, ReportService,
│                                 InsightReport (+ nested records), InsightReportEntity, InsightReportRepository
├── journey/                      JourneyService, JourneyCheck, checks/ (one class per check)
├── action/                       ActionStatus (entity), ActionStatusRepository, ActionService
├── intent/                       IntentSignalProvider (interface), PublicStandInProvider
├── chat/                         ChatService, LlmProvider, OpenAiCompatibleProvider, ToolRegistry,
│                                 tools/ (10 tools), NumberValidator, ScopeGuard, RuleBasedRouter, PromptLoader
├── web/                          StoreController, JobController, ReportController, ChatController,
│                                 BenchmarkController, SystemController, GlobalExceptionHandler, dto/
├── cli/                          ScanCommand, InsightBriefWriter
└── common/                       BeaconException, ErrorCode, Clock config, Money
```

### 6.1 DTOs & Request/Response Models

```java
// web/dto
public record AddStoreRequest(
    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL is too long")
    String url) {}

public record AddStoreResponse(Long storeId, UUID jobId, String status) {}

public record StoreResponse(Long id, String domain, String displayName, String platform, String currency,
                            String status, Long currentSnapshotId, Instant lastCheckedAt, Instant nextCheckAt) {}

public record JobResponse(UUID jobId, Long storeId, String type, String status, String step,
                          int productsRead, Integer productsEstimate, ErrorBody error) {}

public record ActionStatusRequest(
    @NotNull @Pattern(regexp = "TODO|DONE|DISMISSED", message = "Invalid status") String status) {}

public record ChatRequest(
    @NotNull(message = "storeId is required") Long storeId,
    @NotBlank(message = "Message is required") @Size(max = 1000) String message,
    @Size(max = 20) List<@Valid ChatTurn> history) {}

public record ChatTurn(@Pattern(regexp = "user|assistant") String role, @Size(max = 4000) String content) {}

public record ChatResponse(String answer, List<String> toolsUsed, int numbersVerified, boolean validatorFallback,
                           String confidence, String provider, Long snapshotId) {}

public record ErrorBody(Instant timestamp, int status, String code, String message, String hint, String path) {}
```
`InsightReport` and nested records mirror the TypeScript shapes in Section 5.6 exactly.

---

### 6.2 Service Layer (key services, pseudocode)

**`security/UrlGuard.java`**: blocks SSRF.
```java
/** @throws BeaconException URL_NOT_ALLOWED / INVALID_URL */
public StoreUrl check(String raw) {
    // 1. normalise (add https://, lowercase host, drop path/query); scheme must be http/https
    // 2. resolve all A/AAAA records; reject if ANY is loopback, site-local (10/8, 172.16/12, 192.168/16),
    //    link-local (169.254/16, fe80::/10), multicast, any-local, or unique-local (fc00::/7)
    // 3. return StoreUrl(host, baseUri)
    // Redirects: PoliteHttpClient calls check() again on every Location header (max 5 redirects)
}
```

**`robots/RobotsPolicy.java`**
```java
public static RobotsPolicy parse(String text, String userAgentToken) {
    // 1. split into groups; a group = consecutive User-agent lines + following rules
    // 2. keep rules from ALL groups matching "*" or our token (Reebok has two "*" groups)
    // 3. compile each Allow/Disallow pattern: "*" → any chars, trailing "$" → end anchor
}
public RobotsDecision decide(String pathAndQuery) {
    // longest matching pattern wins; tie → Allow; no match → ALLOWED; returns matched rule for evidence
}
```

**`fetch/PoliteHttpClient.java`**
```java
public FetchResult get(StoreUrl store, String pathAndQuery, FetchPurpose purpose) {
    log.info("[FETCH] store={} path={} purpose={}", store.host(), pathAndQuery, purpose);
    RobotsDecision d = robotsFor(store).decide(pathAndQuery);
    if (d.disallowed()) return FetchResult.blockedByRobots(d.rule());     // never sent
    hostLimiter.acquire(store.host());                                     // one in flight + delay (Q1)
    for (attempt = 1..maxAttempts) {
        response = http.send(request with UA, timeouts (Q10));
        if (429) { sleep(retryAfterOrBackoff(attempt)); continue; }
        if (5xx || IOException) { sleep(backoff(attempt)); continue; }
        if (redirect) { urlGuard.check(location); robots check; follow (max 5) }
        enforce size cap while reading body; return FetchResult.ok(status, body, durationMs);
    }
    throw new BeaconException(ErrorCode.STORE_NOT_REACHABLE or STORE_RATE_LIMITED);
}
```

**`store/StoreService.java`**
```java
public AddStoreResponse addStore(AddStoreRequest req) {
    log.info("[ADD_STORE] url={}", req.url());
    StoreUrl url = urlGuard.check(req.url());
    storeRepository.findByDomain(url.host()).ifPresent(s -> { throw BeaconException.conflict(STORE_ALREADY_TRACKED, s.getId()); });
    StoreAdapter adapter = adapterRegistry.detect(url);                    // 422 if none
    Store store = storeRepository.save(Store.adding(url, adapter.platform()));
    Job job = jobService.start(store.getId(), JobType.ADD_STORE);          // async
    return new AddStoreResponse(store.getId(), job.getId(), job.getStatus().name());
}
```

**`job/JobService.java`**
```java
public Job start(Long storeId, JobType type) {
    // StoreLockRegistry: if a job is RUNNING/QUEUED for storeId → return it (joinedExisting=true)
    // else persist job QUEUED and submit snapshotService.capture(storeId, jobId) to the executor
}
```

**`snapshot/SnapshotService.java`**
```java
@Transactional(propagation = NOT_SUPPORTED)   // long-running; short transactions inside
public void capture(Long storeId, UUID jobId) {
    log.info("[CAPTURE] store={} job={} start", storeId, jobId);
    Snapshot snap = snapshotRepository.save(Snapshot.running(storeId, SCHEMA_VERSION));
    try {
        job(jobId).step(DETECT);
        StoreAdapter adapter = adapterRegistry.forStore(store);
        job(jobId).step(FETCH_CATALOG);
        CatalogSnapshotData data = adapter.fetchCatalog(url, ctx, n -> job(jobId).progress(n));
        job(jobId).step(FETCH_SIGNALS);
        data = data.withSignals(adapter.fetchSignals(url, data, ctx));
        String path = snapshotStore.writeAtomic(storeId, data);            // temp file + rename, gzip
        txTemplate.execute(s -> {
            catalogUpsertService.upsert(storeId, snap.getId(), data);
            diffService.recordChanges(storeId, previousSnapshotId, snap.getId(), data);
            snap.complete(path, data.productCount(), data.variantCount());
            store.setCurrentSnapshotId(snap.getId()); store.setCurrency(data.currency()); store.setStatus(ACTIVE);
        });
        job(jobId).step(BUILD_INSIGHTS);
        reportService.generate(storeId, snap.getId());
        job(jobId).succeed();
        log.info("[CAPTURE] store={} job={} done products={} ms={}", …);
    } catch (BeaconException e) {
        snap.fail(e.code(), e.getMessage()); job(jobId).fail(e.code(), e.hint());
        log.warn("[CAPTURE] store={} job={} failed code={}", storeId, jobId, e.code());
    } catch (Exception e) {
        snap.fail(INTERNAL_ERROR, "Unexpected error"); job(jobId).fail(INTERNAL_ERROR, "Try again; see logs");
        log.error("[CAPTURE] store={} job={} unexpected", storeId, jobId, e);
    }   // previous current snapshot untouched on failure
}
```

**`diff/DiffService.java`**: match by external ids; emit events with `window_start/window_end` (FR-6). No events for the first snapshot.

**`size/SizeParser.java` + `SizeComparator.java`**: FR-7 rules; returns `SizeRun(kind, sortedLabels, coreLabels)` or `UNRECOGNISED`.

**`insight/RestockService.java`**
```java
public List<RestockRow> rank(StoreView view, Assumptions a) {
    // for each product not excluded (ExclusionRules) with ≥1 sold-out sized variant:
    //   signals   = DemandModel.signals(product, view)        // which of the 5 signals are available
    //   demand    = DemandModel.score(signals, a.signalWeights())          (Q11)
    //   gap       = Σ weight(size) * soldOut(size) / Σ weight(size)        (core vs edge weights, Q12)
    //   atRisk    = price * demand * a.weeklyDemandBaseline() * gap        (Q4) → Money(estimate=true)
    //   confidence= DemandModel.confidence(signals)                        (Q11 thresholds)
    //   score     = demand * gap * price
    // sort by atRisk desc; attach swymSignalLabel per signal (Appendix 18.5)
}
```

**`journey/JourneyService.java`**: runs each `JourneyCheck` (FR-14); each returns `CheckResult(status, score|null, evidence, fix, reason)`; stage score = mean of non-null scores; overall = mean of stage scores.

**`insight/ReportService.java`**: assembles `InsightReport` (headline, KPIs, top 5 actions excluding DISMISSED, restock, exclusions, size gaps, promoted, catalog, pricing, journey, dataNotes) and persists it with `assumptionsVersion`.

**`chat/ChatService.java`**
```java
public ChatResponse ask(ChatRequest req) {
    InsightReport report = reportService.latest(req.storeId());           // 409 if none
    if (scopeGuard.isOutOfScope(req.message())) return ChatResponse.refusal(scopeGuard.reason(req.message()));
    if (!llm.isReachable()) return ruleBasedRouter.answer(req.message(), report);   // provider=RULES
    try {
        List<ToolResult> results = new ArrayList<>();
        Conversation c = Conversation.of(prompts.system(), req.history(), req.message());
        for (int i = 0; i < MAX_TOOL_CALLS /*4*/; i++) {
            LlmReply r = llm.complete(c, toolRegistry.schemas(), timeout /*Q7*/);
            if (!r.hasToolCall()) {
                Validation v = numberValidator.check(r.text(), results);
                return v.ok() ? ChatResponse.llm(r.text(), results, v.count())
                              : ChatResponse.deterministic(templateFrom(results), results, true);
            }
            ToolResult tr = toolRegistry.run(r.toolCall(), req.storeId());  // storeId injected, args validated
            results.add(tr); c.addToolResult(tr.asDataMessage());          // store text = data, truncated
        }
        return ChatResponse.deterministic(templateFrom(results), results, true);
    } catch (LlmTimeoutException | LlmUnavailableException e) {
        log.warn("[CHAT] store={} llm fallback reason={}", req.storeId(), e.getClass().getSimpleName());
        return ruleBasedRouter.answer(req.message(), report);
    }
}
```

**`chat/NumberValidator.java`**: extract numbers with type (currency, percent, count, date, size label); each must match a value in tool JSON (currency/percent/count within ±1 in last displayed digit; dates exact; size labels excluded from quantity checks).

**Error Handling Pattern:**
```java
try { … }
catch (BeaconException e) { log.warn("[OP] store={} code={}", storeId, e.code()); throw e; }
catch (Exception e) { log.error("[OP] store={} unexpected", storeId, e); throw new BeaconException(INTERNAL_ERROR, "Something went wrong", "Try again; details are in the logs", e); }
```

---

### 6.3 Repository Layer (Spring Data JPA)

```java
public interface StoreRepository extends JpaRepository<Store, Long> { Optional<Store> findByDomain(String domain); }
public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    Optional<Snapshot> findTopByStoreIdAndStatusOrderByStartedAtDesc(Long storeId, SnapshotStatus status);
    List<Snapshot> findByStoreIdAndStatusOrderByStartedAtAsc(Long storeId, SnapshotStatus status); }   // retention, trends
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByStoreIdAndExternalId(Long storeId, Long externalId);
    List<Product> findByStoreIdAndRemovedFalse(Long storeId); }
public interface VariantRepository extends JpaRepository<Variant, Long> {
    Optional<Variant> findByStoreIdAndExternalId(Long storeId, Long externalId);
    List<Variant> findByProductIdIn(Collection<Long> productIds); }
public interface ChangeEventRepository extends JpaRepository<ChangeEvent, Long> {
    List<ChangeEvent> findByStoreIdOrderByWindowEndDesc(Long storeId, Pageable p); }
public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findFirstByStoreIdAndStatusIn(Long storeId, Collection<JobStatus> statuses); }
public interface ActionStatusRepository extends JpaRepository<ActionStatus, Long> {
    List<ActionStatus> findByStoreId(Long storeId);
    Optional<ActionStatus> findByStoreIdAndActionKey(Long storeId, String actionKey); }
public interface InsightReportRepository extends JpaRepository<InsightReportEntity, Long> {
    Optional<InsightReportEntity> findTopByStoreIdOrderByGeneratedAtDesc(Long storeId);
    List<InsightReportEntity> findByStoreIdOrderByGeneratedAtAsc(Long storeId); }
```

---

### 6.4 Controller Layer

| Controller | Endpoints |
|---|---|
| `StoreController` | 5.1–5.4 |
| `JobController` | 5.5 |
| `ReportController` | 5.6–5.12 |
| `BenchmarkController` | 5.13 |
| `ChatController` | 5.14 |
| `SystemController` | 5.15–5.18 |

Pattern: `@Valid` request records → service → response record; log `[OPERATION] store=… ` at entry. No authorization annotations (local single user, Section 8).

---

### 6.5 Model/Entity Layer

JPA entities mirror Section 4 one-to-one (`Store`, `Snapshot`, `Product`, `Variant`, `ChangeEvent`, `Job`, `ActionStatus`, `InsightReportEntity`). Enums stored as strings (`@Enumerated(EnumType.STRING)`). `spring.jpa.hibernate.ddl-auto=validate` (Liquibase owns the schema).

---

### 6.6 Configuration

```yaml
# application.yml
server:
  address: 127.0.0.1          # localhost only (decided); override to expose
  port: 8080
spring:
  datasource:
    url: jdbc:h2:file:./data/beacon-db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE
  jpa.hibernate.ddl-auto: validate
  liquibase.change-log: classpath:db/changelog/db.changelog-master.yaml
beacon:
  snapshot:
    interval: PT6H              # decided
    dir: ./data/snapshots
    retention: { keep-all-days: 7, then: one-per-day }   # decided
  fetch:
    user-agent: "BeaconLite/1.0 (+contact in README)"
    delay-ms: 500               # 🟢 Q1
    connect-timeout: PT10S      # 🟢 Q10
    read-timeout: PT30S         # 🟢 Q10
    max-attempts: 3             # 🟢 Q10
    max-response-bytes: 20971520  # 🟢 Q10 (20 MB)
    max-redirects: 5
  llm:
    base-url: http://localhost:11434/v1   # Ollama OpenAI-compatible endpoint
    model: qwen2.5:7b                     # decided
    api-key: ${BEACON_LLM_API_KEY:}       # empty for Ollama
    timeout: PT60S                        # 🟢 Q7
    max-tool-calls: 4                     # decided
  assumptions-file: ./config/assumptions.yml
---
spring.config.activate.on-profile: demo        # ReplayAdapter, no network
---
spring.config.activate.on-profile: postgres
spring.datasource.url: jdbc:postgresql://localhost:5432/beacon
```

```yaml
# config/assumptions.yml  (every judgment call; hash = assumptionsVersion)
coreSizes: { rule: middle-percent, percent: 50 }                       # decided
exclusions:
  preOrderTags: [pre-order, preorder, coming soon, coming-soon, purple-dot]   # decided (observed)
  backOrderTags: [back-order, backorder]
  nonPhysicalTypes: [gift card, gift cards, gift-card, gwp, membership, sgdonation, trashie]
  bundleTypes: [bundle, bundles]
  likelyDiscontinued: { fullySoldOut: true, notInBestSellerCollection: true, wasDiscounted: true }   # decided
bestSellerCollectionPattern: "best-seller|bestseller|best-of|top-sell"   # 🟢 Q17
demand:
  weeklyDemandBaselineUnits: 20            # 🟢 Q4
  signalWeights: { bestSellerCollection: 0.35, promoted: 0.15, sellOutSpeed: 0.30, fullPrice: 0.10, recentlyLaunched: 0.10 }   # 🟢 Q11
  recentlyLaunchedDays: 30                 # 🟢 Q11
  confidence: { high: 3, medium: 2 }       # 🟢 Q11 (signals available)
gapWeights: { core: 2.0, edge: 1.0 }       # 🟢 Q12
samples: { soldOutProductPages: 5, altTextProducts: 30 }   # 🟢 Q2, Q3
search: { termSource: top-product-types, termCount: 5 }    # 🟢 Q5
journeyThresholds: { … }                   # 🟢 Q13 (per-check scoring bands)
catalog: { minImages: 3, thinDescriptionChars: 100, thinTitleChars: 15 }   # 🟢 Q13
signals: { minHomeLinks: 10 }              # 🟢 Q13
```

Environment variables: `BEACON_LLM_API_KEY` (optional; not needed for Ollama). `.env` is git-ignored; `.env.example` committed.

</details>

---

## 7. Frontend Implementation

<details open>
<summary><strong>Click to expand</strong></summary>

**Stack (decided):** React + TypeScript + Vite; charts with **Recharts** (MIT); font **Inter** bundled via `@fontsource/inter` (SIL OFL); tests with **Vitest + React Testing Library**. Built by Maven (frontend-maven-plugin) into `src/main/resources/static/`, so the app still starts with one command. Design follows `SWYM/spec/mockups/`.

```
frontend/
├── package.json · vite.config.ts · tsconfig.json · index.html
└── src/
    ├── main.tsx · App.tsx · styles/tokens.css (colours, spacing, type from the mockups)
    ├── types/beacon.ts            // mirrors Section 5 schemas exactly
    ├── api/beaconApi.ts           // fetch wrapper; parses ErrorBody into ApiError
    ├── hooks/                     useStores, useJob (polls /api/jobs/{id}), useReport, useRestock,
    │                              useChanges, useTrends, useBenchmark, useChat, useAssumptions
    ├── components/
    │   ├── layout/                TopBar, StoreSwitcher, AddStoreBox, Tabs, ChatPanel
    │   ├── onboarding/            WelcomeAddStore (big URL box, step progress, demo-store chips)
    │   ├── overview/              HeadlineCard, KpiCard (+ trend line), TopActions, ActionStatusMenu
    │   ├── journey/               JourneyScorecard, StageCard, CheckList (status pills + coverage)
    │   ├── restock/               RestockTable (photo, link, size strip, signals, Estimate badge, CSV),
    │   │                          NotRestockCandidates
    │   ├── sizes/                 SizeGapHeatmap
    │   ├── changes/               ChangesTimeline (time windows, not exact times)
    │   ├── catalog/               CatalogQuality
    │   ├── compare/               CompareTable (common checks only)
    │   ├── assumptions/           AssumptionsPanel
    │   └── common/                Money (store currency or "Currency unknown"), LocalTime, Tooltip,
    │                              EstimateBadge, StatusPill, EmptyState, ErrorState, Skeleton
    └── __tests__/                 component tests (Vitest + RTL)
```

### 7.1 Types & Interfaces
`src/types/beacon.ts` contains exactly the interfaces in Section 5 (`InsightReport`, `RestockRow`, `JourneyStage`, `JourneyCheck`, `JobResponse`, `ChatResponse`, `Money`, …) plus:
```typescript
export interface ApiError { status: number; code: string; message: string; hint?: string; }
export type ActionStatus = 'TODO' | 'DONE' | 'DISMISSED';
```

### 7.2 API Client
```typescript
export const beaconApi = {
  listStores: () => get<StoreResponse[]>('/api/stores'),
  addStore: (url: string) => post<AddStoreResponse>('/api/stores', { url }),
  refresh: (storeId: number) => post<{ jobId: string; joinedExisting: boolean }>(`/api/stores/${storeId}/snapshot`),
  job: (jobId: string) => get<JobResponse>(`/api/jobs/${jobId}`),
  report: (storeId: number) => get<InsightReport>(`/api/stores/${storeId}/report`),
  restock: (storeId: number, p: { category?: string; q?: string; limit?: number; offset?: number }) =>
    get<{ rows: RestockRow[]; total: number; currency: string | null }>(`/api/stores/${storeId}/restock`, p),
  restockCsvUrl: (storeId: number) => `/api/stores/${storeId}/restock.csv`,
  changes: (storeId: number) => get<ChangesResponse>(`/api/stores/${storeId}/changes`),
  trends: (storeId: number) => get<TrendsResponse>(`/api/stores/${storeId}/trends`),
  setActionStatus: (storeId: number, key: string, status: ActionStatus) =>
    put(`/api/stores/${storeId}/actions/${encodeURIComponent(key)}`, { status }),
  briefUrl: (storeId: number) => `/api/stores/${storeId}/brief`,
  benchmark: (ids?: number[]) => get<BenchmarkResponse>('/api/benchmark', ids ? { storeIds: ids.join(',') } : {}),
  chat: (req: ChatRequest) => post<ChatResponse>('/api/chat', req),
  assumptions: () => get<AssumptionsResponse>('/api/assumptions'),
  llmStatus: () => get<LlmStatus>('/api/llm/status'),
};
```

### 7.3 Hooks
Each hook returns `{ data, loading, error, reload }`. `useJob(jobId)` polls every 1 s until `SUCCEEDED`/`FAILED`, then triggers `useReport` reload. Errors surface the server `hint`.

### 7.4 Components (behaviour rules)
- **Numbers:** every KPI/value has a `Tooltip` (meaning, calculation, data date); estimates wrapped in `EstimateBadge`.
- **Currency:** `Money` renders with the store currency; `null` → "Currency unknown".
- **Times:** `LocalTime` renders UTC in the viewer's local time; change events render windows ("08:00 → 14:00").
- **Not-checked states:** `StatusPill` shows Checked / Checked via alternative page / Not checked: blocked by robots.txt / Not verifiable / Not available, with the reason on hover.
- **Security:** never `dangerouslySetInnerHTML`; store text rendered as plain text.
- **Responsive:** CSS grid collapses to one column under the mobile breakpoint.
- **Accessibility:** keyboard navigable tabs/menus, visible focus, icons + text for every status colour.

**Usage Example:**
```tsx
<KpiCard label="Sizes sold out" value={report.kpis.sizesSoldOutPct} unit="%"
         tooltip="Share of size/colour variants marked unavailable in the latest snapshot"
         trend={trends.points.map(p => p.sizesSoldOutPct)} />
```

</details>

---

## 8. Security & Authorization

<details open>
<summary><strong>Click to expand</strong></summary>

### 8.1 Authentication Requirements
None (decided): local single-user prototype. The server binds to **127.0.0.1** by default; exposing it requires an explicit config change. Login and multi-tenancy are production work (Section 16).

### 8.2 Authorization Rules
| Resource | Action | Required Permission | Scope |
|----------|--------|-------------------|-------|
| All endpoints | Read/Write | None (localhost only) | Single local user |

### 8.3 Security Considerations
1. **SSRF (URL guard):** user-typed URLs fetched by the server are restricted to http/https; DNS resolved and private, loopback, link-local (incl. 169.254.169.254), multicast and unique-local targets rejected; re-checked after every redirect; redirects and response size capped.
2. **Prompt injection:** store text enters the model only as tool-result data; long fields truncated; tools are read-only (no actions the model can trigger); `storeId` injected server-side; number validator still applies.
3. **XSS:** React escapes output; product HTML descriptions are stripped to text server-side; no `dangerouslySetInnerHTML`.
4. **CSRF:** not applicable (no cookies/sessions, localhost binding).
5. **SQL injection:** Spring Data JPA parameterised queries only.
6. **Outbound rate limiting:** one request at a time per store host, delay between requests (Q1), honour 429/Retry-After.
7. **Responsible scraping:** robots.txt obeyed; public pages only; no logins, no checkout, no cart creation; clear user agent; findings framed as opportunities.

### 8.4 Sensitive Data Handling
- No personal data is collected (public catalog/pages only).
- Secrets: none required for Ollama; optional `BEACON_LLM_API_KEY` via environment; `.env` git-ignored, `.env.example` committed.
- Logs never contain API keys.
- Data retention: raw snapshots 7 days full, then one per day (FR-5); delete `./data/` to reset.

</details>

---

## 9. Error Handling & Edge Cases

<details open>
<summary><strong>Click to expand</strong></summary>

### 9.1 Error Responses

| Status | Code | Message | Cause |
|--------|------|---------|-------|
| 400 | `INVALID_INPUT` | "Validation failed" | Bean validation failure |
| 400 | `INVALID_URL` | "That doesn't look like a web address" | Unparseable / non-http(s) URL |
| 400 | `URL_NOT_ALLOWED` | "This address can't be scanned" | SSRF guard |
| 404 | `NOT_FOUND` | "Not found" | Unknown store/job |
| 409 | `STORE_ALREADY_TRACKED` | "This store is already tracked" | Duplicate domain |
| 409 | `REPORT_NOT_READY` | "First scan still running" | No completed snapshot yet |
| 422 | `PLATFORM_NOT_SUPPORTED` | "We couldn't read a product catalog from this store" | Detection failed |
| 422 | `CATALOG_FEED_UNAVAILABLE` | "This Shopify store uses a custom storefront that doesn't publish its catalog" | Headless Shopify, feed 403/404 |
| 502 | `STORE_NOT_REACHABLE` | "The store didn't respond" | Network/DNS failure after retries |
| 503 | `STORE_RATE_LIMITED` | "The store asked us to slow down" | Repeated 429 |
| 500 | `INTERNAL_ERROR` | "Something went wrong" | Unexpected |

Job-level codes (in `JobResponse.error`, not HTTP): `ROBOTS_UNAVAILABLE`, `SNAPSHOT_WRITE_FAILED`, `PARSE_FAILED`, plus the network codes above.

### 9.2 Error Response JSON Examples
Format (decided `{code, message, hint}` extended with the template's fields):
```json
{ "timestamp": "2026-10-03T10:30:00Z", "status": 400, "code": "URL_NOT_ALLOWED",
  "message": "This address can't be scanned",
  "hint": "Use a public store address such as stevemadden.com", "path": "/api/stores" }
```
```json
{ "timestamp": "2026-10-03T10:30:00Z", "status": 400, "code": "INVALID_INPUT", "message": "Validation failed",
  "hint": "Check the highlighted fields", "path": "/api/chat",
  "details": { "errors": [ { "field": "message", "message": "Message is required" } ] } }
```
```json
{ "timestamp": "2026-10-03T10:30:00Z", "status": 409, "code": "STORE_ALREADY_TRACKED",
  "message": "This store is already tracked", "hint": "Opening the existing store", "path": "/api/stores",
  "details": { "storeId": 1 } }
```
The UI always shows `message` + `hint`; internal details stay in logs.

### 9.3 Edge Cases

| Case | Expected Behavior | Handling |
|------|-------------------|----------|
| First snapshot (no history) | "What changed", sell-out speed show "Available after the next check (in ~6 h)" | DiffService / UI |
| `updated_at` bulk-sync timestamps | Never used for timing | DiffService uses snapshot windows only |
| robots.txt with multiple `*` groups (Reebok) | Rules merged | RobotsParser |
| robots.txt 404 | All allowed | RobotsPolicy |
| robots.txt 5xx/timeout | Disallow all for this run; job explains | RobotsPolicy / JobService |
| `/policies/` blocked (Reebok, Meshki) | Footer `/pages/*` returns/shipping pages used | ShopifyAdapter (CHECKED_VIA_ALTERNATIVE) |
| `/search` blocked (Reebok, Meshki) | Search test NOT_CHECKED_ROBOTS; findability checks still run | JourneyService |
| No best-seller collection (Reebok) | Demand from other signals; lower confidence shown | DemandModel |
| Home page links load via JS (Steve Madden: 2 links) | Notice; collections checked instead | PromotedService |
| `/products.json` lacks image alt | Alt text sampled via `/products/<handle>.json` | CatalogQualityService |
| Notify-me rendered only by JS | NOT_VERIFIABLE | Journey check |
| "Continue selling when out of stock" | Variant looks in stock; documented limitation (undercounts) | Data notes |
| Pre-order variants show available=true | Excluded via tags, counted separately | ExclusionRules |
| Size option order ≠ size order | Size-aware sort | SizeComparator |
| Unrecognised size formats | "Size format not recognised", not guessed | SizeParser |
| No size option (memberships) | Skipped from size analysis | SizeParser |
| compare_at == price | Catalog clean-up, no claim about strike-through | PricingService |
| Free-shipping amount not parseable | "Not found" | PricingService |
| Currency missing | "Currency unknown" | Store / Money |
| Malformed product JSON | Product skipped and logged | Adapter |
| Oversize response | Request aborted, logged, run continues where possible | PoliteHttpClient |
| 429 / Retry-After | Wait and retry; repeated → STORE_RATE_LIMITED | PoliteHttpClient |
| Network timeout / 5xx | Exponential backoff retries | PoliteHttpClient |
| Redirect to private IP | Blocked | UrlGuard |
| Two refreshes at once | Second joins first | StoreLockRegistry |
| Crash during write | No corrupt file (temp + rename); previous snapshot current | GzipFileSnapshotStore |
| Unknown snapshot schemaVersion | Clear error, not silent | SnapshotStore reader |
| Ollama down / slow | Rule-based answer | ChatService |
| Model invents a number | Deterministic answer, flagged | NumberValidator |
| Out-of-scope question | Honest refusal | ScopeGuard |
| Injected instructions in product text | Ignored (data only, read-only tools) | ChatService |
| Store blocks or rate-limits all traffic | Recorded snapshot fallback (demo profile) | Profiles |
| Catalog > 25,000 products (Culture Kings) | Stop at page 100; "catalog capped" notice; totals labelled partial | ShopifyAdapter (Q24) |
| Headless Shopify (Gymshark 403, Fashion Nova 404) | Try Generic; else CATALOG_FEED_UNAVAILABLE | AdapterRegistry |
| Non-Shopify site returns 200 HTML for /products.json (ASOS) | Not treated as Shopify (content check) | AdapterRegistry |
| JSON-LD without offers (ASOS) or no JSON-LD (CamelBak) | Stock-dependent insights not available; page checks only | GenericAdapter |
| Beauty/home "Size" = volume, set or bedding | No core-size weighting | SizeParser (Q25) |
| Two size dimensions (bra band × cup) | Per Q25 | SizeParser |
| Very large catalog scan time | ~100 pages × (delay + ~2 MB) ≈ several minutes; progress shown | PoliteHttpClient |

### 9.4 Fail-Safe Defaults
- A failed run never replaces a good snapshot.
- Unknown → "Not available" / "Not checked" with reason, never a guess.
- Robots uncertainty → don't fetch.
- Never expose stack traces to the UI; log with `storeId`/`jobId`.

</details>

---

## 10. Observability & Monitoring

<details open>
<summary><strong>Click to expand</strong></summary>

### 10.1 Logging Requirements
Structured logs; every line during a run carries `storeId` and `jobId` (MDC).
```json
{ "timestamp": "2026-10-03T08:00:04Z", "level": "INFO", "service": "beacon-lite", "operation": "FETCH",
  "storeId": 1, "jobId": "7f3c…", "path": "/products.json?page=3", "robots": "ALLOWED",
  "status": 200, "duration_ms": 412 }
```
```java
log.info("[CAPTURE] store={} job={} start", storeId, jobId);
log.warn("[FETCH] store={} path={} status=429 retryAfter={}s", host, path, retryAfter);
log.error("[CAPTURE] store={} job={} unexpected", storeId, jobId, e);
```

### 10.2 Metrics (exposed at `/api/metrics`)
| Metric | Type | Description |
|--------|------|-------------|
| `fetch.requests` | Counter (by store, outcome) | Requests sent / blocked by robots / failed |
| `fetch.duration_ms` | Timer | Per-request latency |
| `jobs.completed` / `jobs.failed` | Counter | Snapshot runs |
| `job.duration_ms` | Timer | Run duration |
| `chat.requests` | Counter (by provider LLM/RULES) | Chat volume |
| `chat.llm_latency_ms` | Timer | Model latency |
| `chat.validator_failures` | Counter | Answers replaced by deterministic text |

### 10.3 Alerts
Not applicable locally (no on-call). Production alerting (Prometheus/Grafana) is part of the production path (Section 16.3).

</details>

---

## 11. Deployment Plan

<details open>
<summary><strong>Click to expand</strong></summary>

### 11.1 Pre-Release Checklist
- [ ] All tests pass locally and in GitHub Actions
- [ ] Clean checkout builds with one command
- [ ] README followed step by step on a clean machine
- [ ] Demo profile works offline
- [ ] Definition of done (Section 17.4) complete

### 11.2 Run Modes

| Mode | Command | Notes |
|------|---------|-------|
| Dev / live | `sdk env && ./mvnw spring-boot:run -Dspring-boot.run.profiles=live` | `.sdkmanrc` selects Java 21 |
| Demo (offline) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` | Recorded snapshots |
| Prebuilt jar | `java -jar target/beacon.jar` | Any Java 21 |
| CLI | `java -jar target/beacon.jar scan stevemadden.com` | Writes `reports/` |
| Docker | `docker compose up` (Colima) | App + Ollama; `--profile postgres` adds PostgreSQL |

### 11.3 Feature Flags
Not used. Behaviour switches are Spring profiles (`demo`, `live`, `postgres`).

### 11.4 Rollback
Local prototype: `git revert`/checkout previous tag; delete `./data/` to reset state.

### 11.5 CI
GitHub Actions on every push: `./mvnw -B verify` (backend tests + frontend build and Vitest via frontend-maven-plugin).

</details>

---

## 12. Migration Strategy

<details>
<summary><strong>Click to expand</strong></summary>

Not applicable: new product, no existing system or data. (The earlier JSON-file design in the planning notes was never built; this DEVSPEC replaces it.)

</details>

---

## 13. AI Implementation Guide

<details open>
<summary><strong>Step-by-Step Implementation for AI Coding Assistants</strong></summary>

> Each checkpoint is reversible (delete its files / revert its commit) and ends with verification. Development tests run on **recorded real snapshots** because the build sandbox cannot reach store sites; live verification runs on the Mac.

### Overview

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  CP-0 Bootstrap & deps ─► CP-1 Core types ─► CP-2 Schema ─► CP-3 Entities ─► CP-4 Repos │
│                                                                                  │
│  CP-5a Fetch/robots/URL guard ─► CP-5b Adapters ─► CP-5c Snapshots/diff/jobs     │
│  ─► CP-5d Size + insights ─► CP-5e Journey ─► CP-5f Chat                         │
│                                                                                  │
│  CP-6 Controllers + errors + OpenAPI ─► CP-6b CLI + Insight Brief                │
│                                                                                  │
│  CP-7 FE types ─► CP-8 API client + hooks ─► CP-9 Components                     │
│                                                                                  │
│  CP-10 Demo data, packaging, Docker, CI ─► CP-11 Accuracy evidence               │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### CP-0: Bootstrap & dependencies
**Goal:** Project skeleton and every dependency available offline in the build sandbox.
**Depends On:** None · **Revertible:** Yes.
**Tasks:**
```
0.1 Create pom.xml (Spring Boot 3.3.3; web, data-jpa, validation, h2, postgresql, liquibase-core,
    springdoc-openapi-starter-webmvc-ui, jsoup, test; frontend-maven-plugin) and Maven wrapper; .sdkmanrc (java=21.0.4-tem)
0.2 Create frontend/ with package.json (react, react-dom, typescript, vite, recharts, @fontsource/inter,
    vitest, @testing-library/react, jsdom)
0.3 On the Mac (one time): resolve Maven deps + plugins into ~/.m2, run npm install → package-lock.json
0.4 Copy the resolved caches into the build sandbox; verify `./mvnw -o verify` on an empty app
```
**Verification:** [ ] offline build succeeds · [ ] `npm ci` reproducible from lockfile.

### CP-1: Core types & enums
**Files:** `common/ErrorCode`, `BeaconException`, `Money`; enums `Platform`, `StoreStatus`, `SnapshotStatus`, `JobType`, `JobStatus`, `JobStep`, `ChangeType`, `ExclusionReason`, `DemandSignal`, `Confidence`, `SizeKind`, `JourneyStageKey`, `CheckStatus`, `ActionState`.
**Verification:** [ ] compiles · [ ] unit tests for enum parsing.

### CP-2: Database schema (Liquibase)
**Files:** `db/changelog/db.changelog-master.yaml`, `db.changelog-set01_initial_schema.xml` (Section 4).
**Verification:** [ ] app starts on H2 with `ddl-auto=validate` · [ ] same changelog applies on PostgreSQL (`postgres` profile) · [ ] rollback script tested.

### CP-3: Entities · CP-4: Repositories
**Files:** entities per Section 6.5; repositories per Section 6.3.
**Verification:** [ ] `@DataJpaTest` CRUD + unique constraints (`uq_product_store_external`, `uq_variant_store_external`, `uq_action_store_key`).

### CP-5a: Fetch, robots, URL guard
**Files:** `security/UrlGuard`, `robots/*`, `fetch/*`.
**Verification:** [ ] RobotsPolicy tests on the three **real** robots.txt files (FR-3 cases) · [ ] UrlGuard rejects private/loopback/link-local/metadata IPs and redirects to them · [ ] stub-server tests: 429 + Retry-After, 503→200, timeout, oversize, malformed JSON.

### CP-5b: Adapters
**Files:** `adapter/*`.
**Verification:** [ ] ShopifyAdapter parses recorded responses of all three stores (pagination, collections, home page signals, footer policy pages, product JSON alt sample) · [ ] detection works on recorded home pages · [ ] ReplayAdapter round-trips.

### CP-5c: Snapshots, diff, jobs, scheduler, retention
**Files:** `snapshot/*`, `catalog/*`, `diff/*`, `job/*`.
**Verification:** [ ] atomic write test (simulated crash) · [ ] diff by external ids (rename = no add/remove) · [ ] concurrent refresh joins · [ ] failure keeps previous snapshot current · [ ] retention with fake clock.

### CP-5d: Size parsing + insights
**Files:** `size/*`, `insight/*`, `intent/*`.
**Verification:** [ ] SizeComparator fixes `[11,12,10.5,11.5,13]` · [ ] Steve Madden 3 Oct fixture: 771 products missing core sizes, 284 pre/back-order excluded, 1,342 compare-at = price · [ ] Meshki: `#REF!` + case-variant types flagged, `gwp` excluded · [ ] Reebok: Membership/Bundles excluded, no best-seller collection → lower confidence · [ ] no "Best seller" label without a best-seller collection.

### CP-5e: Journey scorecard
**Files:** `journey/*`.
**Verification:** [ ] Reebok/Meshki search = NOT_CHECKED_ROBOTS, returns = CHECKED_VIA_ALTERNATIVE (30 / 14 days) · [ ] Steve Madden returns 30 days via `/policies/refund-policy` · [ ] coverage counts correct.

### CP-5f: Chat
**Files:** `chat/*`, `src/main/resources/prompts/*`.
**Verification:** [ ] golden set (~30 questions) passes with RuleBasedRouter and a mocked LLM · [ ] NumberValidator rules (currency, %, rounding, dates, sizes) · [ ] scope refusals · [ ] injection test · [ ] timeout → RULES.

### CP-6: Controllers, error handler, OpenAPI · CP-6b: CLI + Insight Brief
**Verification:** [ ] MockMvc tests for every endpoint in Section 5 incl. error codes · [ ] Swagger UI lists all endpoints · [ ] `scan` writes `.md` and `.html` brief from a recorded snapshot.

### CP-7: Frontend types · CP-8: API client + hooks · CP-9: Components
**Verification:** [ ] types match API JSON (contract test against a sample report) · [ ] Vitest + RTL tests: KPI tooltips, Estimate badge, Money currency fallback, StatusPill states, action status menu, chat evidence pills · [ ] screens match the four mockups · [ ] responsive + keyboard checks.

### CP-10: Demo data, packaging, Docker, CI
**Verification:** [ ] `demo` profile runs offline with the three recorded stores · [ ] jar runs · [ ] `docker compose up` (Colima) starts app + Ollama; `--profile postgres` works · [ ] GitHub Actions green.

### CP-11: Accuracy evidence
**Verification:** [ ] back-test hit rate computed from collected snapshots (definition: Q14) and written to docs · [ ] spot-check screenshots of top findings vs live sites.

### Implementation Order Summary

| Order | Checkpoint | Depends On | Est. Time | Reversible |
|-------|------------|------------|-----------|------------|
| 1 | CP-0 Bootstrap & deps | None | Day 1 (am) | Yes |
| 2 | CP-1 Core types | CP-0 | Day 1 | Yes |
| 3 | CP-2 Schema | CP-0 | Day 1 | Yes |
| 4 | CP-3/4 Entities, repos | CP-2 | Day 1 | Yes |
| 5 | CP-5a Fetch/robots/guard | CP-1 | Day 1 | Yes |
| 6 | CP-5b Adapters | CP-5a | Day 1 | Yes |
| 7 | CP-5c Snapshots/diff/jobs | CP-4, CP-5b | Day 1 (start live collection) | Yes |
| 8 | CP-5d Size + insights | CP-5c | Day 2 | Yes |
| 9 | CP-5e Journey | CP-5d | Day 2 | Yes |
| 10 | CP-5f Chat | CP-5d | Day 3 | Yes |
| 11 | CP-6/6b Controllers, CLI | CP-5* | Day 3–4 | Yes |
| 12 | CP-7/8/9 Frontend | CP-6 (contract) | Day 3 | Yes |
| 13 | CP-10 Packaging/CI | all | Day 4 | Yes |
| 14 | CP-11 Accuracy evidence | ≥ 2 days of snapshots | Day 4 | Yes |

**Total:** Days 1–4 (Sat 3 – Tue 6 Oct) for the build; Days 5–6 docs, checks on a clean clone and buffer.

</details>

---

## 14. Documentation

<details>
<summary><strong>Click to expand</strong></summary>

### 14.1 Documents in this repository
| Document | Link |
|----------|------|
| README (run in 2 minutes, results, who it is for) | `README.md` |
| Development spec (this) | `docs/DEVSPEC.md` |
| How it works (every screen, number and data source) | `docs/HOW_IT_WORKS.md` |
| User guide | `docs/USER_GUIDE.md` |
| Accuracy (live spot-checks, back-test) | `docs/ACCURACY.md` |
| Coding standards | `docs/CODING_STANDARDS.md` |
| Assumptions (every judgment call) | `config/assumptions.yml` |
| Sample Insight Brief | `reports/stevemadden-insight-brief.md` |
| AI chat prompts and tool menu | `src/main/resources/prompts/` |
| Test Spec | Not created (decided); tests are specified in this document |

Earlier planning notes and mockups that this DEVSPEC consolidated are not part of the repository.

</details>

---

## 15. Open Questions & Decisions

<details open>
<summary><strong>Click to expand</strong></summary>

### 15.1 Decision Log (🟢 Decided)

| # | Decision | Detail / reason |
|---|---|---|
| D1 | Product = **Watcher + Insights + Dashboard + AI chat** in one loop | Agreed scope: one complete loop from data to action |
| D2 | Stores: **Steve Madden (hero), Reebok, Meshki** | Swym customers from the getswym.com logo wall; tested live 3 Oct |
| D3 | **Adapter architecture**: `StoreAdapter` + Shopify, Generic, Replay; registry auto-detects | "A new platform = one new class" |
| D4 | **Java 21 + Spring Boot 3.3.3 + Maven** | User choice (Spring Boot); 3.3.3 in local cache |
| D5 | Java 21 via SDKMAN **not default** (Homebrew Java 11 stays default); `.sdkmanrc`; builds use `JAVA_HOME` | Don't break other projects |
| D6 | **Free & open source only** for the running app | User requirement; licenses checked for every component |
| D7 | AI: **Ollama + Qwen2.5 7B** (Apache 2.0) via OpenAI-format `LlmProvider` | llama3.2 is under Meta's community license (not OSI); Qwen better at tool calling; same connector for vLLM/Groq/OpenAI/Claude |
| D8 | **Tool calling over pre-computed insights**; no vector DB/RAG, no SQL agent | Numbers need exactness; SQL agent risks wrong queries and tenant leaks; RAG/semantic SQL layer are future options |
| D9 | **10 read-only tools**, max 4 calls/question, number validator, scope refusals, rule-based fallback, golden set ~30 | Safeguards for AI answers |
| D10 | Restock score = **Demand × Gap × Value**; zero stock alone is not a signal; exclusions; **$ at risk directional, labelled Estimate, assumptions visible/adjustable** | Public data has no quantities/sales |
| D11 | **Public stand-in → Swym signal** labels; `IntentSignalProvider` interface | Shows the path to Beacon |
| D12 | **robots.txt read first and obeyed**; merge all `*` groups; best-selling **sort order never used**; store's **best-seller collections** used instead | Verified blocks on all three stores |
| D13 | Blocked checks: **policies via footer pages**; **search only where allowed**, findability checks elsewhere; statuses + coverage shown; comparison uses common checks only | Verified 3 Oct |
| D14 | **Snapshots every 6 h**; timing only from snapshot windows (`updated_at` not used) | Verified bulk-sync timestamps |
| D15 | Raw snapshots **gzip, atomic write, schemaVersion, retention 7 days then 1/day**; reports store **snapshotId + assumptionsVersion** | Data integrity, reproducibility |
| D16 | Deliver **tool and script**: web app + CLI `scan` + Insight Brief (.md + print-ready .html); real Steve Madden brief committed | A tool for exploring and a script for a shareable result |
| D17 | **6-stage journey scorecard** (Discover, Browse, Product page, Size & stock, Cart & checkout, Come back); equal weights per check | Explainable |
| D18 | Store-owner additions: photos + links, store currency, local time, trend lines, action status To do/Done/Dismissed, table search, responsive, print-ready brief | Owner review |
| D19 | Engineering additions: SSRF URL guard, localhost binding, prompt-injection handling, polite fetching (timeouts, backoff, 429, size caps), background jobs + per-store lock, health/metrics endpoints, structured logs, global error format `{code,message,hint}`, offline bundling, accessibility, fixture + stub-server + MockMvc tests, GitHub Actions, demo/live profiles | Senior-engineer review |
| D20 | **No Spring Boot Actuator** (Boot 3 version not in local cache); own `/api/health`, `/api/metrics` | Don't add untestable deps; Actuator = production upgrade |
| D21 | Storage: **H2 embedded (file) + Postgres-compatible schema + optional `postgres` profile**; raw snapshots as gzip files | User choice: suits all our data, zero setup |
| D22 | Frontend: **React + TypeScript + Vite**, **Recharts**, bundled Inter font | User choice; professional and testable |
| D23 | Supporting tools: **Spring Validation, springdoc OpenAPI, Liquibase, Vitest + React Testing Library**; Java records, no Lombok | User delegated "the best one"; Liquibase over Flyway to match the template's changelog convention |
| D24 | One-time **dependency download on the Mac**, caches copied into the build sandbox | Sandbox can't reach Maven Central / npm; lets everything be compiled and tested before handover |
| D25 | **No separate TESTSPEC**; tests specified in this DEVSPEC | User decision |
| D26 | DEVSPEC written before the code; published in the repo as `docs/DEVSPEC.md` | User decision |
| D27 | Responsible scraping: public pages only, no logins/checkout/cart creation, **no "add 9,999 to cart" trick**, findings framed as opportunities | These are Swym's customers |
| D28 | Corrections: no "all products missing alt text" claim (alt sampled via product JSON); compare-at = price reported as catalog clean-up, not "fake discount seen by shoppers" | Verified data |
| D29 | Quality bar: no invented numbers; every assumption in `config/assumptions.yml` and the Assumptions panel; "Not checked/Not available" instead of guesses; no dead code/TODOs/secrets | User: "don't assume anything" |
| D30 | Timeline Sat 3 – Fri 9 Oct; cut order store comparison → GenericAdapter depth → Docker; never cut core loop or docs | Plan |
| D32 | Docker via **Colima**; optional **Lighthouse** instead of PageSpeed API | Open-source swaps |
| D33 | Production stack (Kafka, ClickHouse, Postgres RLS, vLLM + LiteLLM, Langfuse, Polaris, Unleash, Kubernetes, OpenTofu…) documented as the scale path, not built | Section 16.3 |
| D34 | Out of scope: alerts, login/multi-tenancy, extra platform adapters, private data | Section 1 non-goals |
| D35 | **Git rules:** no commit, push or config change without the author's explicit approval; commits under the author's personal account only (repo-local identity) | User decision |

### 15.2 Values decided from proposals (🟢 Decided 2026-10-03 by Mohammed: all proposed defaults accepted; all remain configurable)

| # | Question | Decision (accepted default) | Impact |
|---|---|---|---|
| Q1 | Delay between requests to the same store | 500 ms, one request at a time | Politeness vs scan time (~11 catalog pages for Steve Madden) |
| Q2 | Sold-out product pages sampled (notify-me, size guide) | 5 | Pages are ~2 MB each on Steve Madden |
| Q3 | Products sampled for alt text (`/products/<handle>.json`) | 30 | Accuracy vs request count |
| Q4 | Weekly-demand baseline behind $ at risk | 20 units/week at demand score 1.0, linear | Size of every $ figure (labelled Estimate) |
| Q5 | Search test terms | Store's top 5 product types (lower-cased), e.g. "women's shoes" | Avoids hard-coded terms |
| Q6 | Java base package | `com.beacon` | Code layout |
| Q7 | LLM call timeout | 60 s, then rule-based fallback | Chat responsiveness on a laptop |
| Q8 | GitHub repo name, visibility, branch | Repo `beacon-lite`, public (free Actions minutes), branch `main` | Hosting + CI |
| Q9 | Node.js present on the Mac? | Check with `node -v`; if absent, frontend-maven-plugin downloads Node | CP-0 |
| Q10 | HTTP timeouts / retries / size cap | connect 10 s, read 30 s, 3 attempts, 20 MB | Robustness |
| Q11 | Demand signal weights, "recently launched" window, confidence thresholds | best-seller 0.35, promoted 0.15, sell-out speed 0.30, full price 0.10, recent 0.10; 30 days; HIGH ≥ 3 signals, MEDIUM 2, LOW ≤ 1 | Ranking |
| Q12 | Gap weights core vs edge sizes | core 2.0, edge 1.0 | Ranking |
| Q13 | Journey per-check scoring bands; thin-description and thin-title length; min home-page links; dead-stock age/discount; deep-discount threshold; how featured collections are identified | Bands per check in assumptions.yml (e.g. home page ≤ 500 KB = 100, ≥ 3 MB = 0, linear); thin description < 100 chars; thin title < 15 chars; min home links 10; dead stock = in stock, published > 180 days, discounted ≥ 30%; deep discount ≥ 50%; featured = collections linked from the home page | Scores |
| Q14 | Back-test definition | Flag = restock candidate with ≥ 1 core size sold out and SELL_OUT_SPEED signal; hit = product loses ≥ 1 more size or becomes fully sold out within the next 24 h of snapshots | Accuracy evidence |
| Q15 | Definition of "sizes sold out" for the KPI and comparison | Full catalog (e.g. Steve Madden 33.7%, 8,129 of 24,122) for both, so they match; newest-250 figures stay as verification notes only | KPI and compare screens |
| Q16 | How top 5 actions mix restock and catalog/journey findings | Top 3 restock items by $ at risk + top 2 non-restock findings by severity (matches mockup 01) | Overview |
| Q17 | Best-seller collection handle pattern | `best-seller|bestseller|best-of|top-sell` (plain "best" over-matches) | Demand signal |
| Q18 | Mixed size kinds in one product; DUAL size sort basis | Analyse each kind as its own run (regular vs wide); DUAL sorted by the men's number | Size gaps |
| Q19 | "Likely discontinued" rule (notes differ) | Fully sold out + not in a best-seller collection + was discounted | Exclusions |
| Q20 | Add "email capture (Klaviyo) detected" to the Come back stage? | Yes, as a third Come back check | Journey score |
| Q21 | Code formatter | Spotless with google-java-format (Java) + Prettier (frontend) | Code style |
| Q22 | jsoup version | Upgrade from cached 1.13.1 to the current release (1.13.1 predates a fixed DoS issue) since dependencies are downloaded anyway | Security |
| Q23 | Generic adapter sample size (product pages per scan) | 200 pages, labelled "Sampled" | Coverage vs scan time on non-Shopify stores |
| Q24 | Catalogs above Shopify's 25,000-product feed cap | Stop at the cap, label totals partial (no per-collection crawl) | Very large stores |
| Q25 | Gaps for non-fit sizes and two-dimension sizes | Volume/set/bedding: unweighted share sold out; bra band × cup: treat each band/cup combination as one size, no core weighting | Beauty, home, lingerie stores |


</details>

---

## 16. Future Considerations

<details>
<summary><strong>Click to expand</strong></summary>

### 16.1 Potential Enhancements
- **Swym intent data** via `IntentSignalProvider` (wishlist adds, back-in-stock signups by size) → measured $ at risk instead of estimates.
- **Shopify Admin API with merchant consent** (via the Swym app): real stock quantities, orders, policies, search behaviour; robots.txt no longer applies.
- **Alerts** (email/Slack) when a best seller sells out.
- **Login and multi-tenancy** (Keycloak or Shopify session tokens; Postgres row-level security).
- **RAG for text** (reviews: "which products get sizing complaints?") with pgvector/Qdrant; limited SQL tool over a semantic layer (Cube).
- More adapters (WooCommerce, BigCommerce); streaming chat responses; Lighthouse in the pipeline.

### 16.2 Technical Debt / Known Limitations
- "Continue selling when out of stock" variants look in stock (undercounts sold-outs).
- $ at risk is an estimate until intent or sales data exists.
- Timing precision limited to the snapshot interval.
- Search quality can't be tested where robots.txt blocks `/search`.
- Home-page product links can be JavaScript-rendered (no headless browser by design).
- Actuator, Micrometer and production observability are not used locally.

### 16.3 Follow-up Work (production path)
- [ ] Temporal + Kafka ingestion; ClickHouse for events; Postgres with RLS; Redis cache
- [ ] vLLM + LiteLLM gateway; Langfuse tracing; promptfoo evals
- [ ] Embedded Shopify app (React + Polaris); Unleash staged rollout 10 → 100 → 1,000 → 50,000 stores
- [ ] Kubernetes + Helm + Argo CD; OpenTofu; OpenTelemetry + Prometheus + Grafana

</details>

---

## 17. Developer Checklist

<details>
<summary><strong>Click to expand</strong></summary>

### 17.1 Backend Tasks
- [ ] CP-0 bootstrap; offline build works
- [ ] Core types/enums
- [ ] Liquibase schema (H2 + Postgres)
- [ ] Entities, repositories
- [ ] UrlGuard, RobotsPolicy, PoliteHttpClient
- [ ] Shopify, Generic, Replay adapters
- [ ] Snapshots (atomic gzip, schemaVersion, retention), diff, jobs, scheduler, per-store lock
- [ ] Size parser/comparator, exclusions, restock, size gaps, promoted, catalog, pricing, headline, report
- [ ] Journey scorecard with statuses and coverage
- [ ] Chat: provider, 10 tools, validator, scope guard, fallback, prompts, golden set
- [ ] Controllers, global error handler, OpenAPI
- [ ] CLI scan + Insight Brief (.md, .html); commit Steve Madden brief
- [ ] Structured logging with storeId/jobId; `/api/health`, `/api/metrics`

### 17.2 Frontend Tasks
- [ ] Types mirroring Section 5
- [ ] API client + hooks (job polling)
- [ ] Components per Section 7 matching the mockups
- [ ] Loading / empty / error / not-checked states
- [ ] Tooltips, Estimate badges, currency, local time
- [ ] Responsive + accessibility checks
- [ ] Vitest + RTL tests

### 17.3 DevOps Tasks
- [ ] Recorded snapshots of the three stores (demo profile + fixtures)
- [ ] Start live snapshot collection on Day 1
- [ ] Prebuilt jar; docker-compose (app + Ollama; postgres profile); Colima instructions
- [ ] GitHub Actions workflow
- [ ] README (run in 2 minutes; `sdk env`; `ollama pull qwen2.5:7b`)

### 17.4 Definition of Done (from quality standards; per-change rules in `docs/CODING_STANDARDS.md`)
- [ ] Every screen matches the mockup design and works on the three demo stores
- [ ] Every number on screen traces to data or a listed assumption
- [ ] Every error state tested (bad URL, non-Shopify store, blocked store, Ollama off)
- [ ] All tests pass; build runs from a clean checkout with one command
- [ ] README followed step by step on a clean machine
- [ ] No TODOs, dead code, secrets or unused files
- [ ] One formatter across the codebase (tool choice 🟢 Q21)

</details>

---

## 18. Appendices

<details>
<summary><strong>Click to expand</strong></summary>

### 18.1 Glossary

| Term | Definition |
|------|------------|
| Store / merchant | The online shop and its owner |
| Variant / SKU | One specific version of a product, e.g. "GERONIMO, black, size 9" |
| Snapshot | A dated copy of a store's catalog and page signals |
| Adapter | A reader for one kind of store (Shopify, Generic, Replay) |
| Core sizes | Middle 50% of a product's sorted size run (configurable) |
| Size gap | Core sizes sold out while others remain |
| Exclusion | Item kept out of restock logic (pre-order, back-order, non-physical, bundle, likely discontinued) |
| Demand × Gap × Value | Restock score components |
| $ at risk | Estimated weekly sales blocked by sold-out sizes (labelled Estimate) |
| Journey scorecard | Six stage scores (0–100) with per-check status and coverage |
| Coverage | "Based on N of M checks" for a score |
| Public stand-in | A public signal used in place of Swym intent data |
| Intent data | Signals of what shoppers want: wishlist adds, back-in-stock signups |
| Tool (chat) | One of 10 read-only functions the AI can call for real numbers |
| Number validator | Check that every number in an AI answer exists in tool output |
| robots.txt | A site's rules for automated tools; obeyed for every request |
| Insight Brief | One-page report (headline, evidence, actions, impact) |
| assumptionsVersion | Hash of `config/assumptions.yml` stored with each report |

### 18.2 Related Documents
See Section 14.

### 18.3 Verified Data Facts (3 Oct 2026)

| Fact | Steve Madden | Reebok | Meshki |
|---|---|---|---|
| Products (full catalog) | 2,513 (24,122 variants) | 1,361 | 2,865 |
| Variants sold out (newest 250 products) | 48% | 8% | 16% |
| Variants sold out (full catalog) | 33.7% | — | — |
| Products missing core sizes (default rule) | 771 | — | — |
| Products with < 3 images | 14 | — | — |
| Pre-order / back-order tagged | 284 | uses `coming-soon` | 42 in newest 250 |
| Compare-at = price (products) | 1,342 | 1 | 2 |
| Currency (`Shopify.currency`) | USD | USD | USD |
| Best-seller collection | `best-sellers-all-products` (348) | none | `best-sellers` (92) |
| Home page product links in HTML | 2 | 6 | 136 |
| Home page | ~1 MB, 157 scripts | ~1.5 MB | ~3 MB, ~420 scripts |
| Swym detected in home page HTML | Yes | No (not in home page HTML) | Yes |
| robots.txt blocks `/policies/` | No | Yes | Yes |
| robots.txt blocks `/search` | No | Yes | Yes |
| robots.txt blocks `/collections/*sort_by*` | Yes | Yes | Yes |
| Returns window (source) | 30 days (`/policies/refund-policy`) | 30 days (`/pages/returns-exchanges`) | 14 days (`/pages/returns`) |
| Free-shipping text | — | "free shipping" (no amount) | "Free Shipping On Orders Over $130 USD" |
| `/products.json` image alt field | Absent (alt present in `/products/<handle>.json`) | Absent | Absent |
| Variant `updated_at` | Bulk-sync timestamps | — | All 1,948 variants (newest 250) in one hour |
| Notify-me on sold-out page HTML | Yes (Swym script + text) | — | — |

Other Swym customers checked on 3 Oct (extras, not in scope): Culture Kings 59%, Asphaltgold 61%, White Mountain 45%, Petal & Pup 36%, Tibi 20%, Princess Polly 6% variants sold out (newest 250 products).

### 18.4 Non-physical / excluded product types (verified)
Steve Madden: Gift Cards, Bundle, `sgdonation`, `TRASHIE` · Reebok: Membership, Bundles · Meshki: `gwp`.

### 18.5 Public stand-in → Swym signal

| Public stand-in (today) | Swym intent signal (Beacon) |
|---|---|
| Sell-out speed between snapshots | Back-in-stock signups per SKU |
| In best-seller collection / promoted | Wishlist adds per product |
| Core-size gaps | Back-in-stock signups by size |
| Full-price sell-through | Wishlist-to-purchase conversion |
| Estimated $ at risk | Measured: signups × price × historical conversion |

### 18.6 References
- Spring Boot 3.3, Spring Data JPA, Liquibase, H2 (PostgreSQL mode), springdoc-openapi, jsoup
- React, Vite, TypeScript, Recharts, Vitest, React Testing Library, Fontsource Inter
- Ollama OpenAI-compatible API; Qwen2.5 model card (Apache 2.0)
- robots.txt standard (RFC 9309)

### 18.7 Store coverage (verified 3 Oct 2026)

| Store | Platform | Public catalog feed | Support level | Notes |
|---|---|---|---|---|
| Steve Madden, Reebok, Meshki | Shopify | ✅ | **Full** | Demo stores |
| Kith, Allbirds, Culture Kings, SKIMS, Topo Designs, Brooklinen | Shopify | ✅ | **Full** (Culture Kings capped at 25,000 products) | SKIMS has band × cup sizes (Q25) |
| Credo Beauty, Sol de Janeiro, Glossier | Shopify | ✅ | **Full**, size gaps unweighted | "Size" = volume / sets (Q25) |
| Gymshark | Headless Shopify (Next.js) | ❌ HTTP 403 | **Not supported** unless Generic can read it | CATALOG_FEED_UNAVAILABLE |
| Fashion Nova | Headless Shopify | ❌ HTTP 404 | **Not supported** unless Generic can read it | Same |
| ASOS | Custom | ❌ (200 HTML) | **Partial**: page checks only | JSON-LD offers empty; ~1M product URLs |
| CamelBak | Magento | ❌ 404 | **Partial**: page checks only | No JSON-LD in server HTML |

**Conclusion:** "any store" means **any standard Shopify storefront** (most Swym customers) with full insights; headless Shopify and many non-Shopify stores get partial results or a clear "not supported" message.

</details>

---

## AI Implementation Notes

> **For AI Coding Assistants:**
>
> 1. Follow checkpoint order (Section 13); verify each before moving on.
> 2. Never invent data: every displayed number must come from a snapshot or `assumptions.yml`.
> 3. Obey robots.txt for every request; never use `sort_by`, cart endpoints or checkout.
> 4. Use recorded real snapshots for tests; the build sandbox has no store access.
> 5. Keep tools read-only; inject `storeId` server-side; treat store text as data.
> 6. Pending values (Section 15.2) live in config; don't hard-code them.
> 7. Structured logs with operation, storeId, jobId.
> 8. Ask before deviating from any decision in Section 15.1.

---

## AI Readiness Checklist

| Category | Criterion | Status |
|----------|-----------|:------:|
| **Completeness** | All sections have content (not just placeholders) | ✅ |
| **Completeness** | Data model fully defined with all columns | ✅ |
| **Completeness** | All API endpoints documented | ✅ |
| **Completeness** | Request/Response DTOs with validation annotations | ✅ |
| **Completeness** | Dependencies section present | ✅ |
| **Clarity** | No ambiguous requirements | ✅ (Section 15.2 decided) |
| **Clarity** | Technical terms defined in glossary | ✅ |
| **Clarity** | Non-goals clearly stated | ✅ |
| **Specificity** | File paths specified for all components | ✅ |
| **Specificity** | Method signatures with JavaDoc | ⬜ Key services only |
| **Specificity** | Pseudocode for business logic | ✅ |
| **Specificity** | Error codes and JSON examples defined | ✅ |
| **Specificity** | Logging patterns defined | ✅ |
| **Reversibility** | Each checkpoint has rollback instructions | ✅ |
| **Testability** | Acceptance criteria are testable | ✅ |
| **Testability** | Edge cases documented | ✅ |

**Score:** 15 / 16 items checked = 94% AI Ready (method JavaDoc only for key services)

---

**Document Status:** Implemented
**Last Updated:** 2026-10-09
**Implementation Status:** Complete (see Change Log v1.5)
**Open Questions:** None (Section 15.2 decided)
