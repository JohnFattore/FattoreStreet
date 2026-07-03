Take unsubmitted work — uncommitted changes and/or local commits, possibly sitting on the wrong branch — and get it onto its own branch, committed, pushed, and opened as a PR. This command never merges.

## Steps

1. Survey the state:
   - `git status` — uncommitted/untracked changes
   - `git branch --show-current` — current branch
   - Default branch: `gh repo view --json defaultBranchRef -q .defaultBranchRef.name`
   - `git log --oneline origin/<default>..HEAD` — local commits not on the default branch's remote
   - `git diff` and `git diff --staged` — what the unsubmitted work actually is

   If there is nothing unsubmitted (clean tree, no unpushed commits), say so and stop.

2. Ensure the work is on its own branch:
   - **On the default branch with only uncommitted changes**: create a new branch with `git checkout -b <name>` (uncommitted changes carry over).
   - **On the default branch with local commits**: create the branch at HEAD (`git branch <name>`), then move the default branch back to the remote (`git checkout <default> && git reset --hard origin/<default> && git checkout <name>`). Confirm with the user before the reset and never do it if the default branch has diverged in a way the new branch doesn't capture.
   - **Already on a feature branch that matches the work**: stay on it.
   - **On a feature branch but the work is clearly unrelated to it**: create a new branch for it and point this out.

   Branch names are short kebab-case describing the change (e.g. `migrate-to-google-genai`, `docker-compose-deploy`).

3. Commit anything uncommitted: stage the relevant files and follow the /commit conventions (match `git log --oneline -10` style; first line under 72 chars; body explains why, not what). Leave unrelated files out — mention them instead of sweeping them in.

4. Push with `git push -u origin <branch>`.

5. Open the PR against the default branch with `gh pr create`:
   - Title matching the commit-style summary
   - Body with a `## Summary` (bulleted, why-focused) and a `## Test plan` (what was run, what remains)
   - If a PR already exists for the branch (`gh pr view` succeeds), the push updated it — report its URL instead of creating a new one.

6. Report the PR URL and stop.

## Hard rule: never merge

This command ends when the PR is open. Do not run `gh pr merge`, do not enable auto-merge, do not approve the PR — even if checks are green or the user previously merged similar PRs. Merging is a separate, human decision.
