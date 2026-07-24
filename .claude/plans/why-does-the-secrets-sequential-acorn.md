# Delete the dead portfolio templates, triage the detect-secrets baseline to zero, drop the pre-push ritual

## Context

The `secrets` CI job (`.github/workflows/ci.yml:104-119`) runs `pre-commit run detect-secrets --all-files` and blocks merge. It has never caught a real credential: all 9 entries in `.secrets.baseline` are false positives (local dev defaults, Bootstrap CDN SRI hashes, test fixtures, an env-var reference, and two expired JWT fixtures).

The cost is not the scanner, it is the untriaged baseline. Every entry carries a `line_number`, and every entry has `is_secret: null`, meaning nothing was ever adjudicated. When unrelated edits shift those lines, the hook rewrites the file and exits 3, failing CI until the baseline is re-committed. Git history shows at least four commits that are pure churn (`aeffada0`, `594c3ae7`, `e5f9ebcb`, `3afddc27`), and `.claude/rules/secrets-check.md` exists solely to document the workaround ritual.

The decision was to keep the scanner (it is the backstop against a real AWS key ever getting committed) but eliminate the recurring tax.

**Why this works.** `detect_secrets/pre_commit_hook.py::should_update_baseline` only rewrites the baseline when the trimmed results differ from the original. With `results` empty there is nothing to trim, so the hook returns 0 and never touches the file. Detection is unaffected: any new finding still lands in `new_secrets`, prints diagnostics, and returns 1. Marking false positives with `pragma: allowlist secret` is the mitigation detect-secrets itself recommends in its own error output.

Three of the nine findings live in `django/portfolio/templates/`, a directory that nothing serves. Those files are deleted rather than annotated, which drops the work to six pragmas.

**Intended outcome:** baseline `results` becomes `{}`, `uvx pre-commit run detect-secrets --all-files` is idempotent and leaves the working tree clean, and no one has to think about the baseline before pushing again.

## Changes

### 1. Delete `django/portfolio/templates/`

All 12 files in `django/portfolio/templates/portfolio/` are dead code left over from the pre-React era, when the portfolio app rendered server-side pages. Delete the whole `templates/` directory under `django/portfolio/`.

Evidence it is unreachable:

