# Market Cap

## Why

[Indexes](README.md) rank and weight companies by size, so a wrong size puts the wrong company in or
gives it the wrong weight. Size has to be built from free primary data: shares and float from SEC
filings, price from the exchange.

## Requirements

### Market cap is shares outstanding times the latest price
A company's size is the latest reported share count times its latest close. Both inputs are
primary-source and stored, so the figure can be reproduced.

### Free-float market cap counts only shares the public can trade
Shares held by insiders and controlling owners can't be bought, and counting them overweights
closely held companies. Free-float market cap scales market cap by the share of stock the public
holds, derived from the public float the company reports. When no float is reported, the whole
company counts as free-floating.

### Float and share count are on the same share basis
A split between the float reporting date and the latest share count would make the float ratio
wildly wrong, such as a tenth of its true value after a 10-for-1 split. The price used to turn the
reported float into shares is split-adjusted to the current basis.

### A share class is the same security whatever the source calls it
Sources spell share-class tickers differently: SEC writes Berkshire Class B as `BRK-B`, the exchange
feed `BRK.B` and the fund file `BRKB`. A company missing from the index because two sources
disagree on punctuation is a silent hole, so the forms are treated as one ticker.

### Each share class is sized from its own share count
A company with several share classes reports shares outstanding per class on its filing cover page,
and each listed class is its own security with its own price. Multiplying a combined count by each
class's price overstates the company, and multiplying one class's count by another's price makes
it unrecognizable (Berkshire Class B came out at $0.5B). Each class is sized from its own count,
read from the latest cover page, so no list of multi-class companies has to be kept by hand.
Unlisted classes (Alphabet's Class B, Visa's Class B and C) have no price and add nothing, for now.

### A stale share count is never used
A share count more than about 15 months older than the latest price says nothing about the company
today. The bulk SEC data keeps decade-old company-wide figures for multi-class filers, because it
drops the per-class figures their cover pages report. Such a figure sends the refresh to the cover
page, and if that has none, the company is skipped and keeps yesterday's size.

### Each company's security type and domicile are recorded
Construction excludes funds, partnerships, preferred stock and non-US companies, so each entry
records its security type and where the company is headquartered and incorporated. Securities whose
filings misclassify them as common stock are corrected. The rare corrections are kept as a
hand-maintained list; detecting them from filings isn't worth the cost.

### A source outage keeps yesterday's sizes
If the SEC can't be reached, sizes computed earlier stay in place rather than being cleared, and a
run that gets repeated failures stops early instead of hammering the source. Applies *Missing
evidence is not evidence of absence* and *Be a good citizen of free sources* from
[principles](../principles.md).
