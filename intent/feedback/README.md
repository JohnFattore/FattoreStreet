# Feedback

## Why

One person runs the platform, with no support team and no second pair of eyes. When a number looks
wrong or a page breaks, the author only finds out if users can say so. Without a feedback channel,
problems go unreported, and the author has nowhere to track what to fix next.

Feedback lives in the main API (Django) because a ticket belongs to a signed-in user, and the author
triages tickets in its built-in admin screens rather than in a separate tool.

This branch has two parts:

- [Tickets](tickets.md): signed-in users report a problem or make a request.
- [Change requests](change-requests.md): the author's private work queue.

## Non-goals

- **A public issue tracker.** Tickets aren't visible to other users.
- **Support guarantees.** No promised response time.
