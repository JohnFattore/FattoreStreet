# Series

## Why

The [economic indicators](README.md) page is only useful if it shows the handful of series that
actually move long-term investments, in a form a non-economist can read, and only series the
platform is allowed to show.

## Requirements

### The page covers the indicators that drive long-term returns
Interest rates and the yield curve, inflation, the labor market, growth and consumer activity,
housing, financial market conditions and monetary policy each have a section, so a user sees the
whole picture in one place.

### Fast-growing series are shown as year-over-year change
Price levels, payrolls, GDP and money supply only ever go up, so their raw level says nothing
about direction. Those series are shown as percent change from a year earlier; rates and ratios are
shown as they are.

### The yield curve is shown as a curve
Short, medium and long Treasury yields together show whether the curve is inverted, which is the
most-watched recession signal, and separate numbers hide it.

### Each indicator shows its latest reading and its date
Series update on different schedules, from daily to quarterly, and a reading without a date could be
months old.

### Readings are no more than a day behind FRED
Each indicator's stored readings are refreshed at least daily with no human action, so a new
release shows up by the next day.

### Missing observations are skipped, not shown as zero
FRED marks gaps such as holidays explicitly, and plotting them as zero would draw a false crash.

### Exception: a few third-party series are shown despite their owners' terms
This excepts *Only commercially free data is stored or shown* in [principles](../principles.md).
The S&P 500 level, the VIX, the ICE high-yield spread and the Case-Shiller home price index are
owned by private companies and only redistributed by FRED, under terms that restrict reproduction.
The owner has chosen to store and show them anyway, because the market-conditions and housing
sections are much weaker without them. The exception covers exactly these four series; any other
series must be public domain or verified free before it's stored or shown.
