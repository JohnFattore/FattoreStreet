# Directory

## Why

[Restaurants](README.md) are only useful as recommendations if a visitor can find the ones near
them. The directory is the list of places, organised by location, that reviews attach to.

## Requirements

### Anyone can browse restaurants by state and city
Recommendations for a place are worth nothing if you have to sign in to see them. Browsing the
directory needs no account, and results are narrowed to the chosen state and city. (inferred)

### Each restaurant has a location that can be mapped
Reviews are shown on a map, so every restaurant carries an address and coordinates. A restaurant
without a usable location can't be placed and shouldn't be listed as if it could. (inferred)

### Changes to a restaurant are kept, not overwritten
If an entry is corrected or vandalised, the earlier version is needed to see what changed and to put
it back. Every change to a restaurant keeps its previous version. (inferred)
