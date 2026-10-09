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
returns and headline figures from [fundamentals](../fundamentals/README.md). (inferred)

### Each entry links to its security page
A row that catches the eye needs one step to the full picture. See [security page](security-page.md).
(inferred)

### A new watch list starts with broad market funds
An empty list gives a new visitor nothing to look at. It starts with a total US market fund and an
S&P 500 fund, which suits the passive-investing audience. (inferred)

## Open questions

- **Whose watch list is it?** The list is kept only in the browser, not with the account, so it
  doesn't follow a user to another device and it's shared by anyone using the same browser. Should
  it be stored per user like holdings (see [data ownership](../accounts/data-ownership.md)), or is
  browser-only intended so visitors can use it without signing in?
