# Reviews

## Why

The [directory](directory.md) lists places. A review is what turns a place into a recommendation:
a rating and a note from someone who ate there. Reviews belong to the user who wrote them, so the
rules in [data ownership](../accounts/data-ownership.md) apply.

## Requirements

### A signed-in user can rate a restaurant and add a note
A recommendation needs a judgement. Ratings run from 1 to 5 in half steps, with an optional
comment. (inferred)

### A user can see, change and delete only their own reviews
Narrows *A user changes only their own data* from [data ownership](../accounts/data-ownership.md).
Someone else's reviews are never shown in a user's list and can't be edited by them. (inferred)

### A user's reviews are shown on a map
Recommendations are about places, and a list of names doesn't show which ones are near each other.
A user's reviewed restaurants appear on a map at their locations. (inferred)

### Changes to a review are kept, not overwritten
A user who edits a rating may want the old one back, and abuse needs a trail. Every change to a
review keeps its previous version. (inferred)
