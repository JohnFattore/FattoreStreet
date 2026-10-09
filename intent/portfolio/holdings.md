# Holdings

## Why

Everything else in [portfolio](README.md) is computed from what a user says they hold and when they
bought and sold it. If a holding can be recorded wrongly or seen by the wrong person, every return
built on it is wrong or leaked.

## Requirements

### A user can group holdings into named accounts with a type
Long-term investors spread money across retirement and taxable accounts and think about them
separately. Each account has a name and a type such as Roth IRA, traditional IRA, 401k or taxable.

### A holding records a security, a share count, a buy date and an optional sell date
That's the minimum needed to value a position and its return over time. A holding with a sell date
is a closed position and stays on record, so past decisions can still be reviewed.

### Impossible holdings are rejected when they're entered
A holding with zero or negative shares, a date in the future, a date the market was closed, or a
sale before its purchase would produce a nonsense return that's hard to trace back. These are
refused at entry with a reason.

### Holdings and accounts are private to their owner
Applies [data ownership](../accounts/data-ownership.md). A holding can only be placed in an account
the same user owns. (inferred)

### Deleting an account deletes its holdings
A holding only means something inside its account, and leaving orphans behind would clutter every
total. The change history still records what was deleted.

### Changes to holdings keep their history
Users edit and delete positions, and without a history a mistaken edit can't be traced or undone.

## Non-goals

- **Partial sales.** A holding is sold all at once. Selling part of a position is done by splitting
  it into two holdings by hand.
