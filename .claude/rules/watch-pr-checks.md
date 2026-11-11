# Watch PR Checks After Pushing

After opening a PR, or pushing new commits to a PR's branch, watch its CI and the Claude review through to the end and report back. Do this without being asked.

## How

1. Start a background watch right after the push, so the conversation isn't blocked:

   ```bash
   sleep 20; gh pr checks <number> --watch --interval 20 >/dev/null 2>&1; gh pr checks <number>
   ```

   Run it with `run_in_background`; you are notified when it exits. The short sleep gives GitHub time to register the new runs, otherwise `--watch` can exit on the previous push's results. Don't poll it in the foreground.

2. When it finishes, report:
   - **CI**: every check's result. For a failure, pull the failing step's log (`gh run view <run-id> --log-failed`) and say what broke, not just that it's red.
   - **Claude review**: read the sticky comment (`gh pr view <number> --json comments -q '.comments[-1].body'`; it is posted by `github-actions`, not a Claude bot account) and summarize its findings by severity. It is edited in place on every push, so confirm it is current: the "View job" run ID in its first line must match the run ID of the `Claude review` check.

3. Recommend what to do about each finding (fix, follow-up, or ignore, with a reason), then wait for the user.

## Never

- Never merge because checks went green. Merging still needs the user's say-so.
- Never push fixes for review findings without asking; the review is advisory (see `claude-code-review.yml` in `CLAUDE.md`).
