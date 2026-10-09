# Entertainment

## Why

Part of the author's public workspace in the vision is keeping personal recommendations. This branch
holds the non-food kind: books, films, shows, music, podcasts, games and websites the author thinks
are worth someone's time. Without it, those recommendations are scattered and can't be shared.

The list is kept in the main API (Django), edited through its admin screens and shown on a page
Django renders itself rather than in the web app. The author is the only writer and a built-in admin
needs no code to maintain, and the server-rendered page doubles as a working demonstration of
Django's built-in page templates, which fits the practice half of the public workspace.

This branch has one part:

- [Recommendations](recommendations.md): the author's curated list, readable by anyone.

## Non-goals

- **Ratings or reviews from visitors.** It's the author's list.
- **Pulling in outside catalogue data** such as cover art or scores, which would bring licensing
  questions for little gain.
- **A second page in the web app.** An unlinked web app page with a hard-coded album list, useful
  links and ratio definitions duplicated this branch. It goes; the ratio definitions move to
  [derived metrics](../fundamentals/derived-metrics.md).
