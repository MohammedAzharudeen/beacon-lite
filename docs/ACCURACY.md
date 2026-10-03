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

**Status:** waiting for enough snapshots. The back-test needs at least two days of snapshots taken
about every 6 hours. With the recordings in `.bootstrap/recordings/`, run:

```bash
./mvnw test -Dtest=BackTestRunner -Dbeacon.backtest=.bootstrap/recordings
```
