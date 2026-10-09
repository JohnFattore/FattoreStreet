# Market Cap

## Why

[Indexes](README.md) rank and weight companies by size, so a wrong size puts the wrong company in or
gives it the wrong weight. Size has to be built from free primary data: shares and float from SEC
filings, price from the exchange.

## Requirements

### Market cap is shares outstanding times the latest price
A company's size is the latest reported share count times its latest close. Both inputs are
primary-source and stored, so the figure can be reproduced. (inferred)

### Free-float market cap counts only shares the public can trade
Shares held by insiders and controlling owners can't be bought, and counting them overweights
closely held companies. Free-float market cap scales market cap by the share of stock the public
holds, derived from the public float the company reports. When no float is reported, the whole
company counts as free-floating. (inferred)

### Float and share count are on the same share basis
A split between the float reporting date and the latest share count would make the float ratio
wildly wrong, such as a tenth of its true value after a 10-for-1 split. The price used to turn the
reported float into shares is split-adjusted to the current basis. (inferred)

### Share classes of one company don't double count
Some companies list several share classes but report one combined share count. Multiplying that
full count by each class's price overstates the company. Known cases split the company's size
across its listed classes. (inferred)

### Each company's security type and domicile are recorded
Construction excludes funds, partnerships, preferred stock and non-US companies, so each entry
records its security type and where the company is headquartered and incorporated. Securities whose
filings misclassify them as common stock are corrected. (inferred)

### A source outage keeps yesterday's sizes
If the SEC can't be reached, sizes computed earlier stay in place rather than being cleared, and a
run that gets repeated failures stops early instead of hammering the source. Applies *Missing
evidence is not evidence of absence* and *Be a good citizen of free sources* from
[principles](../principles.md). (inferred)

## Open questions

- **Hand-kept exception lists.** Security-type corrections and dual-class splits are kept as fixed
  lists of known tickers. Is that acceptable, or should they be detected from filings?
