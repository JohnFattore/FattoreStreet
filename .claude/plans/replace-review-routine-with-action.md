# Replace the claude.ai review routine with a repo-tracked GitHub Action

## Context

PR review is currently driven by a Claude Code **routine** (claude.ai/code/routines) with a
GitHub trigger. `.github/workflows/claude-pr-review.yml` (242 lines) exists only to scrape the
routine's PR comment for a `<!-- claude-routine-review: <sha> -->` marker and a `Verdict:` line,
and turn that into a `Claude Review Verdict` check run.

It misfired badly on the `linters/00`-`03` stack. Across PRs #135-#138 the routine posted **20
comments where 4 were intended**:

| PR | Comments | Parseable marker | HTML-escaped marker | No marker |
|---|---|---|---|---|
| 135 | 5 | 3 | 2 | 0 |
| 136 | 6 | 3 | 2 | 1 |
| 137 | 5 | 3 | 1 | 1 |
| 138 | 4 | 3 | 1 | 0 |

Three causes, none fixable from this repo:

1. **The routine POSTs instead of PATCHes.** It revises a review by adding a new comment. PR #138
   got three consecutive *valid* comments for one SHA, so this happens even when nothing is
   malformed. It also apologises in-band, costing more comments ("(Second correction, sorry for
   the noise...)" on #136).
2. **HTML-escaped markers** (`&lt;!-- claude-routine-review:`), 6 of 20. The workflow's `contains()`
   guard only matched the raw form, skipped those runs, and the routine reposted because its
   verdict never landed. Our own workflow was feeding the loop.
3. **Unsubstituted placeholder.** #137 has a comment whose body literally starts `REPLACE_MARKER`.

Per the routines docs, a routine's prompt and triggers live in a claude.ai dashboard, not the repo,
and *"Each matching GitHub event starts its own session. Session reuse across events is not
available."* There is no dedupe and no cancel-superseded. The fix has to be a move, not a patch.

**Outcome:** one PR review comment, edited in place, defined in a file that can be diffed and
reverted, on the same subscription billing as today.

## Decisions

- **Trigger:** `opened`, `ready_for_review`, `synchronize`. Reviews follow the PR as it evolves;
  `cancel-in-progress` keeps a burst of pushes to roughly one review.
- **Advisory only.** The job succeeds whatever the review finds. `main` has **no branch protection**
  (`gh api .../branches/main/protection` → 404), so the current verdict check already gates nothing;
  there is no gating to preserve. This deletes the marker-parsing, check-seeding and
  verdict-publishing apparatus entirely.

## Prerequisites (user-side, not part of this change)

1. Run `claude setup-token` locally (interactive; `! claude setup-token`).
2. Add the output as repo secret **`CLAUDE_CODE_OAUTH_TOKEN`**. The repo currently has *no* Actions
   secrets. Without it the new job fails on its first run.
3. Disable the routine's GitHub trigger at claude.ai/code/routines so both don't fire.

## Changes

### 1. Preserve the in-flight work, then delete the old workflow

`.github/workflows/claude-pr-review.yml` has uncommitted edits from this session (accepting escaped
markers, plus a `Collapse superseded review comments` step). Commit that to its own branch
`claude-review/collapse-duplicates` first so it's recoverable if the routine is kept after all, then
delete the file on the new branch.

### 2. Add `.github/workflows/claude-code-review.yml`

