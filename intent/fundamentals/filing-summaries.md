# Filing Summaries

## Why

An annual report's management discussion explains *why* the numbers in
[fundamentals](README.md) moved, but it runs to dozens of pages. A short plain-language summary
lets an individual investor get the story without reading the whole filing. (inferred)

## Requirements

### Summaries are shown for the filings that have one
A user viewing a company sees any stored summaries of its annual reports, each tied to the filing
it summarizes, so the source can be checked. (inferred)

### A filing without a summary shows nothing, not an error
Summaries exist only for filings that were processed while generation was running. A company with
no summary simply shows none. (inferred)

### Each filing is summarized at most once
Summarizing costs model time and SEC requests, and two summaries of one filing would conflict. A
filing that already has a summary is skipped. (inferred)

## Non-goals

- **Investment advice in summaries.** A summary restates what the company said; it doesn't judge
  it. (inferred)

## Open questions

- **The feature is frozen.** Summary generation has been retired, so no new summaries appear and
  existing ones grow older. Keep it read-only as is, revive generation (and with what model and
  cost), or remove it from the page?
- **Labelling.** Should summaries be labelled as machine-generated, so users don't read them as the
  company's own words?
