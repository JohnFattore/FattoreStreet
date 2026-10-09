# Data Ownership

## Why

[Accounts](README.md) exist so data can belong to one person. That only means something if the
platform enforces it everywhere: one area that forgets to check exposes every user's data in it.

## Requirements

### A user sees only their own private data
Holdings, accounts, advisor conversations and submitted tickets are visible only to
the user who created them. Asking for someone else's record behaves as if it doesn't exist, so
other users' records can't be discovered by guessing.

### A user changes only their own data
Creating, editing or deleting a record is allowed only for its owner. A new record always belongs
to the user who created it, whatever the request claims.

### Signed-out visitors can't reach private data at all
Every private record needs a signed-in owner, so a request without one gets nothing, not an empty
answer that hides a missing check.

### Public content stays readable without signing in
The platform's own market data, the blog and the recommendations are public by design, and putting
them behind sign-in would defeat the vision of an open platform.
