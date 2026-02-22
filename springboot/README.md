# SecFilingsAPI

A Spring Boot 3.4 microservice providing SEC EDGAR financial data for all publicly traded US companies.

## Features

- Fetches quarterly financial data from SEC EDGAR XBRL filings
- Bulk sync of all XBRL frames (2009 to present)
- Derives missing quarterly values from annual totals
- Calculates Trailing Twelve Months (TTM) metrics and Year-over-Year (YoY) growth
- Calculates financial ratios (net margin, gross margin, ROA, debt-to-assets)
- Ticker/CIK mapping from NASDAQ and SEC sources
- Downloads and parses IEX HIST TOPS pcap/pcapng files into daily OHLCV prices (built-in binary parser, no external dependencies)
- Detects stock splits and dividends from SEC EDGAR `EntityCommonStockSharesOutstanding` and `CommonStockDividendsPerShareDeclared`
- Applies cumulative backward price adjustment factors for split/dividend-adjusted OHLCV
- Also supports loading pre-generated OHLCV CSV files

## Stack

- Java 17, Spring Boot 3.4.2
- Spring Data JPA + Hibernate (PostgreSQL)
- Spring WebFlux (reactive HTTP client for SEC APIs)
- Bean Validation (`spring-boot-starter-validation`)
- Jackson CSV for NASDAQ data parsing and IEX price CSV ingestion
- Custom pcap/pcapng + IEX-TP binary parser using `ByteBuffer` for HIST TOPS trade extraction
- Maven build, Docker multi-stage image

## Getting Started

### Prerequisites

- Java 17
- Maven
- PostgreSQL

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/sec-api` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `ADMIN_API_KEY` | `spike` | Key for `X-Admin-Key` header on admin endpoints |
| `IEX_DATA_DIR` | `./data/iex_prices` | Directory containing IEX daily OHLCV CSV files |

### Run Locally

```bash
mvn spring-boot:run
```

The service starts on port **8080** by default.

### Run with Docker

```bash
docker build -t sec-api .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host:5432/sec-api \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=postgres \
  sec-api
```

### IEX Price Data

The service downloads IEX HIST TOPS pcap files, parses them in-process using a custom binary parser, and writes OHLCV data directly to the database. Trigger via the admin endpoint:

```bash
# Load ~1 year of IEX TOPS data (default 252 trading days)
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/load-hist

# Or specify a smaller window
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/load-hist?days=30"
```

Dates already in the database are skipped automatically. After loading, the service automatically detects splits and dividends from SEC EDGAR and applies adjustment factors.

You can also manually trigger price adjustments:

```bash
# Adjust all tickers
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/adjust-prices

# Adjust a single ticker
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?ticker=AAPL"
```

You can also load pre-generated CSV files:

```bash
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/load-prices
```

### Run Tests

```bash
mvn test
```

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## License

MIT
