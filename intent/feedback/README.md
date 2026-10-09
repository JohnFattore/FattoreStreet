# Feedback

## Why

One person runs the platform, with no support team and no second pair of eyes. When a number looks
wrong or a page breaks, the author only finds out if users can say so. And users trust a site more
when they can see that it changes and that problems get fixed. Without a feedback channel, problems
go unreported and the site looks abandoned even while it's being worked on. (inferred)

Feedback lives in the main API (Django) because a ticket belongs to a signed-in user, and the author
triages tickets in its built-in admin screens rather than in a separate tool. (inferred)

This branch has two parts:

- [Tickets](tickets.md): signed-in users report a problem or make a request.
- [Changelog](changelog.md): a record of what changed.

## Non-goals

- **A public issue tracker.** Tickets aren't visible to other users. (inferred)
- **Support guarantees.** No promised response time. (inferred)

## Open questions

- **Changelog edits are open to every user.** Any signed-in user can create, edit and delete any
  changelog entry, with no owner or author check. That conflicts with *Least privilege, no secrets
  in the repo* in [principles](../principles.md) and with
  [data ownership](../accounts/data-ownership.md). Should only the author write it, or should a job
  write it (see [accounts](../accounts/README.md) on one shared ownership rule)?
- **Can users see their own tickets?** A ticket can be submitted but never looked at again by the
  person who filed it, so they can't tell whether it was handled. Should users see their tickets and
  their status?
