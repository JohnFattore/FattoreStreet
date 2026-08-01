# Quickstart: validating the LLM Notebook publish path

**Date**: 2026-08-01 | **Plan**: [plan.md](./plan.md)

Five checks, in order. The first four run locally; the fifth is the production pre-flight and is **mandatory before the automated deploy step ever runs unattended** — it is the only thing standing between a slug mistake and duplicated live posts.

## Prerequisites

```bash
cd django && uv sync
```

## 1. The command reads both directories

```bash
cd django
uv run python manage.py sync_blog_posts --dry-run
```

Expect twenty lines and a summary. Against an empty dev database everything is a creation:

```text
created CLAUDE_CODE.md -> claude-code
…
created 111_THE_DJANGO_SPRING_BOOT_JWT_TRUST_BOUNDARY.md -> the-django-spring-boot-jwt-trust-boundary
…
would sync 20 file(s): 20 created, 0 updated
```

Check the right-hand slugs, not just the count. `index-funds-rock` and `json-web-tokens` must appear — if you see `index-funds-101` or `json-web-token`, the front matter overrides from Phase B are missing and production would gain duplicates.

## 2. Content lands correctly

```bash
uv run python manage.py sync_blog_posts
uv run python manage.py shell -c "
from blog.models import Post
p = Post.objects.get(slug='the-django-spring-boot-jwt-trust-boundary')
print('categories:', [c.name for c in p.categories.all()])
print('published:', p.published_at)
print('excerpt:', p.excerpt[:80])
"
```

Expect:

- `categories: ['LLM Notebook']` — the directory-implied category (FR-002)
- `published: 2026-07-19 00:00:00+00:00` — from the version stamp, **not** today (R-004)
- `excerpt:` the Overview paragraph, **not** `_FattoreStreet @ …_` (R-005)

## 3. It is idempotent

```bash
uv run python manage.py sync_blog_posts --dry-run
```

Expect `would sync 20 file(s): 0 created, 20 updated` and no row actually changing. This is FR-009 and SC-004. If `published_at` shifts between runs, the date derivation has fallen back to `timezone.now()`.

## 4. Tests

```bash
uv run python manage.py test tests.test_blog
```

Covers the guarantees that protect the live site: file-less posts untouched, re-import does not unpublish or re-date, learning topics categorised, admin-added categories survive, duplicate slugs error before writing.

## 5. The files are actually in the image

The `.dockerignore` fix fails silently — the build succeeds and the container just has no posts. Check inside a built image, never by reading the file:

```bash
docker build -t fs-django-check django/
docker run --rm --entrypoint sh fs-django-check -c \
  'ls blog/journal/*.md blog/learning-topics/*.md | wc -l'
```

Expect `20`. A `0` means the negation lines are missing or wrong.

## 6. Production pre-flight — do this before the deploy step goes live

Run against the production database from the host, in dry-run:

```sh
cd /home/ec2-user/FattoreStreet/deploy
sudo docker compose run --rm django python manage.py sync_blog_posts --dry-run
```

**Expected**, and worth reading line by line:

```text
would sync 20 file(s): 13 created, 7 updated
```

- **7 updated** — the journal posts, each landing on its existing live post
- **13 created** — the learning topics, none of which exist yet
- **No mention of `react`** — the live post with no file stays untouched (FR-011)

Any *other* creation among the journal seven means a slug does not match what is live, and running for real would duplicate that post. Stop and fix the front matter.

Two changes are expected and intentional, but confirm they are wanted before proceeding:

- Every live post gains an `excerpt` for the first time — all eight are empty today.
- `INDEX_FUNDS_101.md` retitles its live post from **"Index Funds Rock!"** to **"Index Funds 101"**. To keep the live title instead, add `title: Index Funds Rock!` to that file's front matter — a content edit, not a code change.

## 7. End to end

Merge to main and let the deploy run. Then:

```bash
curl -s "https://fattorestreet.com/django/blog/api/posts/?category=llm-notebook&page_size=50" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['count'])"
```

Expect `13` (SC-001), and `21` total across the unfiltered list with no duplicates (SC-002). The deploy output in the GitHub Actions log should show the `synced 20 file(s)` summary between the migrate step and the health check.
