# Security Universe

## Why

Every other part of market data, and every branch built on it, needs to know which securities
exist, which company stands behind each ticker, and which are funds. Prices that can't be tied to a
company can't be checked against its filings, and a fund mistaken for a company gets searched for
earnings and splits it doesn't report. The universe is the list everything else joins on.

## Requirements

### Every SEC-registered company and fund ticker is known
Users look up arbitrary tickers, and a ticker missing from the universe has no fundamentals and no
corporate actions. The universe covers every ticker the SEC publishes for operating companies and
for funds. (inferred)

### Each ticker is tied to the company that files for it
Fundamentals and corporate actions come from a company's filings, so a ticker is only useful once
it points at the right filer. Several tickers may share one filer, such as two share classes of the
same company, and they are kept as separate tickers of one company. (inferred)

### Funds are marked as funds
Funds don't report the earnings, share counts and dividends that companies do, so treating one as a
company produces empty or wrong results and wastes requests against the SEC's rate limit. A ticker
listed as a fund by the SEC is marked as one even when it also appears among operating companies.
(inferred)

### A fund carries its own SEC identity where one exists
A fund's distributions are found in filings made at the fund series and share-class level, not the
company level. Each fund ticker records that identity when the SEC publishes it, and a fund whose
identity can't be resolved is marked as unresolved rather than guessed. (inferred)

### The universe refreshes on its own and only grows
New listings appear and filers change over time, and a stale universe hides new companies. The
universe reloads on a schedule, at least monthly, and a reload adds and updates entries but never
removes a ticker because it is missing from one fetch. Applies *Missing evidence is not evidence of
absence* from [principles](../principles.md). (inferred)

### A reload that loads nothing is a failure
A rejected or misconfigured request to the SEC can return nothing while appearing to succeed, and a
"successful" run that stored nothing would leave the problem unnoticed. Applies *The data stays
current without a human* from [principles](../principles.md). (inferred)

## Open questions

- **Price symbols with no filer.** Most symbols in the price feed have no SEC filer. Should they be
  part of the universe users can search, or kept only as price history?
- **Delisted and renamed tickers.** When a company changes ticker or a ticker is reused by another
  company, how should the old association be kept so past prices still join to the right company?
