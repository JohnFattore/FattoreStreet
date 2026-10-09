# Sign-in

## Why

Every per-user feature in [accounts](README.md) depends on knowing who is asking. Sign-in has to be
open enough that anyone can join a public site, and safe enough that a leaked database or a stolen
browser session does limited harm.

## Requirements

### Anyone can register with a username, email and password
The site is public and its core audience is individual investors, so joining needs no invitation
or approval.

### Weak passwords are refused at registration
A short or common password is the first thing an attacker tries. Registration refuses passwords that
are too short, too common, or too similar to the username. Today it applies no check, which has to
change.

### Passwords are never stored in readable form
A database leak must not hand out passwords that people reuse elsewhere. Only a one-way hash of
each password is kept.

### A signed-in user stays signed in without re-entering their password
Being thrown out mid-session loses work and drives people away. When a user's short-lived access
expires, the web app renews it on its own and retries the request, and the user only signs in again
when their longer-lived renewal has expired too.

### The author can switch an account off without deleting it
Abuse on a public site has to be stoppable, but deleting an account destroys the evidence and the
user's data. A deactivated account can't sign in, and its data stays until someone decides
otherwise.

## Non-goals

- **Password reset, for now.** A forgotten password can't be recovered yet. An email reset is wanted
  eventually; deferred, not ruled out.
- **Self-service account deletion, for now.** Users can't delete their own account and data yet; the
  author's deactivation covers abuse. Wanted eventually; deferred, not ruled out.
