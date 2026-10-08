# How I used AI

Beacon Lite was built in about a week with Claude (Anthropic) as a pair: it researched, drafted,
wrote code and tests, and ran the app; I set the direction, made the product calls and decided
what counted as done. The prompts that steered it are in [`PROMPTS.md`](../PROMPTS.md).

## How I worked

1. **Spec before code.** I had the AI turn my notes into a development spec using my own template:
   requirements with acceptance criteria, data model, API and build checkpoints CP-0 to CP-11
   ([DEVSPEC](DEVSPEC.md)). Every open question got a decision before the first line of code, so
   the AI was implementing decisions, not making them.
2. **Verify the data first.** Before designing anything, we checked what public data really
   exists on 16 stores. That changed the plan: headless Shopify stores have no public catalog, so
   "any store" became "any standard Shopify store", said honestly in the app. Later, one planned
   demo store (Meshki) turned out to show a bot check, so it was replaced rather than worked around.
3. **Build in checkpoints, each verified.** Each checkpoint ended with tests on recorded real
   store data. The project has 238 backend and 24 frontend tests, and CI runs them on
   every push.
4. **Check against reality, not against the AI.** Numbers were recounted from the raw data with a
   separate script, five top findings were checked on the live sites (5 of 5 matched), and I ran
   the app myself in both modes.

## What AI did well

- Speed on breadth: adapters, parsers, a database schema, a React dashboard and documentation in
  days rather than weeks.
- Thoroughness when asked: tests, edge cases (size runs, pre-orders, robots.txt rules), security
  checks (no private addresses, prompt-injection handling).
- Explaining its own work. I asked it to walk me through every screen, table and class until I
  could explain the system without it.

## Where it was wrong, and how it was caught

| What went wrong | How it was caught |
|---|---|
| Docs said 17 journey checks; the code has 19 | Cross-checking docs against code |
| The app crashed on restart with an existing database | Restarting the app on an existing database |
| Loading 20 demo snapshots ran out of memory in tests | Full test run after adding the data |
| A snapshot cut short by a stop stayed half-saved and the demo skipped it | Deliberately stopping the app mid-load during a "deep check" |
| Tooltips and labels overlapped on some screens | I spotted them in the running app; an automated layout check across 3 stores × 6 tabs × 3 widths then confirmed the fix |
| A quick re-check could drop the "selling fast" signal | Comparing numbers before and after a refresh. It follows the signal's definition ("sold out since the last check"); counting sell-outs over the last 24 hours instead is a next step |

The pattern: the AI is fast and usually right, and confidently wrong in small ways. Every one of
these was found by running the real thing or recounting independently, never by asking the AI
whether it was sure.

## What I decided myself

- **Who it is for:** a tool for a super admin (internal team) now; a merchant version that only ever shows a store
  its own data.
- **What not to do:** no invented numbers, no working around bot checks, no private data, no
  generic AI chat that does arithmetic.
- **The AI chat's role:** the model only picks a tool and words the answer; every number comes
  from tested code and is checked before it's shown. The same rule I'd want for Beacon at scale.
- **Scope calls**, such as keeping non-Shopify support minimal, and when something was good enough to ship.

## What I'd keep for production work

Spec first, small verified steps, independent checks of every number, and the human owning the
product decisions. AI made the week possible; the checks made it trustworthy.
