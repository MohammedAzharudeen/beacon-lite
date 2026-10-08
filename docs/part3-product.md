# Part 3 · Product discovery and prioritisation

## Framework: follow the lost intent

Beacon's job is to turn shopping intent into action, so I look for places where **intent is
expressed but not converted**, size the money at stake, and build only where Swym has an unfair
advantage.

**1. Find problems where the evidence already is**

| Source | What it shows |
|---|---|
| **Swym's own intent data** | Where to look for lost intent: back-in-stock sign-ups that never turn into a restock, wishlisted products that sell out, sizes asked for again and again |
| **Store teardowns from public data** | What Beacon Lite does: read a store's catalog and pages and find sold-out best sellers, missing core sizes and journey friction, before ever talking to the merchant |
| **Merchant conversations and support tickets** | What merchants try to do by hand today (spreadsheets, weekly stock reviews) and what they ignore |
| **Customer success and sales teams** | Which questions come up on every call; which findings make a merchant lean in |

**2. Size and filter each problem**

- **Money at stake:** revenue lost per store per week, times how many stores have the problem.
- **Frequency:** a weekly decision (restocking) beats a yearly one (re-platforming).
- **Right to win:** does it need Swym's intent data, or can Swym act on it? If neither, a platform
  feature or another app will do it better or for free (see [defensibility](part2-defensibility.md)).
- **Actionable:** does the insight end in something the merchant or Swym can do in one step?

**3. Score and rank**

`priority = (value × reach × confidence) ÷ effort`, with a bonus when the feature strengthens the
data advantage (every use makes the next recommendation better).

**4. Prove it cheaply before building it properly**

Prototype on public data or with a handful of stores, measure whether merchants act, then build.
Beacon Lite is that step for restock priorities: built in a week on public data, with accuracy
checked against the live sites and a back-test.

**5. Measure outcomes, not usage**

Restocks taken after a recommendation, back-in-stock sign-ups converted, revenue recovered. A
feature that is opened often but changes nothing gets cut.

## Top 5 features to build next for Beacon, ranked

| Rank | Feature | What the merchant gets | Why this rank | Today in Beacon Lite |
|---|---|---|---|---|
| **1** | **Restock priorities from intent** | Products and sizes to restock first, ranked by measured lost revenue from back-in-stock sign-ups and wishlist demand, with evidence (for example "212 shoppers asked for size 8") | Weekly decision, large money at stake, needs Swym's data, and the loop is already proven in Beacon Lite. Highest value with the clearest right to win | Partly: the restock ranking is built, on public stand-ins (estimated $), not intent data |
| **2** | **Recovery tracker** | After a restock: alerts sent, shoppers who came back, revenue recovered, per product | Proves Beacon's value in dollars, enables pricing on outcomes, and every result improves the ranking. Small effort once #1 exists | Not built |
| **3** | **Sell-out alerts** | A message (email, Slack, the Shopify admin) within hours when a best seller loses a core size and shoppers start asking for it | Turns a report into a timely action. Medium effort | Not built: checks every 6 hours and shows what sold out in the Changes tab, but sends no alerts |
| **4** | **Ask Beacon, inside the merchant's assistant** | Natural-language answers about intent ("what are shoppers waiting for?"), including inside Shopify's Sidekick through an app integration | Distribution where merchants already work, and a hedge against the platform's own assistant. Built on the same verified tools as #1–3, so it comes after them | Partly: Ask Beacon works in the Beacon Lite dashboard, not inside Sidekick |
| **5** | **Early demand on new launches** | Wishlist momentum in a product's first days, to reorder or move stock before it sells out | High value for fashion and drops, but needs more history and calibration; ranked last because confidence is lowest today | Not built: a "New" signal for recent launches, but no wishlist momentum |

**Also valuable, outside the merchant product:** Beacon Lite as a super-admin tool for internal sales
and customer success teams. A one-page brief on any prospect's store from public data gives the
first call a concrete finding.

## What I would not build first

- **Generic sales and conversion dashboards:** the platform already offers them for free.
- **A standalone journey-friction score:** useful as evidence in a sales conversation, but not a
  weekly merchant decision and not tied to Swym's data.
- **Chat before the data loop:** the conversational layer is the easiest part to copy; it should
  sit on top of features 1–3, not replace them.
