# Tickets

## Why

[Feedback](README.md) starts with a user telling the author something is wrong. A ticket is that
report, kept until it's dealt with.

## Requirements

### A signed-in user can file a ticket with a title and description
A report needs enough to act on and someone to follow up with. Filing requires signing in, which
also keeps anonymous spam out. (inferred)

### Every ticket has a status the author moves it through
Without a status, the author can't tell new reports from ones already handled. A ticket starts open
and moves through review to closed. (inferred)

### A ticket belongs to the user who filed it
Applies *A user changes only their own data* from
[data ownership](../accounts/data-ownership.md). The filer is always the signed-in user, whatever
the request claims, and other users can't see the ticket. (inferred)

### Changes to a ticket are kept, not overwritten
The author needs to see when a ticket was reopened or reworded. Every change keeps its previous
version. (inferred)
