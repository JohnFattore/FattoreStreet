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
- Applies cumulative backward price adjustment factors for split/dividend-adjusted OHLCV (decoupled from IEX load — runs as a separate process)
- Fetches 10-K filings from SEC EDGAR, extracts the MD&A section, and generates ~500 word summaries via a local LLM (llama.cpp server)
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
| `LLM_SERVER_URL` | `http://localhost:8081` | URL of the llama.cpp server for 10-K summarization |

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

Dates already in the database are skipped automatically. IEX load saves raw prices only — price adjustments run as a separate process.

### Price Adjustments (Splits & Dividends)

Corporate action detection and price adjustment is decoupled from IEX loading. Run it separately:

```bash
# Adjust all tickers (skips SEC re-fetch for tickers with existing actions)
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/adjust-prices

# Force re-fetch from SEC for all tickers (catches new splits/dividends)
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?force=true"

# Adjust a single ticker
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/adjust-prices?ticker=AAPL"
```

You can also load pre-generated CSV files:

```bash
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/load-prices
```

### 10-K Filing Summaries

Fetches 10-K filings from SEC EDGAR, extracts the Management's Discussion and Analysis (Item 7) section, and generates ~500 word summaries using a local Qwen 2.5-7B model via llama.cpp.

Requires the llama.cpp server to be running (see `llm/run-server.sh`):

```bash
# Summarize all equities with a CIK
curl -H "X-Admin-Key: spike" http://localhost:8080/admin/summarize-filings

# Summarize a single ticker
curl -H "X-Admin-Key: spike" "http://localhost:8080/admin/summarize-filings?ticker=AAPL"
```

Retrieve stored summaries via the public endpoint:

```bash
curl "http://localhost:8080/filing-summaries?ticker=AAPL"
```

Already-summarized filings (by accession number) are skipped on re-runs.

### Run Tests

```bash
mvn test
```

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## License

MIT
