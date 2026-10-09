# Restaurants

## Why

Part of the author's public workspace in the vision is keeping personal recommendations. Restaurants
are one kind: places to eat, organised by where they are, with the author's own ratings and notes.
Without it, those recommendations live in someone's head or a phone note and can't be shared.
(inferred: secondary to the finance core)

It runs on the main API (Django) alongside accounts, because a review belongs to a signed-in user,
and that ownership already lives there. (inferred)

This branch has two parts:

- [Directory](directory.md): restaurants, browsable by place.
- [Reviews](reviews.md): a signed-in user's own ratings, with a map.

## Non-goals

- **A general restaurant review site.** This is the author's list, not a public review community.
  (inferred)
- **Menus and favourite dishes.** Started once and never used. (inferred)

## Open questions

- **Where does restaurant data come from, and may it be kept?** The page credits Yelp, and stored
  restaurants carry Yelp identifiers, star ratings and review counts, but the code that imported
  them is gone. Yelp's terms limit storing and displaying its data, which may conflict with *Only
  commercially free data is stored or shown* in [principles](../principles.md). Verify the license,
  replace the source, or remove the data?
- **Who can add restaurants?** Anyone, signed in or not, can currently add a restaurant to the
  directory. That conflicts with *Least privilege, no secrets in the repo* in
  [principles](../principles.md). Should adding be author-only, or done by a job?
- **Ownership checks don't match the rest.** Listing reviews relies on a check that doesn't stop a
  signed-out visitor at the door, unlike the rule in
  [data ownership](../accounts/data-ownership.md). Fix it here, or adopt one shared rule (see
  [accounts](../accounts/README.md))?
- **Recommendations.** The page still asks for restaurant recommendations, but that feature was
  switched off and the request goes nowhere. Revive it or remove it?
