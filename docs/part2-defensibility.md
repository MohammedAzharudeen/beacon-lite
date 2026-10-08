# Part 2 · Defensibility

**Question:** is AI merchant intelligence a defensible standalone product, or a feature that
platform owners like Shopify will inevitably absorb?

## My position

**Generic AI analytics will be absorbed. Beacon is defensible only as the intelligence layer on
data Shopify doesn't have, attached to actions Shopify doesn't run.**

- "Ask questions about your store's sales, inventory and conversion" is already a free platform
  feature: Shopify's Sidekick is included with every plan and answers questions like these
  from the store's own Shopify data. A standalone product competing there loses on price,
  distribution and data access.
- What Shopify doesn't see is **pre-purchase intent**: who saved which product, who asked to be
  told when size 8 is back, how that intent behaves across many stores. Swym collects it through
  its apps (Swym Wishlist Plus and Swym Back in Stock Alerts); Store Leads counts about 35,000
  stores using Swym. That is the part worth building Beacon on.

## Why generic AI analytics becomes a feature

| Force | Effect |
|---|---|
| **Data access** | The platform owns orders, inventory and traffic. Any outside tool reads a subset through APIs and permissions the platform controls |
| **Distribution** | The assistant sits inside the admin merchants already use, at no extra cost |
| **Models are a commodity** | Tool calling, summaries and charts are available to everyone; prompts and dashboards are easy to copy |
| **Merchant budgets** | Merchants buy outcomes (recovered revenue) more readily than another dashboard |

If Beacon's answer to "which products should I restock first?" came only from orders and stock
levels, Sidekick could give the same answer for free.

## Where Beacon can be defensible

| Advantage | Why it is hard to copy | How Beacon uses it |
|---|---|---|
| **Intent data the platform doesn't collect** | Wishlist adds and back-in-stock sign-ups live in Swym's apps, gathered on the storefront before a purchase | Measured demand for sold-out sizes instead of guesses; for example "212 shoppers asked for size 8 this week" |
| **Cross-store benchmarks** | Only a company with intent data across many stores can say "stores like yours recover a third of back-in-stock demand within a week" (illustrative) | Anonymous benchmarks and better default conversion rates for new stores |
| **Closing the loop** | Swym's apps already send back-in-stock and price-drop alerts and wishlist reminders | Insight → one click → back-in-stock alert or wishlist campaign → measured revenue recovered |
| **Compounding accuracy** | Every recommendation and its outcome trains the ranking; a newcomer starts with no history | Back-tests become calibration; estimates become measured numbers over time |
| **Domain depth** | Size runs, core sizes, pre-order traps, variant quirks: small details that decide whether a recommendation is right | Fewer wrong recommendations than a general-purpose assistant |

Beacon Lite shows the gap concretely: from public data alone, its restock ranking has to say
"Estimate" on every dollar figure. The `IntentSignalProvider` interface is exactly where Swym's
data would replace those estimates; the rest of the product stays the same.

## The real risks

1. **Shopify collects intent itself.** Shoppers can already save products and follow stores in Shop,
   Shopify's shopping app. If Shopify brought wishlist and back-in-stock signals into the admin and
   Sidekick, the data advantage would shrink. *Response:* depth (demand per product and size,
   cross-store benchmarks, measured recovery) and speed.
2. **The platform controls access.** API terms, rate limits and app review can change. *Response:*
   be a good platform citizen and use the platform's own extension points (see below).
3. **"Nice insight, no action."** Insights without an action button get ignored. *Response:* every
   Beacon recommendation ends in something Swym can do, and is measured by revenue recovered.

## What I would do about it

- **Plug into Sidekick rather than compete with it.** Sidekick can only use an app's data when the
  app exposes it, and Swym Wishlist Plus already lists a Sidekick integration on its App Store
  page. Beacon should be the app that answers the intent questions inside the
  assistant merchants already use ("ask Sidekick → answered from Swym intent data"), and own the
  deeper workflow in Swym's admin.
- **Price on outcomes.** Tie Beacon to recovered revenue from back-in-stock and wishlist actions,
  which the platform's free assistant can't measure.
- **Invest in the data loop first, the chat second.** The defensible asset is calibrated demand
  per variant across stores; the conversational layer is the replaceable part.

## What would change my mind

- Shopify starts collecting wishlist and back-in-stock intent natively in the admin, with
  per-variant detail.
- Merchants don't act on intent-based recommendations more than on order-based ones (measure
  this in the first rollout stage).
- Benchmarks across stores don't improve accuracy for new stores.

---

Sources, checked 9 Oct 2026: Shopify Sidekick features and data access
([CraftShift](https://craftshift.com/shopify-sidekick-launch-date-features-2026/)); saving
products in Shop ([Shop Help Center](https://help.shop.app/en/shop/shopping/save-products-and-follow-stores));
Swym's store footprint
([Store Leads](https://storeleads.app/reports/technology/Swym)); Swym's apps
([Swym Wishlist Plus](https://apps.shopify.com/swym-relay),
[Swym Back in Stock Alerts](https://apps.shopify.com/watchlist)).
