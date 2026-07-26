---
paths:
  - "springboot/**/*.java"
---

# Spring Boot / Java Conventions

## Project Layout

```
springboot/src/main/java/com/fattorestreet/sec_api/
  controller/           # @RestController endpoints
  client/               # SEC HTTP client (WebService)
  index/                # index membership, metrics refresh
  corporateaction/      # dividends/splits, validation, price adjustment, filing discovery
  corporateaction/support/  # parsers, extractors, persisters (non-@Service helpers)
  fundamentals/         # EdgarService, quarters, financial metrics
  listing/              # assets, listings, ETF identity enrichment
  filing/               # 10-K MD&A fetch / LLM summary
  marketdata/           # daily prices, IEX HIST ingest
  repository/           # Spring Data JPA
  model/                # @Entity JPA entities
  config/, util/          # shared helpers
```

Mirror the same package names under `src/test/java/.../sec_api/` for unit tests.

## Code Style

- Java 17 features are available (records, sealed classes, pattern matching, text blocks)
- Use constructor injection (not field injection with `@Autowired`)
- Logging via SLF4J: `private static final Logger log = LoggerFactory.getLogger(MyClass.class);`
- Use `Map.ofEntries()` and `List.of()` for immutable collections
- camelCase for fields and methods; service classes handle the business logic, controllers stay thin

## Formatting

- 4-space indent, spaces only, 120-column soft limit (`/.editorconfig`)
- Run `./mvnw spotless:apply` before committing; CI runs `spotless:check` and fails on drift
- Import order: `java`, `javax`, `jakarta`, `org`, `com`, other, then statics. Spotless enforces this, so don't hand-sort
- Spotless never reflows code, so it will not touch line wrapping or brace placement in your diff
- Never pass `-Dquality.skip=true` locally; it exists only for the Docker build stage

## Linting

- `./mvnw checkstyle:check` runs the curated ruleset in `springboot/config/checkstyle/checkstyle.xml`
- No wildcard imports in `src/main` or `src/test`. Static wildcard imports (`import static org.mockito.Mockito.*`) are still allowed
- Checkstyle owns semantics; Spotless owns whitespace, import order and line length. Never add whitespace, wrapping or `LineLength` modules to the Checkstyle config
- Utility classes need a private constructor and `final`; the `@SpringBootApplication` class is exempt (Spring instantiates it) and is suppressed by name
- Records are implicitly static, so write `public record X(...)`, not `public static record X(...)`
- SpotBugs + FindSecBugs gate `mvn verify`. To silence a finding:
  - a whole category: `springboot/config/spotbugs/exclude.xml`, with a written rationale in the XML comment
  - one intentional site: `@SuppressFBWarnings(value = "...", justification = "...")` at the narrowest scope. The justification is mandatory
- Prefer `InputStream.skipNBytes` over `skipBytes`; the latter can skip fewer bytes than asked and returns the count, which silently desynchronises binary parsing
- Any regex run over SEC filing text must match through `BoundedRegexInput` (`corporateaction/support/`), which aborts a match that exceeds its budget. The date patterns combine lazy bounded quantifiers with month-name alternations and can backtrack badly. Do not "fix" that with possessive quantifiers — the lazy `?` deliberately finds the *nearest* date, and possessive matching changes which date a filing resolves to
- PMD also gates `mvn verify`, with a deliberately small ruleset in `springboot/config/pmd/ruleset.xml`. It references individual rules, never whole categories, so it only reports what Spotless, Checkstyle and SpotBugs do not. Keep it that way when adding rules
- Error Prone runs at `compile` via the `errorprone` profile. Severities are set in the `errorprone.checks.*` properties in `pom.xml`, not inline in the plugin config
- **`src/main` compiles warning-free under `-Werror`.** Any javac warning or Error Prone WARNING-tier finding fails the build, so a "harmless warning" is never an option there — fix it or justify a narrow `@SuppressWarnings`. `-Xlint:deprecation` is on; `rawtypes`/`unchecked` are deliberately not. `src/test` is exempt from `-Werror`
- Use `JsonNode.asString()` / `asString(default)`, never `asText()`. Jackson 3 deprecated `asText` and `-Xlint:deprecation -Werror` rejects it. `asInt`/`asLong`/`asDouble` are unaffected
- `String.split` / `Pattern.split` need an explicit limit: Error Prone's `StringSplitter` objects to the default limit-0 behaviour of dropping trailing empty fields. Use `PATTERN.split(s, -1)` with the pattern hoisted to a `private static final Pattern`
- Dead code fails the build: `UnusedMethod`, `UnusedVariable` and `UnusedNestedClass` are promoted to ERROR on `src/main`. `UnusedVariable` is the only gate that sees an unused constructor-injected field (PMD counts `this.x = x;` as a use, SpotBugs skips fields), so do not leave an injected dependency wired up "for later". Delete it and re-add when needed
- The dead-code trio stays advisory on `src/test`, because a `@Mock` field feeding `@InjectMocks` is never read yet is load-bearing — dropping it injects `null`. Do not "fix" such a field by deleting it
- Never pass `LocalDate.now()`, `LocalDateTime.now()` or `Year.now()` without a zone; `JavaTimeDefaultTimeZone` is ERROR-tier on both main and test sources. Use a `MarketTime` constant (`com.fattorestreet.sec_api.util.MarketTime`) rather than an inline `ZoneId.of(...)`:
  - `MarketTime.MARKET` (`America/New_York`) for anything that answers "what trading day / filing year is it" — IEX trading-date windows, SEC filing staleness, index metric years
  - `MarketTime.STORAGE` (UTC) for audit timestamps only ever compared to other stored timestamps (created-at, extracted-at, last-detected-at)
  - A test must use the same constant as the production code it asserts against, or it turns zone-dependent at day boundaries

## SEC EDGAR Patterns

- `EdgarService` is the core service; it maps SEC XBRL tags to normalized field names via `FIELD_TO_TAGS`
- The `STOCK_FIELDS` set distinguishes balance-sheet (instant) fields from income/cash-flow (duration) fields
- When adding new SEC data fields, add the mapping to `FIELD_TO_TAGS` and, if it's a balance-sheet field, to `STOCK_FIELDS`

## Data Access

- JPA + Hibernate with PostgreSQL
- Use Spring Data repository interfaces; avoid raw SQL unless performance requires it
- `WebService` in `client` uses `RestTemplate` for external HTTP calls to SEC APIs

## External Data Licensing

- Only add external data sources that are permitted for free commercial use.
- Before integrating a new source, verify Terms of Use or license coverage for commercial use, storage, and redistribution.
- If terms are unclear or restrictive, do not integrate the source.
