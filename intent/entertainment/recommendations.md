# Recommendations

## Why

[Entertainment](README.md) exists to share the author's picks. That only works if visitors can read
them easily and nobody but the author can change them.

## Requirements

### Anyone can read the recommendations without signing in
A shared list behind a sign-in wall isn't shared. (inferred)

### Recommendations are grouped by kind
A reader looking for a book doesn't want to scroll past podcasts. The list is grouped into books,
films, shows, music, podcasts, games and websites, and a kind with no entries isn't shown.
(inferred)

### Each entry names what it is and who made it
A title alone is ambiguous: many films share a name. Each entry has a title and a creator (author,
director, band), with an optional year and a note on why it's recommended. (inferred)

### The same item can't be listed twice
A duplicate makes the list look careless. One kind, title and creator appear at most once.
(inferred)

### Only the author can change the list
Applies *Least privilege, no secrets in the repo* from [principles](../principles.md). There is no
public way to add, edit or remove a recommendation. (inferred)
