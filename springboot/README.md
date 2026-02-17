# SecFilingsAPI

A Spring Boot 3.4 microservice providing SEC EDGAR financial data for all publicly traded US companies.

## Features

- Fetches quarterly financial data from SEC EDGAR XBRL filings
- Bulk sync of all XBRL frames (2009 to present)
- Derives missing quarterly values from annual totals
- Calculates Trailing Twelve Months (TTM) metrics and Year-over-Year (YoY) growth
- Calculates financial ratios (net margin, gross margin, ROA, debt-to-assets)
- Ticker/CIK mapping from NASDAQ and SEC sources

## Stack

- Java 17, Spring Boot 3.4.2
- Spring Data JPA + Hibernate (PostgreSQL)
- Spring WebFlux (reactive HTTP client for SEC APIs)
- Bean Validation (`spring-boot-starter-validation`)
- Jackson CSV for NASDAQ data parsing
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

### Run Tests

```bash
mvn test
```

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## License

MIT
