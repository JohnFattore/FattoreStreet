# Spring Boot Test Suite Overhaul

## Context

The `springboot/` service (Spring Boot 4.1.0, Java 17) has a passing suite — 30 classes, 279 tests, `mvn test` → BUILD SUCCESS — but structural weaknesses: no coverage tooling, zero `@DataJpaTest` repository tests, ~15 classes with no tests at all (including real-I/O `IexHistService` and `PcapParser`), a 1017-line `MainControllerTest` monolith covering two controllers with ~20 `@MockitoBean`s, and the real `springboot/.env` (live secrets) leaking into the `@SpringBootTest` context via `spring.config.import=optional:file:.env[.properties]` (application.properties line 2). Goal: fix these, add coverage measurement with an enforced floor, and expand coverage to the riskiest untested logic.

**User decisions:** JaCoCo enforces a coverage floor (fails build on regression). Constructor-injection refactor of `HttpClient` in two production classes approved.

**Conventions:** follow `.cursor/skills/springboot-tests/SKILL.md` — lightest slice per target (plain JUnit for utils, `MockitoExtension` for services, `@WebMvcTest`+`@MockitoBean` for controllers, `@DataJpaTest` for repos), tests mirror the main package tree. Boot 4 APIs only (`@MockitoBean` not `@MockBean`, `spring-boot-webmvc-test` artifact, Jackson 3 `tools.jackson` where used). Suite stays hermetic — no real network/DB. Reuse `testsupport/TestJwtTokens.java` for controller auth.

## Phase 1 — Fix existing problems

### 1.1 Split `MainControllerTest`
`src/test/java/.../controller/MainControllerTest.java` (57 tests) → two classes:
- `AdminControllerTest`: `@WebMvcTest(AdminController.class)` + `@Import(SecurityConfig.class)` + `@TestPropertySource` SECRET_KEY; only AdminController's collaborators as `@MockitoBean`. Includes auth matrix: no token → 401, non-admin (`user_id=2`) → 403, admin (`user_id=1`) → 200, via `TestJwtTokens`.
- `PublicControllerTest`: `@WebMvcTest(PublicController.class)`; the 9 public GET endpoints, verifying anonymous access permitted.
- New `config/SecurityConfigTest`: explicit security-matrix tests — expired token → 401, wrong-signature token → 401, public path with no token → 200.
- Delete `MainControllerTest` only after the combined new test count ≥ 57 and both classes pass.

