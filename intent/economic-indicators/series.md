# Series

## Why

The [economic indicators](README.md) page is only useful if it shows the handful of series that
actually move long-term investments, in a form a non-economist can read, and only series the
platform is allowed to show.

## Requirements

### The page covers the indicators that drive long-term returns
Interest rates and the yield curve, inflation, the labor market, growth and consumer activity,
housing, financial market conditions and monetary policy each have a section, so a user sees the
whole picture in one place. (inferred)

### Fast-growing series are shown as year-over-year change
Price levels, payrolls, GDP and money supply only ever go up, so their raw level says nothing
about direction. Those series are shown as percent change from a year earlier; rates and ratios are
shown as they are. (inferred)

### The yield curve is shown as a curve
Short, medium and long Treasury yields together show whether the curve is inverted, which is the
most-watched recession signal, and separate numbers hide it. (inferred)

### Each indicator shows its latest reading and its date
Series update on different schedules, from daily to quarterly, and a reading without a date could be
months old. (inferred)

### Readings are no more than a day behind FRED
A brief cache keeps page loads fast and FRED requests few, but never holds a reading longer than a
day. (inferred)

### Missing observations are skipped, not shown as zero
FRED marks gaps such as holidays explicitly, and plotting them as zero would draw a false crash.
(inferred)

## Open questions

- **Third-party series.** Several series are produced by private companies and served through FRED
  under their owners' terms, notably the S&P 500 level, the VIX, the high-yield spread and the
  Case-Shiller home price index. *Only commercially free data is stored or shown* in
  [principles](../principles.md) forbids showing them until their licenses are verified. Verify each,
  or drop them from the page?
