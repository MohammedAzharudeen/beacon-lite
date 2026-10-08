# Beacon Product Builder · Submission

**Mohammed Azharudeen A** · [github.com/MohammedAzharudeen/beacon-lite](https://github.com/MohammedAzharudeen/beacon-lite)

Beacon Lite reads any standard Shopify store's public catalog and pages, finds where the store is
losing sales, ranks the fixes by estimated $ at risk, and answers questions in plain English using
only numbers it can verify.

**Demo video:** [watch on Google Drive](https://drive.google.com/file/d/1j5I5jT9YMg3iZqkLrLhkg0dO2ll1VFwd/view?usp=sharing)

## The insight: Steve Madden (stevemadden.com), 8 Oct 2026

> **794 products are missing core sizes, and 155 styles have only one size left. About 216,638 USD
> a week is at risk (estimate).** Start with MARELYN OLIVE MULTI WIDE CALF: in the store's best-seller
> collection, launched in the last 30 days, not discounted, with 6 of 14 sizes left (about 2,125 USD a week,
> estimate).

**Friction across the shopper journey** (19 checks in 6 stages; journey score 75/100):

| Stage | Score | Finding |
|---|---:|---|
| Size & stock | **26** | 33.4% of size/colour variants sold out; 794 of 1,825 sized products missing a middle size while others are in stock |
| Discover | **51** | Home page HTML is 1.07 MB before images and scripts load, with 157 script tags |
| Product page | 80 | 0 of 30 sampled products have alt text on all images |
| Browse | 95 | Search returns relevant results; no fully sold-out products in best-seller collections |
| Cart & checkout | 100 | Free shipping from 75 USD (median price 79.99), Afterpay, Klarna, Shop Pay, 30-day returns |
| Come back | 100 | Wishlist app (Swym's script detected), "notify me" on 5 of 5 sampled sold-out pages, email capture (Klaviyo) |

The biggest problem is not the journey, it's stock: shoppers arrive, find the product and can't
get their size. Steve Madden's pages load Swym's script, and its sold-out pages show a "notify
me" option (the page doesn't show which app provides it). With intent data such as back-in-stock
sign-ups, Beacon would replace these estimates with measured demand.

## Where each part of the assignment is answered

| # | Assignment | Answer |
|---|---|---|
| 1 | Store, actionable insight from public data, journey friction | Above; full detail in the [dashboard](README.md#run-it), the [sample Insight Brief](reports/stevemadden-insight-brief.md) and [how every number is calculated](docs/HOW_IT_WORKS.md) |
| 2 | Functional, executable output | Runnable code ([run in one command](README.md#run-it), offline demo included), the CLI scan's [script output](reports/stevemadden-insight-brief.md), and the demo video |
| 3 | Prompts and workflow transcript | [PROMPTS.md](PROMPTS.md) |
| 4 | From question to answer; failure modes; safeguards for 50,000 stores | [Part 2 · Question to answer](docs/part2-question-to-answer.md) |
| 5 | Defensibility | [Part 2 · Defensibility](docs/part2-defensibility.md) |
| 6 | Discovery and prioritisation | [Part 3 · Product](docs/part3-product.md) |
| 7 | AI reflection | [How I used AI](docs/AI_REFLECTION.md) |

Also included: the [development spec](docs/DEVSPEC.md) written before any code, and the
[accuracy checks](docs/ACCURACY.md).

## Run it

Requires Java 21. The first build downloads its dependencies.

```bash
git clone https://github.com/MohammedAzharudeen/beacon-lite.git && cd beacon-lite
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo     # offline demo, 3 stores, 5 days of history
```

Open <http://127.0.0.1:8080>. Without `-Dspring-boot.run.profiles=demo` it reads live stores;
add any standard Shopify store from the top bar. Demo and live keep separate data (`data/demo/` and
`data/live/`).

## How I know the numbers are right

- A separate script recounted Steve Madden's products, sold-out sizes and latest changes from the
  raw 8 Oct data: all match the dashboard.
- On 3 Oct, five top restock findings across the three stores were checked on the live sites: 5 of
  5 matched.
- Back-test of the "selling fast" signal over 5 days: flagged products were 1.7–7× more likely to
  lose another size within 24 hours than other partly sold-out products.
- 238 backend and 24 frontend tests, run by CI on every push.

## Limitations

- **$ at risk is an estimate.** Public data has no sales, traffic or stock quantities; demand comes
  from public stand-ins (best-seller collections, promotion, sell-out speed, full price, recent
  launch). Swym's intent data is what would make it measured.
- Variants set to "keep selling when out of stock" look in stock, so sold-out counts can be low.
- Timing is only as precise as the check interval (6 hours).
- Content rendered only by JavaScript isn't seen; search can't be tested where robots.txt blocks it.
- Full support for standard Shopify stores; headless and many non-Shopify stores get partial
  results or a clear "not supported" message.
- Built for a super admin (internal team: any public store, no login). The merchant version would show a store only
  its own data.
