# Restaurants

## Why

Restaurants is a legacy app from before the platform settled on its finance core. Any signed-in
user can keep a private log of restaurants they've tried, rated and placed on a map. It no longer
serves the vision, and it is frozen: kept running for now, not maintained beyond keeping it safe,
and not added to. It may later move out to a separate repo for retired FattoreStreet apps.

It still runs on the main API (Django) next to accounts, because its reviews belong to signed-in
users. While it stays, it must not weaken the rest of the platform, so the rules below are about
safety, not features.

## Requirements

### Only signed-in users can add a restaurant
An anonymous write on a public site is an open door for spam and abuse. Adding a restaurant needs an
account, per *Least privilege, no secrets in the repo* in [principles](../principles.md). Today
anyone can add one, signed in or not, which has to change.

### Reviews are private to their author
Applies [data ownership](../accounts/data-ownership.md). A user sees, changes and deletes only their
own reviews, and a signed-out visitor gets nothing. Today the review list doesn't turn signed-out
visitors away at the door, which has to change.

### Exception: Yelp data is kept for now
This excepts *Only commercially free data is stored or shown* in [principles](../principles.md).
Stored restaurants carry Yelp identifiers, star ratings and review counts, and the page credits
Yelp, under terms that restrict storing and displaying its data. The owner keeps them while the app
is frozen rather than spend effort on a retired app. The exception covers only the data already
stored: no new Yelp data is imported, and it ends when the app is retired or moved out.

## Non-goals

- **New features.** The app is frozen. Fixes that keep it safe are the only changes it gets.
- **Recommendations, for now.** A recommendation feature was switched off, and the page still calls
  it. The dead call is removed; reviving the feature is deferred.
- **Public reviews or a review community.** Reviews stay private to whoever wrote them.
- **Menus and favourite dishes.** Started once and never used.

## Open questions

- **When does it move out?** It could move to a separate repo for retired apps. What would trigger
  that: the next time it needs real maintenance, or a cleanup pass on its own schedule?
