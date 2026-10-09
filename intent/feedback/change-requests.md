# Change Requests

## Why

The author is the only maintainer, and a [feedback](README.md) channel is only half useful if what
it turns up has nowhere to go. Change requests are the author's work queue: what needs doing, how
urgent it is, how it was resolved and whether it worked. The author keeps it in the main API's admin
screens, next to the tickets that often prompt it.

## Requirements

### Only the author can read or write change requests
It's the author's private work queue, and a queue anyone can rewrite is no record at all. Per *Least
privilege, no secrets in the repo* in [principles](../principles.md), no other user can create, edit,
delete or read an entry. Today any signed-in user can do all four, which has to change.

### Each request records what it is, how urgent, how it was resolved and its status
A bare title doesn't say whether something was done or how. Each entry carries a description, a
solution, a priority, a status (pending, in progress, completed, failed) and free-form notes.

### The author can focus on what's still open
Completed work buries what's left. The author can filter the queue by status and priority and hide
completed entries.

### Changes to a request are kept, not overwritten
A queue that can be silently rewritten loses the reasoning behind past decisions. Every edit keeps
its previous version.

## Non-goals

- **A public changelog.** Change requests are never shown to users. (inferred: the repo history
  already records what shipped)
