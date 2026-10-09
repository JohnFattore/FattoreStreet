# Accounts

## Why

The vision promises that a user can track their own portfolio, and several other areas hold data
that belongs to one person: holdings, a watch list, restaurant reviews, advisor conversations and
feedback tickets. Without accounts there is no "own". Without strict ownership, one user could
read or change another's holdings, which on a public site is the most damaging failure the platform
could have.

Sign-in runs on the main API, using Django's built-in user model and short-lived signed tokens
(JWT) that the web app renews on its own, so a user stays signed in without the server keeping
session state.

This branch has two parts:

- [Sign-in](sign-in.md): registering, signing in and staying signed in.
- [Data ownership](data-ownership.md): a user's data is theirs alone.

## Requirements

### Every area uses one shared ownership rule
When each area writes its own ownership check, one forgets and leaks: that's how the restaurant
review list and the change-request queue ended up open. All areas use the same rule from
[data ownership](data-ownership.md), so a new area is safe by default. Today each area enforces
ownership on its own.

## Non-goals

- **Social features, for now.** No profiles, followers or sharing between users. Not ruled out.
- **Third-party sign-in, for now.** No Google, GitHub or broker login. Not ruled out.
- **Preferences stored with the account.** Dark mode is remembered in the browser only, which keeps
  it lightweight; it doesn't follow a user to another device.
