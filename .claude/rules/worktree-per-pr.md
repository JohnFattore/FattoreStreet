# One Worktree per PR

Start every new PR in its own git worktree, branched from a fresh `origin/main`. Don't do it by switching branches in the main checkout. Do this without being asked. (This does not apply inside the `claude-code-action` review job itself.)

Several Claude sessions often share the main checkout. If one session commits with a broad `git add`, or switches or resets the branch, it sweeps up or moves another session's uncommitted work. That once put a Redis cache change inside an unrelated error-handling PR, which took a revert commit and a rebase to separate.

## How

1. Create the worktree as a sibling of the repo. `<dir>` is the branch name with any `/` replaced by `-` (`feature/x` → `FattoreStreet-feature-x`), so the worktree never lands in a nested path:

   ```bash
   # Run from the main checkout, where each Bash call starts
   main=$(git rev-parse --show-toplevel)
   wt="$(dirname "$main")/FattoreStreet-<dir>"
   git -C "$main" fetch origin
   git -C "$main" worktree add -b <branch> "$wt" origin/main
   ```

2. Copy the git-ignored local env files from the main checkout. Without them, the pre-push hook's Django tests fail on a missing `SECRET_KEY`, and local Spring Boot runs lack their DB settings:

   ```bash
   for f in django/mysite/.env springboot/.env; do
     if [ -f "$main/$f" ]; then cp "$main/$f" "$wt/$f"; else echo "no $f in the main checkout; skipped"; fi
   done
   ```

   A fresh clone may have neither file. Skip the missing ones, and tell the user which tests that leaves unable to run locally.

   Never export `SECRET_KEY` in the shell to get the hook past the Django tests. Spring Boot's `SecApiApplicationTests` pins its own test value and fails when an environment variable overrides it.

3. Do all edits, builds, commits and pushes in the worktree, using absolute paths (each Bash call starts back in the main checkout). Leave the main checkout's branch and working tree alone. Another session may be using them.

4. After the PR merges, and only once the user confirms or `gh pr view <n> --json state` says `MERGED`, clean up. Shell variables don't carry between Bash calls, so set `main` and `wt` again as in step 1:

   ```bash
   git -C "$main" fetch --prune origin
   # For a merge-commit PR, must print nothing: no commits that exist only locally (squash and
   # rebase merges are covered below). Uses --remotes because GitHub deletes origin/<branch>
   # on merge, and origin/<branch>..<branch> would then error.
   git -C "$main" log --oneline <branch> --not --remotes
   git -C "$main" worktree remove "$wt"
   git -C "$main" merge-base --is-ancestor <branch> origin/main && git -C "$main" branch -D <branch>
   ```

   The `&&` makes the ancestry check a gate rather than a comment: `-D` only runs if every commit on the branch is in `origin/main`. Plain `git branch -d` isn't enough, because it compares against the current `HEAD` and refuses whenever the main checkout isn't on `main`.

   This repo merges PRs with merge commits, so the ancestry check passes after a normal merge. A squash- or rebase-merged PR rewrites the commits, so the ancestry check fails even though the work landed, and the `--not --remotes` check lists the branch's original commits. Those are expected there. Instead, delete with `-D` only when the PR shows `MERGED` and its head is the local branch tip, which means every local commit was in the PR:

   ```bash
   [ "$(gh pr view <n> --json state -q .state)" = MERGED ] \
     && [ "$(gh pr view <n> --json headRefOid -q .headRefOid)" = "$(git -C "$main" rev-parse <branch>)" ] \
     && git -C "$main" branch -D <branch>
   ```

## Skip When

- The change goes onto an existing PR's branch that already has a worktree. Keep working in that worktree.
- The user explicitly asks to work in the main checkout.
