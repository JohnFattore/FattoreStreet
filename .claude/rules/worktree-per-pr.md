# One Worktree per PR

Start every new PR in its own git worktree, branched from a fresh `origin/main`. Don't do it by switching branches in the main checkout. Do this without being asked. (This does not apply inside the `claude-code-action` review job itself.)

Several Claude sessions often share the main checkout. If one session commits with a broad `git add`, or switches or resets the branch, it sweeps up or moves another session's uncommitted work. That once put a Redis cache change inside an unrelated error-handling PR, which took a revert commit and a rebase to separate.

## How

1. Create the worktree as a sibling of the repo, named after the branch:

   ```bash
   git fetch origin
   git worktree add -b <branch> ../FattoreStreet-<branch> origin/main
   ```

2. Copy the git-ignored local env files from the main checkout. Without them, the pre-push hook's Django tests fail on a missing `SECRET_KEY`, and local Spring Boot runs lack their DB settings:

   ```bash
   cp django/mysite/.env ../FattoreStreet-<branch>/django/mysite/.env
   cp springboot/.env ../FattoreStreet-<branch>/springboot/.env
   ```

   Never export `SECRET_KEY` in the shell to get the hook past the Django tests. Spring Boot's `SecApiApplicationTests` pins its own test value and fails when an environment variable overrides it.

3. Do all edits, builds, commits and pushes in the worktree, using absolute paths (each Bash call starts back in the main checkout). Leave the main checkout's branch and working tree alone. Another session may be using them.

4. After the PR merges, and only once the user confirms or `gh pr view <n> --json state` says `MERGED`, clean up from the main checkout:

   ```bash
   git log --oneline origin/<branch>..<branch>   # must print nothing: no unpushed work
   git worktree remove ../FattoreStreet-<branch>
   git branch -D <branch>                        # only after `git merge-base --is-ancestor <branch> origin/main` passes
   ```

   `git branch -d` refuses here when the current branch isn't `main`, because it compares against `HEAD`. Check ancestry against `origin/main` instead, then use `-D`.

## Skip When

- The change goes onto an existing PR's branch that already has a worktree. Keep working in that worktree.
- The user explicitly asks to work in the main checkout.
