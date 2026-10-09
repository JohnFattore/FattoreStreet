# Adjusted Prices

## Why

This is what the market data branch is for: a price series in which returns are real. It combines
[raw prices](raw-prices.md) with [corporate actions](corporate-actions.md). Because detection works
from heuristics over unstructured filings, the result also has to be checked against an independent
reference, not assumed to be correct.

## Requirements

### Adjusted prices reflect every stored action
A series that ignores a split or dividend misstates every return that spans it. For each security,
the adjusted series accounts for every stored split and dividend using standard backward adjustment,
so the most recent adjusted price equals the raw price. A security with no actions has adjusted
values equal to its raw values.

### Nothing is shown as adjusted until it has been adjusted
A raw value labeled "adjusted" is silently wrong, and nothing would ever retry it. If adjustment
fails for a security, its adjusted values stay empty and it's retried on the next run.

### Every price chart uses the platform's own adjusted prices
A chart drawn from a third-party series shows numbers the platform can't audit or license. Charts use
the adjusted prices built here, and third-party prices appear only in the development-only accuracy
check below. Today the security page's chart takes them from yfinance; see
[portfolio](../portfolio/README.md).

### Accuracy is measured, not assumed
Without an independent check, gradual decay in detection would go unnoticed. At least weekly, the
adjusted prices of the main index's members are compared with an independent reference, and the
owner gets a report listing every security whose adjusted price differs from the reference by more
than 0.5%, with the date of each discrepancy. The
check is read-only, and the reference data is never stored or shown to users. This falls under the
development exception in *Only commercially free data is stored or shown*
([principles](../principles.md)).
