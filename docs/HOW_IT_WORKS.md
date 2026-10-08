# How Beacon Lite works

What every screen, number and label means, where its data comes from, and how it is calculated.
All judgment calls (thresholds, weights, patterns) live in [`config/assumptions.yml`](../config/assumptions.yml);
the values below are the defaults.

## Contents

1. [Where the data comes from](#1-where-the-data-comes-from)
2. [Checks, snapshots and modes](#2-checks-snapshots-and-modes)
3. [Top bar](#3-top-bar)
4. [Overview: headline and key numbers](#4-overview-headline-and-key-numbers)
5. [Restock priority and $ at risk](#5-restock-priority-and--at-risk)
6. [Top 5 actions](#6-top-5-actions)
7. [Shopper journey: 6 stages, 19 checks](#7-shopper-journey-6-stages-19-checks)
8. [Restock tab](#8-restock-tab)
9. [Sizes tab](#9-sizes-tab)
10. [Changes tab](#10-changes-tab)
11. [Catalog tab](#11-catalog-tab)
12. [Compare tab](#12-compare-tab)
13. [Ask Beacon](#13-ask-beacon)
14. [Assumptions panel](#14-assumptions-panel)
15. [Insight Brief and command line](#15-insight-brief-and-command-line)
16. [Glossary](#16-glossary)

---

## 1. Where the data comes from

Only public pages any shopper's browser loads. Before anything else Beacon Lite reads the store's
`robots.txt`; every later request is checked against it, and a blocked path is never requested.
Requests go one at a time per store with a pause between them.

| Public source | What it gives | Used for |
|---|---|---|
| `/products.json` (catalog feed, up to 100 pages × 250 = 25,000 products) | Every product: title, type, tags, price, compare-at price, images, publish date, and each variant (size/colour) with *available* or not | Sizes sold out, core sizes missing, restock list, $ at risk, images, descriptions, findability, pricing |
| `/collections/*/products.json` | Which products are in best-seller and featured collections | Best-seller and promoted signals, sold-out best sellers, promoted sold-outs |
| Home page HTML | Page size, script count, installed apps, free-shipping text, linked products, site name | Discover stage, app checks, free-shipping threshold, promoted signal |
| Policy pages (`/policies/refund-policy`, `/policies/shipping-policy`), or footer pages when those are missing | Returns and shipping text | Returns window, free-shipping text |
| `/search/suggest.json` (where robots.txt allows) | What the store's search returns | Search test |
| Sampled product pages (5 sold-out pages; 30 product JSON files) | Size-guide text, "notify me" button, image alt text | Size guide, notify me, alt text |
| The previous snapshot | What each variant looked like at the last check | Changes, "changed since last check", sell-out-speed signal |

**Which stores work:** standard Shopify storefronts fully. Headless Shopify stores (no public feed)
get "catalog not published". Other platforms with schema.org product data are sampled and labelled
"Sampled". A store that shows a bot check gets "This store blocks automated tools"; bot checks are
never worked around.

## 2. Checks, snapshots and modes

- **Check:** one full read of a store (robots.txt → catalog → store pages).
- **Snapshot:** the result of one check, saved as a compressed file plus database rows. Every
  report is tied to one snapshot and one assumptions version, so any number can be traced back.
- **When checks run:** when a store is added, when **Refresh now** is clicked, and every 6 hours
  automatically while the app runs (live mode). If a check is already running, a refresh joins it.
- **Failures:** a failed check never replaces the last good snapshot.
- **Demo mode** (`--spring.profiles.active=demo`): real snapshots of the three stores taken between
  3 and 8 Oct 2026 (6 or 7 per store, in `src/main/resources/snapshots/<domain>/`) are replayed
  oldest first at startup, so changes, trends and "selling fast" have history. The dashboard shows
  "Loading demo history" with a progress bar meanwhile (under a minute; `GET /api/demo/progress`).
  No network; Refresh replays the newest recording (so it finds no changes); no automatic checks.
  Data is kept in `data/demo/`.
- **Live mode** (no profile): checks read the real store. On an empty database the recordings are
  loaded first as a starting point. Data is kept in `data/live/`, so live and demo never mix.
- **Stopped part-way:** a snapshot and its report are saved together, so a stop never leaves half a
  result. A check cut short by a stop is marked failed at the next start ("The app stopped during
  this check"); the demo load then continues from the last complete snapshot.

## 3. Top bar

| Element | Meaning |
|---|---|
| Store picker | Switches the store shown |
| Last checked | When the latest check of this store finished |
| Refresh now | Starts a check now (see section 2) |
| Assumptions | Opens the assumptions panel (section 14) |
| Add a store | Adds any public Shopify store and starts its first check, showing each step |
| Dark banner | Public data only; robots.txt obeyed; $ figures are estimates until intent data is connected |
| API | Opens the API documentation |

## 4. Overview: headline and key numbers

**Headline:** the main finding from the latest snapshot: products missing core sizes, styles with
one size left, and estimated $ at risk per week. The line below gives the catalog size, capture time
and snapshot number.

| Card | Meaning | Calculation | Measured or estimate |
|---|---|---|---|
| **Sizes sold out** | Share of all variants marked unavailable | sold-out variants ÷ all variants (full catalog, nothing excluded) | Measured |
| **$ at risk / week** | Estimated weekly sales lost to sold-out sizes | Sum of $ at risk over all restock candidates (section 5) | **Estimate** |
| **Journey score** | How easy the store makes finding and buying | Average of the 6 stage scores (section 7) | Rule-based score on measured checks |
| **Changed since last check** | Variant changes between the last two snapshots | sold out + came back + price changes | Measured |

**Trend lines** under each card show the value across checks; they appear from the second check.
**Products missing core sizes:** products where at least one core size is sold out while another
size is still in stock. **One size left:** products with several sizes and exactly one available.

## 5. Restock priority and $ at risk

**Restock candidate:** a product with at least one size fully sold out, at least one demand signal,
and not excluded.

**Excluded** (listed under "Not restock candidates", never dropped silently):

| Reason | Rule |
|---|---|
| Pre-order | Tag contains pre-order, preorder, coming soon, purple-dot |
| Back-order | Tag contains back-order, backorder |
| Not physical stock | Type is gift card, gwp, membership and similar |
| Bundle | Type is bundle |
| Likely discontinued | Every size sold out, not in a best-seller collection, and was discounted |

**Demand signals** (public stand-ins for real demand):

| Signal | Rule | Weight | Swym signal it would become |
|---|---|---:|---|
| Best-seller collection | In a store collection whose handle matches best-seller, bestseller, best-of, top-sell | 0.35 | Wishlist adds per product |
| Selling fast | A size sold out since the last check (needs two snapshots) | 0.30 | Back-in-stock sign-ups per SKU |
| Promoted | Linked from the home page or in a featured collection (not counted again if already a best seller) | 0.15 | Wishlist adds per product |
| Full price | Has a sold-out size and no variant is discounted | 0.10 | Wishlist-to-purchase conversion |
| Recently launched | Published in the last 30 days | 0.10 | Early wishlist adds |

**Calculation**

```
demand score   = sum of the weights of the signals found          (0 to 1)
gap share      = weight of sold-out sizes ÷ weight of all sizes   (core size = 2, edge size = 1)
$ at risk/week = price × demand score × 20 units × gap share
```

Example, CALORA BLACK LEATHER: best seller + full price + recently launched = 0.55; 12 of 13 sizes
sold out, nearly all core, gap share ≈ 0.95; 199.95 × 0.55 × 20 × 0.95 ≈ **2,089 USD a week**.

**Confidence:** HIGH with 3 or more signals found, MEDIUM with 2, LOW with 1.

## 6. Top 5 actions

The 3 restock candidates with the highest $ at risk, then the 2 most severe other findings.
Severity: a journey check scores 100 − its score (alt text at 0 → severity 100); promoted sold-outs
80; compare-at = price and few images by the share of products affected. Each action shows its evidence, signals, confidence, and a status:
**To do**, **Done** or **Dismiss**. Dismissed actions leave the top 5 but stay in the full lists.
Click an action to see all its evidence. Catalog and journey actions carry no $ figure.

## 7. Shopper journey: 6 stages, 19 checks

**Scoring.** Count checks score the share that passed × 100. Band checks score 100 at the *best*
value, 0 at the *worst*, in a straight line between. App checks score 100 if found, 0 if not.
A stage score is the average of the checks that ran ("based on 2 of 3 checks"); the journey score is
the average of the stages.

**Statuses.** Checked · Checked via alternative page (e.g. returns found in the footer) · Not
checked: blocked by robots.txt (the rule is shown) · Not verifiable (content only appears after
JavaScript runs) · Not available (the data does not exist).

| Stage | Check | What it measures | Source | Score |
|---|---|---|---|---|
| Discover | Home page weight | Size of the home page HTML | Home page | Band: 0.5 MB = 100, 3 MB = 0 |
| Discover | Scripts on home page | Number of `<script>` tags | Home page | Band: 30 = 100, 200 = 0 |
| Browse | Search test | Searches for the 5 most common product types; a result is relevant if its title or type contains the term | Search suggestions | Share of searches with at least one relevant result |
| Browse | Catalog findability | Products with a type, a title of 15+ characters, and tags | Catalog feed | Share passing |
| Browse | Sold-out items in best sellers | Best-seller products that are fully sold out | Best-seller collections | Share not sold out |
| Product page | Product images | Products with fewer than 3 images | Catalog feed | Share with 3+ |
| Product page | Image alt text (sampled) | Sampled products with alt text on every image | 30 product JSON files | Share passing |
| Product page | Description depth | Descriptions under 100 characters | Catalog feed | Share at 100+ |
| Product page | Reviews app | A known reviews app (Yotpo, Okendo, Judge.me, Loox and others) | Home page | Found / not found |
| Product page | Size guide (sampled) | Product pages mentioning a size guide or chart | 5 product pages | Share mentioning |
| Size & stock | Sizes sold out | Share of variants sold out | Catalog feed | Band: 5% = 100, 50% = 0 |
| Size & stock | Core sizes missing | Sized products missing a core size while others are in stock | Catalog feed | Band: 5% = 100, 50% = 0 |
| Cart & checkout | Free-shipping threshold | Threshold ÷ median product price | Home page or shipping page + catalog | Band: 1.0× = 100, 3.0× = 0 |
| Cart & checkout | Buy now, pay later | Afterpay or Klarna | Home page | Found / not found |
| Cart & checkout | Shop Pay | Shopify's fast checkout | Home page | Found / not found |
| Cart & checkout | Returns window | Days allowed for returns | Refund policy or footer page | Band: 30 days = 100, 7 days = 0 |
| Come back | Wishlist | A wishlist app (Swym) | Home page | Found / not found |
| Come back | "Notify me" on sold-out pages | Back-in-stock alert offered | 5 sold-out product pages | Share offering |
| Come back | Email capture | An email app (Klaviyo) | Home page | Found / not found |

Search is lenient by design: it checks that results match, not that the best products rank first.
Apps are detected by name patterns in the page HTML, so an app loaded only by JavaScript can be missed.

## 8. Restock tab

Every restock candidate, ranked by $ at risk per week, with a link to the live product page.

- **Size strip:** one box per size in true size order; red = sold out, outlined = core size.
- **Signals, confidence, $ at risk:** as in section 5.
- **Search, category filter, Download CSV** (same columns as the table).
- **Not restock candidates:** excluded products with their reason (section 5).

## 9. Sizes tab

A grid of products × sizes. Green = in stock, red ✕ = sold out, dashed = size not offered, outlined
= core size. Sizes are sorted by real size order, not the store's order.

**Size runs and core sizes.** Sizes are parsed (numbers, letters, wide, kids C/T, dual men's/women's,
combinations). Each kind is its own run: regular and wide shoes, adult and kids sizes are separate.
Core sizes are the middle 50% of each run. Products with two size dimensions (e.g. waist × length)
count each combination as one size with no core weighting.

## 10. Changes tab

What changed between two snapshots: sizes that **sold out**, sizes that **came back**, and **price
changes**. Each change shows a time window ("13:01 → 19:51"), because Beacon Lite only knows it
happened between two checks. The store's own `updated_at` timestamps are not used (they reflect bulk
syncs). Variants are matched by id, so a renamed product is not an add plus a remove.

## 11. Catalog tab

| Finding | Rule |
|---|---|
| Few images | Products with fewer than 3 images |
| Image alt text | Sampled from 30 products |
| Short descriptions | Under 100 characters |
| Short titles | Under 15 characters |
| Missing type / untagged | Products with no product type or no tags |
| Inconsistent product types | Types that differ only by letter case ("Tops" and "TOPS") |
| Spreadsheet errors | `#REF!` and similar in titles or types |
| Compare-at = price | Compare-at price equal to the selling price (a fake-looking "sale" price) |
| Discount depth | Discounted products grouped by % off |
| Deep discounts | 50% off or more |
| Dead stock | In stock, published over 180 days ago, discounted 30% or more |
| Free-shipping threshold | Threshold vs median product price |
| Promoted sold-outs | Fully sold-out products still linked from the home page or featured collections |

## 12. Compare tab

Stores side by side: sizes sold out, $ at risk, comparable journey score, compare-at = price,
wishlist app, "notify me", pre-orders excluded, returns window and search test. Journey scores are
recomputed using only checks that ran on every store, so a check blocked on one
store does not skew the comparison.

## 13. Ask Beacon

Questions in plain English about the selected store.

1. **Scope check.** Questions public data cannot answer (conversion rate, traffic, sales, revenue,
   units in stock, customers, profit) get "Public data can't show that."
2. **Tool.** The AI model picks one of 10 read-only tools: store overview, restock priorities, size
   gaps, promoted sold-outs, journey scorecard, recent changes, pricing, product search, catalog
   health, store comparison. The server supplies the store; the model cannot choose another.
3. **Number check.** Every number in the answer must appear in the tool's output (rounding to the
   last digit allowed; dates must match exactly). If not, the answer is replaced by a template
   answer built from the data.
4. **Fallback.** If the AI model is not running, a rule-based router answers from the same tools.

| Pill | Meaning |
|---|---|
| ✓ N numbers verified | The answer passed the number check |
| Safe answer | The AI draft failed the check; the template answer is shown |
| Used: … | Which tool supplied the data |
| Includes estimates | The answer contains $ at risk figures |
| AI model / Rule-based | Who answered |
| Out of scope for public data | The question needs private data |

Store text is treated as data, never as instructions to the model.

## 14. Assumptions panel

Shows every judgment call and its version (a fingerprint stored with every report).

- **Try different demand values:** units per week at full demand and the five signal weights. The
  restock list re-ranks immediately. Changes stay in the browser tab only; **Reset to saved values**
  restores them.
- **All values:** core-size rule, exclusions, best-seller pattern, sampling sizes, catalog and
  pricing thresholds, journey bands, app detection patterns.
- To change values permanently, edit `config/assumptions.yml` and refresh the store.

## 15. Insight Brief and command line

**Open Insight Brief:** a one-page, print-ready summary (headline, evidence table, top 5 actions,
expected impact, journey scores, data notes). Use the browser's "Save as PDF" to share it.

**Command line:** `java -jar target/beacon.jar scan <store>` runs one check and writes
`reports/<store>-insight-brief.md` and `.html`; `--offline <snapshot file>` uses a recording.

## 16. Glossary

| Term | Meaning |
|---|---|
| Variant | One purchasable option of a product, usually a size or size/colour |
| Size run | A product's sizes of one kind, sorted (e.g. women's 5–11) |
| Core size | A size in the middle 50% of a size run |
| Edge size | A size outside the core range |
| Fully sold-out size | A size with every colour of it unavailable |
| Restock candidate | A product with a fully sold-out size, a demand signal, and no exclusion |
| Demand score | Sum of signal weights, 0 to 1 |
| Gap share | Weighted share of sizes sold out (core counts twice) |
| $ at risk | price × demand score × 20 units × gap share, per week; an estimate |
| Confidence | HIGH / MEDIUM / LOW by number of signals found |
| Snapshot | One check's saved result |
| Change window | The time between the two checks a change happened in |
| Assumptions version | SHA-256 fingerprint of `config/assumptions.yml` stored with each report |
| Demo mode | Replays the bundled recordings; offline |
| Live mode | Reads real stores; checks every 6 hours |
