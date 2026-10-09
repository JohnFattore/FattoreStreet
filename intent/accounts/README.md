# Accounts

## Why

The vision promises that a user can track their own portfolio, and several other areas hold data
that belongs to one person: holdings, a watch list, restaurant reviews, advisor conversations and
feedback tickets. Without accounts there is no "own". Without strict ownership, one user could
read or change another's holdings, which on a public site is the most damaging failure the platform
could have. (inferred)

Sign-in runs on the main API, using Django's built-in user model and short-lived signed tokens
(JWT) that the web app renews on its own, so a user stays signed in without the server keeping
session state. (inferred)

This branch has two parts:

- [Sign-in](sign-in.md): registering, signing in and staying signed in.
- [Data ownership](data-ownership.md): a user's data is theirs alone.

## Non-goals

- **Social features.** No profiles, followers or sharing between users. (inferred)
- **Third-party sign-in.** No Google, GitHub or broker login. (inferred)

## Open questions

- **Preferences across devices.** Dark mode is remembered only in the browser, so it doesn't follow
  a user to another device. Is that fine, or should preferences be stored with the account?
- **One ownership rule or many?** Each area enforces ownership on its own, and they don't all agree
  (see [restaurants](../restaurants/README.md) and
  [change requests](../feedback/change-requests.md)). Should there be a single shared rule every area
  uses?
