# Blog

## Why

The vision calls the platform the author's public workspace: a place to write and to practise
building a real system in the open. The blog is where that writing is read. Without it, the
reasoning behind the platform and what the author learns while building it stay private, and the
"public workspace" half of the vision has nothing to show. (inferred)

Posts are Markdown files kept in the repo, not text typed into an admin screen. The deploy imports
them into the main API (Django) on every release, so the repo is the editor, the history and the
review step, and *Merging is deploying* in [principles](../principles.md) applies to writing as
much as to code. (inferred)

This branch has three parts:

- [Reading](reading.md): how visitors find and read posts.
- [Journal](journal.md): the author's own posts.
- [LLM Notebook](llm-notebook.md): study topics written by Claude, labelled as such.

## Non-goals

- **Comments or reactions.** Visitors read; they don't post. (inferred)
- **Writing in the browser.** Posts are written in the repo, so there is no online editor for
  them. (inferred)

## Open questions

- **Repo or admin, which wins?** A post can still be edited in the admin, but the next deploy
  overwrites it from its file. Should admin edits to file-backed posts be blocked, or is the file
  simply always the truth?
