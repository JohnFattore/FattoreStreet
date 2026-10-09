---
name: intent
description: Build and maintain the intent tree in intent/, the human-readable source of truth for why FattoreStreet exists and what each part must do. Drafts new nodes from the code, interviews the owner to confirm inferred content, syncs nodes with code changes, and audits the tree's structure and open questions. This skill should be used when the user asks to write, grow, restructure, review, or audit intent docs, add or change a requirement, or record why something exists.
---

# Intent

`intent/` is a tree of Markdown files that says **why** the app exists and **what** each part must
do. The code is the **how** and is derived from the tree. The tree replaces the old constitution,
specs and docs, so it has to be complete enough to build from and honest about what is unconfirmed.

It's meant to be read by people. Keep it loose: prose over process, no numbering, no bookkeeping.

## The tree

```
intent/
  README.md                   root: vision (who, problem, success, non-goals)
  design.md                   high-level design: how the product is shaped and why
  principles.md               constraints that apply everywhere
  <branch>/
    README.md                 branch node: why this area exists, branch-wide constraints
    <leaf>.md                 leaf node: one coherent slice of requirements
    <sub-branch>/
      README.md
      ...
```

- A **directory is a branch**. Its `README.md` is the node for that branch. Any other `.md` file is a
  leaf.
- **A node's "why" is stated in terms of its parent.** If a node's why can't point upward, it's in
  the wrong place, or the parent is missing a reason.
- **Constraints flow down.** A child inherits everything its ancestors require and rule out. It may
  narrow that but never loosen it. An exception is stated explicitly at the child, and it names what
  it is excepting.
- **The path is the context.** To work on any node, read every `README.md` from the root down to it
  first, plus `design.md` and `principles.md`. Don't load unrelated branches.
- **Split by reason, not by service.** A branch is a user-facing purpose, like market data or
  portfolio, and usually spans several services. Split a node when it holds two separate reasons to
  exist, or when it gets long enough that a reader loses the thread (roughly 10 requirements).

## Node shape

Every node follows `references/node-template.md`: `## Why`, `## Requirements`, `## Non-goals` and
`## Open questions`, in that order. Leave out any section that would be empty, except `## Why`,
which every node needs. The root and `principles.md` are the exceptions: the root's why is the
vision itself, and `principles.md` is a list of requirements.

A requirement is a `###` heading that states what must be true, followed by a short paragraph saying
why. Add a sentence on how you'd know it holds only when that isn't obvious. Requirements aren't
numbered. To refer to one from elsewhere, use its name and link to its file, e.g. "see
*Primary sources are the truth* in [principles](../principles.md)".

### design.md

`design.md` is the high-level design, distinct from the root's vision. It says how the product is
shaped: the parts, the services and stores that carry them, how the parts depend on each other, the
external sources and their license status, who can change what, and the key technical decisions,
each with its why. It keeps the `## Why` and `## Open questions` sections and may use any other
`##` sections it needs in between. A mermaid diagram is welcome where it helps. A change that alters
a design decision (a new service, a different data store, moving work between services) is a
**Sync** trigger for it.

## Writing rules

- **How much "how" depends on altitude.** A choice of technology is a decision with a reason, so
  `design.md` and branch READMEs may name services, major frameworks, data stores, external sources
  and the scheduled-job model, as long as each comes with why it was chosen and the failure that
  choice prevents. Leaves stay tech-free: a requirement is behavior that survives a rewrite. The
  exception is an external data source (the SEC, FRED, yfinance): a leaf may name one when its
  provenance or license status is the point of the requirement, since that survives a rewrite too.
- **Never, at any altitude:** file paths, class, table and column names, endpoints, env vars and
  tuning numbers. Numbers are fine when they *are* the requirement (a tolerance, a deadline), but
  not when they're tuning (a retry count). If a sentence would go stale on a refactor that keeps the
  same design, it belongs in the code.
- **Checkable.** The heading states something that could be shown true or false. "Prices load fast"
  is not a requirement. "Prices are current by the next market open" is.
- **The why names the failure it prevents.** "Because users need it" is not a why.
- **Short.** A heading and one to three sentences. If a requirement needs more, it's probably two.
- **Mark guesses.** Anything reconstructed from code or old docs rather than confirmed by the owner
  ends with `(inferred)`. Never present a guess as a decision.
- **Unknowns go in Open questions,** phrased as a question with the options you can see. Don't
  quietly pick one.

## Modes

Work out which mode the request is, then follow it. Several can run in one session, for example
grow followed by interview.

### Grow: draft a new node

1. Read the root-to-parent path and `principles.md`.
2. Read the code and any remaining old docs that implement this area, to recover the requirements and
   the reasons behind them. Comments, commit messages, rules files and READMEs often hold the why.
3. Draft the node from the template. Every sentence that came from code rather than from the owner is
   marked `(inferred)`. Add the new node to its parent's list of children.
4. Note contradictions as open questions rather than resolving them: code that violates a principle,
   two docs that disagree, a behavior with no apparent reason.
5. Run the validator. Report the new node, the number of `(inferred)` markers, and the open
   questions.

### Interview: confirm what was inferred

1. Collect the `(inferred)` markers and open questions in the node or branch the user names. Use the
   validator's summary to find them.
2. Ask about them a few at a time with AskUserQuestion, most consequential first. Offer the inferred
   text as the recommended option and plausible alternatives beside it.
3. Write each answer back right away: remove the marker, rewrite the text, or turn an open question
   into a requirement or non-goal. If an answer changes a principle or a parent's why, edit that node
   too.

### Sync: keep the tree and code in step

Use this when the change being made alters behavior: a feature, a fix that changes what users see,
or removing something.

1. Find the nodes the change touches by searching the tree for the area.
2. Decide in which direction it goes:
   - **Intent changed** (a new feature or a decision): edit or add the requirement first, then the
     code follows.
   - **The code is wrong** (it violates a requirement): the requirement stands. Say so; don't edit
     intent to match the bug.
   - **The code is right and intent is stale:** update intent, and tell the user that the tree had
     drifted.
3. Run the validator.

### Audit: check the tree's health

Run the validator with `--summary` and report in two groups:

- **Errors**: these must be fixed (a branch without a README, a broken link, a node with no why).
- **Confidence**: `(inferred)` and open-question counts per node, as the backlog for the interview
  mode.

Then read the tree for problems no script can catch: a "how" that crept in, a requirement whose why
doesn't name a failure, a child that loosens a parent. Suggest fixes, but only make them if the user
asks.

## Validator

```bash
python3 .claude/skills/intent/scripts/validate.py             # structure check, exits 1 on errors
python3 .claude/skills/intent/scripts/validate.py --summary   # plus per-node requirements / inferred / open questions
```

Run it after every edit to the tree. It uses only the standard library and checks the tree rooted
at `intent/` (`--root` to override).
