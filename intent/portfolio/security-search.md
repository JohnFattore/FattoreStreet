# Security Search

## Why

The [security page](security-page.md) and [company comparison](../fundamentals/company-comparison.md)
open from a ticker, but a user researching a company often knows its name and not its symbol. If
the only way in is an exact ticker, they leave to look the ticker up on a commercial site, which is
the dependence the vision is meant to remove.

## Requirements

### Securities can be found by name or by ticker
A user types part of a company or fund name, or a ticker, and sees the matching securities from the
[security universe](../market-data/security-universe.md). Funds are found by their fund name as well
as by their filer's name, so "vanguard total" finds a Vanguard index fund. Today the universe holds
no names for most funds, so those are found by ticker only until fund names are loaded (see the open
question in [security universe](../market-data/security-universe.md)).

### Matches appear as the user types
Typing a whole name and submitting before seeing anything turns a misspelling into a dead end.
Matches show in a dropdown under the box while typing, and picking one opens it.

### Each match shows its name and its ticker together
One company can have several tickers, such as two share classes, and several companies have similar
names. Showing the name with the ticker lets the user pick the right security without guessing.

### An exact ticker comes first
Users who already know the ticker shouldn't have to scroll past name matches to reach it. A query
equal to a ticker ranks that ticker at the top, and pressing enter opens it.

### Small misspellings still find the company
Company names are long and inconsistently spelled ("Alphabet Inc." versus "ALPHABET INC"). A
one-letter typo or a difference in case or punctuation still returns the intended company near the
top.

### Search is available from every page
Looking up a security is the most common way into the platform, so a search box sits in the site's
navigation on every page. Places that ask for a ticker, such as the watch list and company
comparison, use the same search. A place that only works for companies, like the comparison, offers
only companies, so a user can't pick a fund that would fail to load.

### Search needs no sign-in
The security page is public, per *Anyone can open a page for any listed US security* in
[security page](security-page.md), so finding one is public too.
