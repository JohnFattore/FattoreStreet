# Journal

## Why

The journal is the author's own writing, the core of the [blog](README.md). It has to be easy for
one person to publish and impossible to lose by accident.

## Requirements

### Merging a post publishes it
A separate publishing step is one more thing a single maintainer forgets. When a post reaches the
main branch, it goes live on the next deploy with no further action. (inferred)

### A post's date is the date it was written
Publishing everything with the date of whichever deploy happened to run would scramble the order
and misdate the writing. Each post carries the date it was written, and that's the date shown.
(inferred)

### Publishing never deletes or unpublishes a post
An import that removes whatever it didn't find would wipe posts after a bad deploy. Importing only
adds and updates, never removes a post, and never clears or moves an existing publish date.
(inferred)

### Importing the same posts twice changes nothing
Every deploy re-imports every post, so a repeat run updates in place rather than creating
duplicates. Two files that would claim the same address are an error, caught before anything is
written. (inferred)
