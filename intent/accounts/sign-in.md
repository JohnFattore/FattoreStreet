# Sign-in

## Why

Every per-user feature in [accounts](README.md) depends on knowing who is asking. Sign-in has to be
open enough that anyone can join a public site, and safe enough that a leaked database or a stolen
browser session does limited harm.

## Requirements

### Anyone can register with a username, email and password
The site is public and its core audience is individual investors, so joining needs no invitation
or approval. (inferred)

### Passwords are never stored in readable form
A database leak must not hand out passwords that people reuse elsewhere. Only a one-way hash of
each password is kept. (inferred)

### A signed-in user stays signed in without re-entering their password
Being thrown out mid-session loses work and drives people away. When a user's short-lived access
expires, the web app renews it on its own and retries the request, and the user only signs in again
when their longer-lived renewal has expired too. (inferred)

### The author can switch an account off without deleting it
Abuse on a public site has to be stoppable, but deleting an account destroys the evidence and the
user's data. A deactivated account can't sign in, and its data stays until someone decides
otherwise. (inferred)

## Open questions

- **Password rules.** Registration doesn't appear to apply any strength or common-password check.
  Should it?
- **Account recovery.** There's no way to reset a forgotten password. Is that acceptable for now?
- **Deleting an account.** Can a user delete their own account and data, and what happens to their
  reviews and tickets if they do?