- `portfolio/urls.py` maps only `api/...` routes to DRF views; `portfolio/views.py` contains only `generics.*` and `APIView` subclasses, with no `render`, `template_name`, or `TemplateView`.
- The single template render in the entire `django/` tree is `entertainment/views.py:19`, which targets `entertainment/recommendation_list.html`. That file extends `admin/base_site.html` (Django's built-in admin template), so it does not reach into `portfolio/`.
- The only inbound reference to any of these files is `portfolio/sell.html:1` (`{% extends "portfolio/base.html" %}`), which is inside the set being deleted.
- No test, README, or doc references them. `tests/test_entertainment.py:19` asserts on the entertainment template only, and `docs/blog-posts/DJANGO_MTV.md` is about the entertainment app.
- Nothing is orphaned by the removal: there is no `static/` directory anywhere under `django/`, and no `forms.py` in `portfolio/`.

`TEMPLATES` in `mysite/settings.py:58-72` uses `APP_DIRS: True` with an empty `DIRS`, so removing the directory needs no settings change. The entertainment app keeps working because its own `templates/` directory is untouched.

This removes the `base.html`, `login.html`, and `register.html` findings (Bootstrap CDN SRI hashes) outright.

### 2. Add allowlist pragmas to the remaining 6 findings

The pragma regex is built in `detect_secrets/filters/allowlist.py::get_allowlist_regexes`. Two forms matter:

- **Inline**: comment appended to the end of the flagged line. Supported comment styles include `#` and `//`.
- **Nextline**: `pragma: allowlist nextline secret` on the line *above*. The regex is anchored with `^`, so the comment must be alone on its line.

| File | Line | Form |
|---|---|---|
| `django/mysite/settings.py` | 94 | inline `  # pragma: allowlist secret` |
| `django/tests/test_changeflow.py` | 71 | inline `  # pragma: allowlist secret` |
| `django/tests/test_users.py` | 22 | inline `  # pragma: allowlist secret` |
| `react-app/__tests__/mocks/handlers.ts` | 336, 338 | inline `  // pragma: allowlist secret` after the trailing comma |
| `springboot/src/main/resources/application.properties` | 21 | **nextline**, new comment line inserted above line 21 |

`application.properties` must use the nextline form. In Java properties files `#` only starts a comment at the beginning of a line, so a trailing pragma would be parsed as part of the value and the local dev fallback password would literally become `postgres # pragma: allowlist secret`. The result should read:

```properties
# pragma: allowlist nextline secret
spring.datasource.password=${POSTGRES_PASSWORD:postgres}
```

(Considered and rejected: adding `--exclude-lines` to the hook args in `.pre-commit-config.yaml`. The nextline pragma needs no config change, is scoped to exactly one line instead of a repo-wide regex, and cannot silently mask a future finding.)

### 3. Regenerate the baseline

Let the hook rewrite the file itself. Do not hand-edit it, per `.claude/rules/secrets-check.md`. After the pragmas are in place, run the hook and confirm `results` collapses to `{}`. Keep the file rather than dropping `--baseline`: it pins the plugin list and the `Base64HighEntropyString` / `HexHighEntropyString` entropy limits, and removing it would silently change scan behavior. All current plugin and filter settings are stock defaults.

### 4. Trim `.claude/rules/secrets-check.md`

The "Before every push" ritual (steps 1 to 3) is what this change makes obsolete. Reduce the rule to the parts that still carry weight:

- What to do when the hook reports a **new** finding: fix the source or add a pragma if provably a non-secret, and get the user's call before allowlisting anything ambiguous.
- The two "Never" items (never baseline a real credential, never hand-edit `.secrets.baseline`).
- A note that the baseline is intentionally empty and should stay that way, so a non-empty baseline is now a signal that something was skipped rather than business as usual.

`CLAUDE.md` needs no edit; its description of the CI job stays accurate. No user-facing behavior, API, or setup step changes, so per `.claude/rules/auto-update-docs.md` the app READMEs are out of scope.

## Verification

Run from the repo root unless noted.

1. **Hook passes and is idempotent** (this is the real test, since it proves the churn is gone):
   ```
   uvx pre-commit run detect-secrets --all-files    # expect Passed
   git status --porcelain .secrets.baseline          # expect empty
   uvx pre-commit run detect-secrets --all-files    # expect Passed, still no diff
   ```
   A second run that leaves the tree clean is the pass condition. Previously the hook would rewrite the file and print "The baseline file was updated."

2. **Baseline is actually empty**, not just quiet:
   ```
   python3 -c "import json; print(json.load(open('.secrets.baseline'))['results'])"   # expect {}
   ```

3. **Detection still works** (guards against the failure mode where the scanner is now a no-op). Temporarily append a line containing a syntactically valid dummy AWS key (`AKIA` followed by 16 uppercase alphanumerics) to a scanned file, confirm the hook exits non-zero and names the file, then revert. Do not commit this step.

4. **Nothing broke in the three services:**
   ```
   cd react-app && npm run format:check && npm run lint   # prettier keeps trailing // comments in place
   cd django && uv run python manage.py test
   cd springboot && mvn -B test                            # covers the properties-file parse
   ```
   Two of these carry the real risk. The django suite must still pass after the template deletion, in particular `tests/test_entertainment.py`, whose `assertTemplateUsed` proves app-directory template loading still resolves. And prettier landed recently (`f50f22e5`), so `format:check` confirms it leaves the two `handlers.ts` pragmas on their prettier-wrapped continuation lines rather than reflowing them.

5. **Django still boots and the entertainment page renders**, since template discovery is the one thing a unit test could plausibly miss:
   ```
   cd django && uv run python manage.py check
   uv run python manage.py runserver   # then load /entertainment/ and confirm 200
   ```

6. Commit the deletion, the pragmas, and the regenerated baseline together, and include this plan file in the PR.

## Observed, out of scope

- `SECRETS.md` holds a plaintext AWS access key pair in the working tree. It is gitignored (`.gitignore:63`), untracked, and absent from history, so nothing has leaked. Worth moving to a password manager, but unrelated to this change.
