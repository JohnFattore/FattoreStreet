# Contract: `sync_blog_posts` management command

**Type**: Django management command (CLI). This is the feature's only external interface — no HTTP endpoint is added, and the existing public blog API (`/django/blog/api/posts/`) is unchanged; it simply starts serving more rows.

## Invocation

```bash
uv run python manage.py sync_blog_posts [--path DIR] [--publish] [--dry-run]
```

In production, via the deploy:

```sh
docker compose run --rm django python manage.py sync_blog_posts
```

## Arguments

| Flag | Default | Meaning |
|---|---|---|
| `--path DIR` | both built-in directories | Import a single directory instead. Posts in it get no directory-implied category unless `--category` is also given. For one-off imports and tests. |
| `--category NAME` | none | Category applied to every post in a `--path` import. Ignored without `--path`. |
| `--publish` | off | Publish posts that have neither `published_at` front matter nor a parseable version stamp. Posts that do keep their own date either way. |
| `--dry-run` | off | Report every change without writing. Rolls back per file. |

With no arguments the command reads both `django/blog/journal/` and `django/blog/learning-topics/`, resolved relative to the `blog` app package.

## Behaviour

**Per file**, in one transaction:

1. Parse optional front matter; unknown keys are an error.
2. Derive slug, title, excerpt, body, and `published_at` per [data-model.md](../data-model.md).
3. Match an existing `Post` on slug: update it in place, or create one.
4. Set front-matter categories and tags; add the directory-implied category without displacing existing ones.

**Across files**, before any write: two files resolving to the same slug is an error.

**Never**: deletes a post, clears `published_at`, reassigns `author`, or touches a post with no corresponding file.

## Output

One line per file, `<action> <filename> -> <slug>`, so a dry run shows exactly which post each file lands on:

```text
updated CLAUDE_CODE.md -> claude-code
created 111_THE_DJANGO_SPRING_BOOT_JWT_TRUST_BOUNDARY.md -> the-django-spring-boot-jwt-trust-boundary
```

Then a summary:

```text
synced 20 file(s): 13 created, 7 updated
```

Prefixed `would sync` under `--dry-run`. A trailing `, N failed` appears when any file failed.

## Exit codes

| Code | When |
|---|---|
| 0 | Every file imported, or `--dry-run` completed |
| non-zero | Any file failed to parse, a duplicate slug was found, or a source directory is missing |

A non-zero exit fails `deploy/deploy.sh` (`set -eu`), which fails the SSM command, which fails the GitHub Actions deploy job. This is the whole of FR-012's error handling — nothing additional is written.

## Guarantees

- **Idempotent** — re-running against unchanged files produces `0 created`, and no row changes.
- **Atomic per file** — a malformed file fails alone; files before it stay committed.
- **Safe to run against production by hand**, in any mode, at any time.
