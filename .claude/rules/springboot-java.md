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
- PMD also gates `mvn verify`, with a deliberately small ruleset in `springboot/config/pmd/ruleset.xml`. It references individual rules, never whole categories, so it only reports what Spotless, Checkstyle and SpotBugs do not. Keep it that way when adding rules
- Error Prone runs at `compile` via the `errorprone` profile. Its ERROR-tier checks fail the build; WARNING-tier ones are advisory
- Pass an explicit `ZoneId` to `LocalDate.now()`, `LocalDateTime.now()` and `Year.now()`. Without one they silently use the server's default zone, which decides what "today" means for trading-day and filing-window logic. Error Prone reports these as `JavaTimeDefaultTimeZone`

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