### 1.2 Neutralize `.env` leakage into tests
- `pom.xml`: add `maven-surefire-plugin` with `<workingDirectory>${project.build.directory}</workingDirectory>` so `optional:file:.env` resolves against `target/` (no `.env` there). First check no test reads files relative to cwd.
- `src/test/resources/application-test.properties`: pin env-sensitive keys as belt-and-braces (`SEC_CONTACT_EMAIL`, `DJANGO_PORTFOLIO_BASE_URL`, `LLM_SERVER_URL` pointing at unroutable localhost).
- Guard assertion in `SecApiApplicationTests`: `SECRET_KEY` env property equals the test value (proves `.env` didn't win).

### 1.3 Remove Mockito leniency
`IndexMetricsRefreshServiceTest` line ~41 uses `@MockitoSettings(strictness = LENIENT)`. Remove it; fix each `UnnecessaryStubbingException` by scoping stubs to the tests that use them (or targeted `lenient().when(...)` for genuinely shared setup). Grep for other `lenient` usages and audit.

## Phase 2 — Infrastructure: JaCoCo

Add `jacoco-maven-plugin` (0.8.13) to `pom.xml`: `prepare-agent`, `report` bound to `test`, and `check` with a BUNDLE line-coverage floor. Measure the post-Phase-1 baseline first, set the floor as a placeholder, then **ratchet it to (final Phase-3 coverage − 2 pts) at the end**. Exclude `SecApiApplication` bootstrap from the rule if it distorts numbers. Don't set a surefire `argLine` (would clobber the agent), or use `@{argLine}` if one is ever needed.

## Phase 3 — Expand coverage (riskiest first)

Use `@ParameterizedTest` (`@CsvSource`/`@MethodSource`) for input matrices — the suite currently has none.

### 3.1 Utilities (plain JUnit)
- `util/SecDateParsingUtilsTest` — parameterized over all date formats (ISO, "January 5, 2024", slash/short-year variants), `parseAnyDate` dispatch, `extractAllDates`, `previousBusinessDay` over weekends; nulls/blanks/garbage/invalid dates.
- `util/SecTextUtilsTest`, `util/SecNumberUtilsTest` — full public surface incl. null/empty and epsilon boundaries.
- `util/PcapParserTest` — **no committed binaries**: build pcap bytes programmatically in a shared helper `testsupport/PcapTestData.java` (global header + packet records with IEX trade-report messages); gzip through `GZIPOutputStream` into `@TempDir` for `parseGzip`. Cover valid trade reports, multiple packets, non-trade messages skipped, truncated stream, empty capture.

### 3.2 corporateaction (MockitoExtension)
- `EdgarFilingDiscoveryServiceTest` — `@Mock WebService`, real Jackson `ObjectMapper`. Fixtures: trimmed SEC submissions JSON in `src/test/resources/fixtures/edgar/`. Cover form-type filtering, pagination across submission files, malformed JSON, WebService failures, empty recent filings.
- `support/EquitySplitDetectorTest` — split detection from companyfacts JSON fixture, ratio computation, duplicate skip, no-split → zero stats, missing units/dates.
- `support/EtfActionPersisterTest` — each `PersistResult` branch (inserted/duplicate/updated) with `ArgumentCaptor` on repository saves.
- `CorporateActionValidationServiceTest` — **needs refactor R1**. Mock `HttpClient` serving Django dividends/splits JSON. Cover `validateTicker`: matched events, SEC-only, Django-only, amount mismatch beyond epsilon, `minDateInclusive` filtering, Django endpoint failure → graceful degradation, `summarizeBatch` KPIs. Keep `CorporateActionValidationCanaryTest` unchanged.

### 3.3 marketdata
- `IexHistServiceTest` — **needs refactor R2**. Mock `HttpClient` + reuse `PcapTestData` bytes. Cover `loadHistData`: OHLCV persistence via captor (first=open, max=high, min=low, last=close, sum=volume), weekend/holiday skipping, non-200 responses, empty HIST index.

### 3.4 index
- `IndexMemberApiServiceTest` — row mapping for `listAll`/`listByIndexCode`, unknown code → empty, missing metrics/listing edges.
- `IwbHoldingsTickerSetTest` — real classpath CSV via `DefaultResourceLoader`; missing-resource path via stub loader → empty set, no throw.

### 3.5 repository — first `@DataJpaTest`s (`@ActiveProfiles("test")`, H2, seed via `TestEntityManager`)
- `IndexMemberRepositoryTest` — `findAllWithListingAndAsset` fetch joins initialized.
- `ListingIndexMetricsRepositoryTest` — `findAllWithListingAndAssetByYear` year filter + joins.
- `DailyPriceRepositoryTest` — `findDistinctTickers`, `findTickersWithUnadjustedPrices` (null/non-null `adjustedClose` mix), `findTopByTickerAndTradeDateLessThanEqual` (exact/earlier/no match).
- `CorporateActionRepositoryTest` — `findDistinctTickers` + derived finders used by upsert paths.
- `AssetRepositoryTest` — `findEquityAssetsByTickers` excludes funds, `findAllWithListings`.

These also validate entity mappings and that every custom JPQL parses. Watch-out: hibernate-envers on the classpath — if `create-drop` audit tables cause `@DataJpaTest` startup issues, adjust envers test properties.

### 3.6 Thicken thin spots
- `fundamentals/EdgarServiceTest` (774-LOC class, 7 tests): after JaCoCo lands, target the red methods — frames sync, fact-sheet assembly, malformed-JSON error paths.
- Convert repetitive existing extractor/normalizer tests to `@ParameterizedTest` only where it deletes real duplication.

## Production refactors (approved, minimal)

- **R1** `corporateaction/CorporateActionValidationService.java:50` — add a constructor taking `HttpClient`; existing constructor delegates with `HttpClient.newBuilder().build()`. No behavior change.
- **R2** `marketdata/IexHistService.java:60` — same pattern, preserving the HTTP/1.1 + redirect settings in the default. Optionally widen `OhlcvAccumulator` to package-private for direct assertion.
- Everything else is already constructor-injected and mockable.

## Sequencing

1. Baseline `mvn test` (279 green). 2. Phase 1 items, running the suite after each. 3. JaCoCo + record baseline %. 4. Phase 3 in order 3.1 → 3.6 (R1 with 3.2, R2 with 3.3), `mvn test` after each package group. 5. Ratchet JaCoCo floor. 6. Final `mvn test` + `mvn spotbugs:check` (R1/R2 touched production code).

## Verification

- `cd springboot && mvn test` → BUILD SUCCESS, test count up from 279 to roughly 430–480, zero skipped.
- Hermeticity: suite passes with network unavailable (one-off run with `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1`); temporarily rename `springboot/.env` → identical results.
- Coverage: `open springboot/target/site/jacoco/index.html`; previously-red classes (`EdgarFilingDiscoveryService`, `EquitySplitDetector`, `IexHistService`, `PcapParser`, `SecDateParsingUtils`) show covered; `mvn verify` passes the `jacoco:check` floor.
- Controller-split parity: new controller tests ≥ 57 combined before deleting `MainControllerTest`.

## Critical files

- `springboot/pom.xml` — surefire workingDirectory, JaCoCo
- `springboot/src/test/java/com/fattorestreet/sec_api/controller/MainControllerTest.java` — split source
- `springboot/src/test/resources/application-test.properties` — env pinning
- `springboot/src/main/java/com/fattorestreet/sec_api/corporateaction/CorporateActionValidationService.java` — R1
- `springboot/src/main/java/com/fattorestreet/sec_api/marketdata/IexHistService.java` — R2
- `springboot/src/test/java/com/fattorestreet/sec_api/testsupport/` — `TestJwtTokens` (reuse), new `PcapTestData`
