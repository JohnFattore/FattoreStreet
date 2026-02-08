# SecFilingsAPI

A Spring Boot application providing SEC financial data for publicly traded US companies.

## Features
- Fetches real-time financial data from SEC EDGAR.
- Calculates Trailing Twelve Months (TTM) metrics.
- Provides Year-over-Year (YoY) growth analysis.
- Calculates Debt Ratios for S&P 500 companies.

## Getting Started

### Prerequisites
- Java 17
- Maven
- PostgreSQL

### Installation
1. Clone the repository.
2. Configure your database in `application.properties`.
3. Run with Maven:
   ```bash
   mvn spring-boot:run
   ```

## Documentation
- [API Endpoints](docs/API.md)

## License
MIT