```yaml
name: Claude Code Review

# Replaces the claude.ai routine that used to drive PR review. The routine
# posted a fresh comment every time it revised a review instead of editing the
# one it had already posted, so PRs #135-#138 collected 4-6 reviews each. That
# was not fixable from here: a routine's prompt and triggers live in a
# claude.ai dashboard, and each matching GitHub event starts its own cloud
# session with no dedupe and no way to cancel a superseded run.
#
# Running the review as an action puts the three missing controls in this file:
#   use_sticky_comment  -> one comment per PR, edited in place on every re-run
#   cancel-in-progress  -> a push landing mid-review cancels the stale run
#   --max-turns         -> caps the self-correction loop that caused the spam
#
# Auth is a subscription-backed OAuth token (claude setup-token), so reviews
# draw down the same Pro/Max allowance the routine did, not API credits.
#
# Advisory only: the job succeeds whatever the review finds, and nothing here
# gates a merge.
#
# Deliberately NOT filtered to `branches: [main]`. ci.yml and docker-build.yml
# are, which is why neither ran on PR #138 while it was still based on
# linters/02-werror. A review is worth having on stacked PRs too.

on:
  pull_request:
    types: [opened, ready_for_review, synchronize]

concurrency:
  group: claude-review-${{ github.event.pull_request.number }}
  cancel-in-progress: true

permissions:
  contents: read
  pull-requests: write

jobs:
  review:
    name: Claude review
    if: ${{ !github.event.pull_request.draft }}
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      # Full history so the review can diff against the base branch, and so the
      # repo's own .claude/ skills and rules are on disk for the session.
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: anthropics/claude-code-action@v1
        with:
          claude_code_oauth_token: ${{ secrets.CLAUDE_CODE_OAUTH_TOKEN }}
          github_token: ${{ github.token }}
          use_sticky_comment: true
          prompt: |
            Review the changes in this pull request against its base branch.

            Follow the checklist in .claude/skills/code-review/SKILL.md and the
            conventions in .claude/rules/. Each rule's `paths:` frontmatter says
            which files it governs; rules without frontmatter always apply.

            Post a single PR comment with the findings grouped by severity
            (Critical, Suggestion, Nit), each citing file:line. Lead with a
            one-line tally. If nothing is worth raising, say so in one line
            instead of padding the comment.
          claude_args: "--max-turns 8"
```

Note the prompt names the skill **by path** rather than passing `prompt: "/code-review"`. The repo
skill `.claude/skills/code-review/SKILL.md` shares a name with the built-in `/code-review` command,
and the built-in is marked `disable-model-invocation`; naming the path avoids the ambiguity.

### 3. Update `CLAUDE.md`

The CI paragraph documents `ci.yml` and `docker-build.yml` but never mentioned the review workflow.
Add one sentence recording that `claude-code-review.yml` posts a single advisory sticky comment per
PR, is not a merge gate, and needs the `CLAUDE_CODE_OAUTH_TOKEN` secret.

### 4. Commit the plan file

Per project practice, `.claude/plans/shimmying-humming-puppy.md` goes in the PR rather than staying
scratch.

## Verification

1. `uv run --with pyyaml python -c "import yaml; yaml.safe_load(open('.github/workflows/claude-code-review.yml'))"`
   parses clean. (Used this on the current workflow already; `python3` has no `yaml` module, `uv run` does.)
2. Confirm the secret exists: `gh secret list -R JohnFattore/FattoreStreet` should list
   `CLAUDE_CODE_OAUTH_TOKEN`. Today it lists nothing.
3. Open the PR for this branch. Because it triggers on `pull_request`, the new workflow runs from
   the PR's own copy, so the change tests itself. Watch for: exactly **one** comment from
   `github-actions[bot]`, job conclusion success, and the old `Claude Review Verdict` context absent
   from `gh pr checks`.
4. Push a second commit to the same PR. The sticky comment should be **edited in place**, not
   duplicated, and `gh run list` should show the first review cancelled if the push lands while it
   is still running.
5. Confirm the routine is off: no second, non-sticky comment carrying a `claude-routine-review`
   marker appears.

## Out of scope

- PR #138's own review findings (the 5 of 19 REDOS-flagged patterns in
  `DividendDeclarationTupleExtractor` and `EtfDateExtractor` that don't route through
  `BoundedRegexInput`). Tracked separately on that PR.
- Turning on branch protection for `main`. Worth doing, but it's a separate decision, and the
  advisory-only design above doesn't depend on it.
