# Secrets Check

CI runs `pre-commit run detect-secrets --all-files` and blocks merge if it fails.

`.secrets.baseline` is intentionally empty (`"results": {}`). Known false positives are marked at the source with an inline `# pragma: allowlist secret` comment instead of being listed in the baseline. Because the baseline holds no line numbers, the hook has nothing to keep up to date and no longer rewrites the file when unrelated edits shift lines. No pre-push baseline refresh is needed.

## When the hook reports a finding

A finding now means the scanner found something genuinely new, so do not reach for the baseline.

1. If it is a real credential, remove it from the code (env var, AWS Secrets Manager) and rotate it.
2. If it is provably a non-secret (test fixture, placeholder, env-var reference), add a pragma comment on that line and re-run the hook.
3. If it is ambiguous, stop and ask the user before allowlisting it.

Pragma forms, per `detect_secrets/filters/allowlist.py`:

- **Inline**, appended to the flagged line: `# pragma: allowlist secret` (also `//` for TS/Java, `<!-- ... -->` for HTML).
- **Nextline**, alone on the line above: `# pragma: allowlist nextline secret`. Use this where a trailing comment would corrupt the value, notably `.properties` files, where `#` only starts a comment at the beginning of a line.

Note that detect-secrets keys findings by `(type, secret_hash)` per file, so several identical secrets in one file surface one at a time. After adding a pragma, re-run until the output stops changing rather than assuming one pass is enough.

## Never

- Never commit a real credential and allowlist it.
- Never delete or hand-edit `.secrets.baseline` to make CI pass; only the hook itself should regenerate it.
- Never re-populate the baseline to silence a finding. A non-empty `results` map means a false positive was baselined instead of pragma'd, which reintroduces the line-number churn this setup removes.
