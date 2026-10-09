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

### Accuracy is measured, not assumed
Without an independent check, gradual decay in detection would go unnoticed. At least weekly, the
adjusted prices of the main index's members are compared with an independent reference, and the
owner gets a report listing every security out of tolerance with the date of each discrepancy. The
check is read-only, and the reference data is never stored or shown to users. This falls under the
development exception in *Only commercially free data is stored or shown*
([principles](../principles.md)).

## Open questions

- **Third-party price charts.** The asset chart in the UI currently gets adjusted prices from a
  non-free third-party source through the main API. That appears to violate *Only commercially free
  data is stored or shown*. Should the chart move to the platform's own adjusted prices, with the
  third-party route kept only for the accuracy check?
- **Accuracy tolerance.** The accuracy check uses a 0.5% price-level tolerance today. Is that the bar
  you want to commit to?
