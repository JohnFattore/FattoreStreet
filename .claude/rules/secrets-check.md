# Secrets Check Before Push

CI runs `pre-commit run detect-secrets --all-files` and blocks merge if it fails. The hook fails not only on new secrets but also when `.secrets.baseline` is stale (line numbers of already-baselined entries shift when nearby lines are added/removed).

## Before every push (or as part of creating a PR)

1. Run the hook locally: `uvx pre-commit run detect-secrets --all-files`
2. If it modified `.secrets.baseline`:
   - Inspect `git diff .secrets.baseline`.
   - **Only line-number / `generated_at` churn on existing entries**: stage the baseline and include it in the commit being pushed.
   - **New entries appeared**: STOP. A potential real secret is in the diff. Do not commit the baseline to silence it; remove the secret from the code (env var, Secrets Manager) or, if it is provably a non-secret (test fixture, placeholder), tell the user and let them decide before baselining it.
3. Re-run the hook and confirm it passes before pushing.

## Never

- Never commit a real credential and baseline it.
- Never delete or hand-edit `.secrets.baseline` to make CI pass; only the hook itself should regenerate it.
