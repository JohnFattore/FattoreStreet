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
