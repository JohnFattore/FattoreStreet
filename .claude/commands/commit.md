Generate a commit message and commit staged changes.

## Steps

1. Run `git diff --staged` to see what's being committed. If nothing is staged, run `git diff` and tell the user to stage files first.

2. Run `git log --oneline -10` to see recent commit style.

3. Write a commit message that:
   - Follows the existing commit message style from the log
   - First line: concise summary of what changed and why (under 72 chars)
   - If the change is non-trivial, add a blank line then a short body (1-3 lines) explaining context
   - Focus on the "why", not the "what" — the diff shows the what

4. Show the proposed message to the user and ask for approval before committing.

5. Once approved, create the commit with that message.
