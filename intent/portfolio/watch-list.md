# Watch List

## Why

Investors follow securities before they buy and after they sell. Without a watch list in
[portfolio](README.md), a user has to look each one up again every visit, or fake it by entering
holdings they don't own, which distorts their returns.

## Requirements

### A user can add and remove securities to follow
Following a security shouldn't require holding it. (inferred)

### The watch list shows each security's recent performance and key fundamentals at a glance
The point of following is to notice change without opening each security. Each row shows recent
returns and headline figures from [fundamentals](../fundamentals/README.md).

### Each entry links to its security page
A row that catches the eye needs one step to the full picture. See [security page](security-page.md).

### A new watch list starts with broad market funds
An empty list gives a new visitor nothing to look at. It starts with a total US market fund and an
S&P 500 fund, which suits the passive-investing audience.

### The watch list lives in the browser and needs no sign-in
A browser-only list keeps the feature lightweight and lets any visitor use it without an account.
The cost is accepted: it doesn't follow a user to another device, and anyone on the same browser
shares it.

## Non-goals

- **Storing the watch list with the account, for now.** It may move to stored, per-user data later.
  If it does, it falls under [data ownership](../accounts/data-ownership.md) like holdings.
