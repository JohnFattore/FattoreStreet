# Changelog

## Why

[Feedback](README.md) is a two-way channel: users report, and they should be able to see that the
site keeps changing. A changelog records what changed and why, so a returning user can tell what's
new and whether their problem was fixed. (inferred)

## Requirements

### Each change records what it was, how it was resolved, and its status
A bare title doesn't tell a reader whether something was done. Each entry carries a description,
the solution, a status (pending, in progress, completed, failed) and a priority. (inferred)

### Entries are shown newest first
Readers care most about recent changes. (inferred)

### Changes to an entry are kept, not overwritten
A changelog that can be silently rewritten isn't a record. Every edit keeps its previous version.
(inferred)

## Open questions

- **Is this a changelog or a work queue?** Entries carry a priority, a free-form data field and a
  failed status, which reads more like an internal task list than a public record. Which is it
  meant to be?
- **Nobody can see it.** No page shows the changelog to users today. Should there be a public
  changelog page, or does the repo history already serve that purpose?
