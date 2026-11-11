# Watch PR Checks After Pushing

In interactive sessions, after opening a PR or pushing new commits to a PR's branch, watch its CI and the Claude review through to the end and report back. Do this without being asked. (This does not apply inside the `claude-code-action` review job itself.)

## How

1. Start a background watch right after the push, so the conversation isn't blocked:

   ```bash
   pr=<number>
   sha=$(git rev-parse HEAD)  # full SHA; a short one never matches
   count() { c=$(gh pr checks "$pr" --json name -q length 2>/dev/null); echo "${c:-0}"; }

   found=0
   for _ in $(seq 60); do  # give up after ~10 minutes
     if [ "$(gh pr view "$pr" --json headRefOid -q .headRefOid)" = "$sha" ] && [ "$(count)" -gt 0 ]; then
       found=1; break
     fi
     sleep 10
   done
   [ "$found" = 1 ] || { echo "TIMED OUT waiting for checks on $sha"; exit 1; }

   sleep 15  # grace period for the other workflows to register
   # Re-watch while the set of checks keeps growing, so a workflow that
   # registered after a watch started is still waited on. `--watch` exits
   # non-zero when a check fails, so it is not chained with `&&`.
   seen=0
   while [ "$(count)" -gt "$seen" ]; do
     seen=$(count)
     gh pr checks "$pr" --watch --interval 20 >/dev/null 2>&1
   done
   gh pr checks "$pr"
   ```

   Run it with `run_in_background`; you are notified when it exits. Don't poll it in the foreground.

   The loop waits until the PR's head is the pushed commit and that commit has checks. Without it, `--watch` can start before GitHub registers the new runs and exit on the previous push's results or on "no checks reported". It counts whatever checks the PR actually gets rather than a fixed number, because that varies: `ci.yml` and `docker-build.yml` only run on PRs into `main`, so a PR stacked on another branch gets the Claude review alone. If no checks appear within the time limit, the script prints `TIMED OUT` and exits non-zero: report that, not stale results.

2. When it finishes, report:
   - **CI**: every check's result. For a failure, pull the failing step's log (`gh run view <run-id> --log-failed`) and say what broke, not just that it's red.
   - **Claude review**: read the sticky comment and summarize its findings by severity. It is posted by `github-actions`, not a Claude bot account, so select it by author rather than taking the PR's last comment, which may be someone else's:

     ```bash
     gh pr view <number> --json comments -q '[.comments[] | select(.author.login=="github-actions")] | last | .body'
     ```

     It is edited in place on every push, so confirm it is current: the "View job" run ID in its first line must match the run ID of the `Claude review` check. That check name is the job `name:` in `.github/workflows/claude-code-review.yml`; if the job is renamed, update it here too.

3. Recommend what to do about each finding (fix, follow-up, or ignore, with a reason), then wait for the user.

## Never

- Never merge because checks went green. Merging still needs the user's say-so.
- Never push fixes for review findings without asking; the review is advisory (see `claude-code-review.yml` in `CLAUDE.md`).
