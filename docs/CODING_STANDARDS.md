# Coding Standards

Applies to every change in Beacon Lite. CI fails if a rule marked **(enforced)** is broken.

## 1. Project structure

```
beacon-lite/
├── pom.xml · mvnw · .mvn/ · .sdkmanrc         Build (Java 21, Spring Boot 3.3.3)
├── config/assumptions.yml                     Every judgment call (versioned by hash)
├── docs/                                      Standards, Part 2/3 docs, technical specs
├── reports/                                   Committed sample Insight Brief
├── scripts/                                   One-off developer scripts
├── src/main/java/com/beacon/<feature>/        Package by feature: config, security, fetch,
│                                              robots, adapter, store, snapshot, catalog, diff, job, size,
│                                              insight, journey, action, intent, chat, web, cli, common
├── src/main/resources/                        application.yml, db/changelog/, prompts/, snapshots/
├── src/test/java/com/beacon/<feature>/        Tests mirror main packages
├── src/test/resources/fixtures/<store>/       Recorded real responses (robots.txt, products.json, pages)
└── frontend/src/                              types/, api/, hooks/, components/<area>/, styles/, __tests__
```

- One responsibility per class; feature code stays in its feature package.
- `web` depends on services, never the reverse. Insight services are pure (no HTTP, no DB writes).
- Interfaces at boundaries: `StoreAdapter`, `SnapshotStore`, `LlmProvider`, `IntentSignalProvider`.

## 2. Java

| Rule | Detail |
|---|---|
| Formatting **(enforced)** | Spotless + google-java-format; run `./mvnw spotless:apply` before committing |
| Language level | Java 21: records for DTOs and value objects; `switch` expressions; no Lombok |
| Dependency injection | Constructor injection only; dependencies `private final`; no field injection |
| Naming | Classes `PascalCase` nouns (`RestockService`); methods verbs (`rankProducts`); constants `UPPER_SNAKE`; no abbreviations except URL/HTTP/JSON/SKU |
| Immutability | Prefer immutable records and `List.copyOf`; no public mutable state |
| Nulls | Return `Optional` from lookups; never return `null` collections; validate inputs at boundaries |
| Errors | Throw `BeaconException(ErrorCode, message, hint)`; never swallow exceptions; never expose stack traces to clients |
| Logging | SLF4J only, no `System.out`; pattern `log.info("[OPERATION] store={} job={} …")`; MDC carries `storeId`/`jobId`; never log secrets |
| Config | No hard-coded URLs, intervals, thresholds or model names; use `application.yml` / `assumptions.yml` |
| Money | `BigDecimal` + currency code; never `double` for prices |
| Time | `Instant` (UTC) everywhere; injected `Clock` for testability |
| Transactions | Short `@Transactional` boundaries; no transactions around network calls |
| Comments | Explain *why*, not *what*; Javadoc on public service methods; no commented-out code; no TODOs |
| Validation | Bean Validation annotations on request records (`@NotBlank`, `@Size`, `@Pattern`) |

## 3. TypeScript / React

| Rule | Detail |
|---|---|
| Formatting **(enforced)** | Prettier (`npm run format:check`) |
| Type safety **(enforced)** | `strict: true`; no `any`; API types in `types/beacon.ts` mirror the backend exactly |
| Components | Function components + hooks; one component per file; `PascalCase.tsx`; hooks `useX.ts` |
| Data access | Only through `api/beaconApi.ts`; components never call `fetch` directly |
| State | Local state + hooks; no global store unless needed |
| Security | Never `dangerouslySetInnerHTML`; render store text as plain text |
| UX rules | Every number has a tooltip; estimates show the Estimate badge; loading, empty, error and "Not checked" states always handled |
| Accessibility | Semantic elements, keyboard reachable controls, visible focus, colour never the only signal |
| Styling | Design tokens in `styles/tokens.css` (from the mockups); no inline magic colours |

## 4. Tests

| Rule | Detail |
|---|---|
| Location | Mirror the main package / component path |
| Naming | `methodOrBehaviour_condition_expectedResult` (Java); `it('does X when Y')` (frontend) |
| Data | Recorded real store responses only; no invented catalog data |
| Coverage expectation | Every insight rule, robots case, validator rule, error code and UI state has a test |
| Network | No live network in tests; stub server (JDK `HttpServer`) for HTTP behaviour |
| Must pass **(enforced)** | `./mvnw verify` runs Java tests, Vitest, frontend build and Spotless check |

## 5. Git

| Rule | Detail |
|---|---|
| Branch | `main` (single developer); one commit per completed checkpoint or logical step |
| Messages | Conventional Commits: `feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:` + short imperative summary |
| Never commit | `target/`, `data/`, `node_modules/`, `.env`, secrets, `.bootstrap/` |
| Before pushing | `./mvnw verify` green |

## 6. Definition of done (per change)

- [ ] Follows the structure above; no dead code, TODOs or unused files
- [ ] Formatted (Spotless, Prettier) and type-checked
- [ ] Tests added/updated and passing
- [ ] No hard-coded assumptions; new judgment calls added to `assumptions.yml`
- [ ] README, user guide and API docs updated when behaviour changes
