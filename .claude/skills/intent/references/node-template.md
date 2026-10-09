# Node template

Copy this for a new node and remove the HTML comments.

```markdown
# Market Data

## Why

<!-- Why this node exists, in terms of its parent's why: the failure it prevents. -->
<!-- 1–3 short paragraphs. Who depends on it, and what goes wrong without it. -->
<!-- A branch README also lists its children, each with a link and a one-line role. -->

## Requirements

### Raw prices are never modified after they're stored
Raw prices are the evidence adjustments are built from and checked against, and changing them would
destroy it. Adjusted values sit alongside them, never in their place.

### A failed fetch never removes data
Applies *Missing evidence is not evidence of absence* from [principles](../principles.md). If a
filing can't be fetched, the platform has failed to see an action, not proved it doesn't exist.

## Non-goals

- **Something it deliberately does not do.** Why not, so nobody adds it back.

## Open questions

- **Short label.** The question, with the options you can see. Which way is the code leaning today?
```

## Notes

- The heading is the requirement. The paragraph under it is the why.
- To point at a requirement elsewhere, use its name in italics and link to its file.
- `(inferred)` goes at the end of the sentence it qualifies.
