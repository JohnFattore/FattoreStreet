# Remove Django dead code (repo review item 3)

## Context

The repo review flagged a layer of dead code in `django/`: a leftover test-email endpoint exposed to any authenticated user, commented-out blocks in `settings.py`, a committed Sass error artifact, and stale local directories. None of it is load-bearing — every removal below was verified to have no remaining references.

## Verified facts

- `SendEmailAPIView` (`django/users/views.py`) sends a hardcoded "This is a test email" from `your-email@gmail.com` to `recipient@example.com`. Referenced only by `django/users/urls.py:11` and a row in `docs/API_REFERENCE.md:21` (which mislabels it "password reset"). No frontend code, no tests call it.
- After its removal, `EMAIL_BACKEND` in `settings.py:198` is the only remaining email-related config and nothing else in the codebase sends mail → also dead.
- `django/src/styles/custom.css` is a **committed Sass error message** (`Errno::ENOENT ... custom.scss` + Ruby backtrace) — created by running sass from the wrong directory. The real `custom.css` is generated into `react-app/src/styles/` (gitignored) by `nginx/Dockerfile` and `deploy/build.sh`. Nothing references `django/src/`.
- `django/venv/` (22MB) and `django/indexes/` (only `__pycache__`, zero source files) are untracked/ignored local cruft — deleted locally, not part of the commit.

## Changes

### 1. `django/users/views.py`
- Delete `SendEmailAPIView` and its now-unused imports (`send_mail`, `APIView`, `Response`, `response`).
- Delete the stale header comment referencing the nonexistent `retiredViews` file.

### 2. `django/users/urls.py`
- Remove the `api/send-email/` path.

### 3. `django/mysite/settings.py`
- Remove the commented-out `#DEBUG = env("DEBUG")` line (line 13).
- Remove the triple-quoted dead `CACHES` block inside the `if DEBUG:` branch (~lines 122–133), keeping the live DummyCache/Redis logic.
- Remove the "these should be env variables" self-note above `DATABASES`.
- Remove the commented-out `SIMPLE_JWT` block and its "default is 5 minutes and 1 day" comment (~lines 196–200).
- Remove `EMAIL_BACKEND = 'django.core.mail.backends.console.EmailBackend'` (dead once the email view is gone).

### 4. `docs/API_REFERENCE.md`
- Remove the `POST /users/api/send-email/` row (per auto-update-docs rule; `django/README.md` has no mention, verified).

### 5. `git rm -r django/src/`
- Removes the committed Sass error artifact (`django/src/styles/custom.css`).

### 6. Local-only cleanup (not committed)
- `rm -rf django/venv django/indexes` — untracked; nothing to commit.

## Not in scope

- `settings.py` hardening (DEBUG default, env-driven `ALLOWED_HOSTS`, `DATABASE` fallback) — that's review item 5, behavior-changing, separate PR.
- React dead code (`customHooks.tsx`) — belongs with the item-2 RTK Query migration.

## Verification

1. `cd django && uv run python manage.py test` — full suite must pass (currently 1,073 lines of tests across 10 files; none touch send-email, so no test updates needed per auto-update-tests rule).
2. `uv run python manage.py check` — confirms settings still parse and URLs resolve.
3. `grep -rn "send-email\|send_mail\|SendEmailAPIView" django/ docs/ react-app/src` — zero hits after the change.

## Delivery

Branch `remove-django-dead-code` off `origin/main` (main worktree is checked out elsewhere, so branch from `origin/main` directly), commit, push, PR against `main` — same flow as PRs #72/#73.
