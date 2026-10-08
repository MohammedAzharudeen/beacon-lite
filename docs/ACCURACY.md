# Accuracy

How the numbers were checked against the stores themselves.

## Spot-check against the live sites (3 Oct 2026)

The top two restock findings for each store were taken from the recorded snapshots and compared
with each store's live product data on the afternoon of 3 Oct 2026, a few hours after the
recording.

| Store | Product | Beacon Lite: sizes still in stock | Live site | Match |
|---|---|---|---|---|
| Steve Madden | CALORA BLACK LEATHER | 6 (1 of 13) | 6 (1 of 13) | Yes |
| Steve Madden | DEVOUR BLACK MESH | 7, 10 (2 of 13) | 7, 10 (2 of 13) | Yes |
| Reebok | Premier Road Ultra LTD Shoes (Tranquil Teal) | M 4.5 / W 6 (1 of 17) | M 4.5 / W 6 (1 of 17) | Yes |
| Petal & Pup | Alara Midi Dress - Navy | XS, XL (2 of 5) | XS, XL (2 of 5) | Yes |
| Petal & Pup | Pierre Long Sleeve Maxi Dress - Deep Velvet Green | S (1 of 5) | S (1 of 5) | Yes |

5 of 5 matched. What this confirms: sizes are read correctly, including Reebok's dual men's/women's
sizes. It does not confirm the $ at risk, which is an estimate by design.

## Back-test of "selling fast" flags

**Definition.** A flag is a restock candidate with at least one core size sold out and the
sell-out-speed signal. It counts as a hit when, within the next 24 hours of snapshots, the product
loses another size or sells out completely. Hit rate = hits ÷ flags that had a later snapshot to
check against.

**Data.** The demo recordings: real snapshots of the three stores taken between 3 and 8 Oct 2026,
6 or 7 per store, 12 to 24 hours apart. Run it with:

```bash
./mvnw test -Dtest=BackTestRunner -Dbeacon.backtest=src/main/resources/snapshots
```

**Result**

| Store | Snapshots | Flags | Flags with a later snapshot within 24 h | Hits | Hit rate |
|---|---:|---:|---:|---:|---:|
| Steve Madden | 7 | 441 | 216 | 34 | 15.7% |
| Petal & Pup | 6 | 362 | 70 | 11 | 15.7% |
| Reebok | 7 | 122 | 59 | 5 | 8.5% |

**Baseline.** On their own these rates look low, so they were compared with products that also had
a size sold out but no new sell-out since the last check, over the same snapshot pairs (a separate
script recount from the raw files; simpler than the flag rule, so the flag rates differ slightly):

| Store | Sold out since last check | Other partly sold-out products | Ratio |
|---|---:|---:|---:|
| Steve Madden | 14.3% (36 of 252) | 3.4% (167 of 4,958) | about 4× |
| Petal & Pup | 11.6% (8 of 69) | 1.7% (68 of 4,037) | about 7× |
| Reebok | 6.2% (5 of 81) | 3.6% (57 of 1,567) | about 1.7× |

**What this means.** A "selling fast" flag makes a further sell-out 1.7 to 7 times more likely than
for other products, so the signal is worth its weight in the demand score; most flagged products
still don't lose another size within a day, so it is a ranking signal, not a prediction. Limits:
five days of data, checks 12 to 24 hours apart instead of 6, and a restock between checks can hide a
sell-out. Swym's back-in-stock sign-ups would measure the same demand directly.

## Recount of the demo's latest snapshot (8 Oct 2026)

A separate script recounted the Steve Madden figures the demo opens on from the raw file: 2,510
products, 8,029 of 24,016 sizes sold out (33.4%), and 145 sizes sold out and 201 back in stock
between the 7 and 8 Oct checks, with no price changes. All match the dashboard.
