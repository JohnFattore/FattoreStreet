"""Import Markdown files in ``docs/blog-posts/`` into blog ``Post`` rows.

Markdown files are the source of truth for post content; this command upserts
them into the database by slug so the public blog API can serve them. Posts are
created unpublished unless the file's front matter sets ``published_at`` (or
``--publish`` is passed), so nothing reaches the site without an explicit
publish step.
"""

from datetime import datetime, time
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand, CommandError
from django.db import transaction
from django.utils import timezone
from django.utils.dateparse import parse_date, parse_datetime
from django.utils.text import slugify

from blog.models import Category, Post, Tag

FRONT_MATTER_FENCE = "---"
LIST_FIELDS = frozenset({"categories", "tags"})
SCALAR_FIELDS = frozenset(
    {"title", "slug", "excerpt", "cover_image_url", "published_at"}
)


class FrontMatterError(Exception):
    """Raised when a Markdown file's front matter cannot be parsed."""


def split_front_matter(text: str) -> tuple[dict[str, str], str]:
    """Split ``text`` into its front matter mapping and Markdown body.

    Front matter is an optional block of ``key: value`` lines fenced by ``---``
    at the very top of the file. Files without it return an empty mapping and
    the untouched body.
    """
    lines = text.splitlines()
    if not lines or lines[0].strip() != FRONT_MATTER_FENCE:
        return {}, text

    for index in range(1, len(lines)):
        if lines[index].strip() == FRONT_MATTER_FENCE:
            raw_front_matter = lines[1:index]
            body = "\n".join(lines[index + 1 :]).lstrip("\n")
            return parse_front_matter(raw_front_matter), body

    raise FrontMatterError("front matter opened with '---' but was never closed")


def parse_front_matter(lines: list[str]) -> dict[str, str]:
    """Parse ``key: value`` front matter lines into a mapping."""
    front_matter: dict[str, str] = {}
    for line in lines:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        key, separator, value = stripped.partition(":")
        if not separator:
            raise FrontMatterError(f"front matter line is not 'key: value': {line!r}")
        front_matter[key.strip().lower()] = value.strip()
    return front_matter


def parse_list_value(value: str) -> list[str]:
    """Parse a front matter list, written either ``[a, b]`` or ``a, b``."""
    trimmed = value.strip()
    if trimmed.startswith("[") and trimmed.endswith("]"):
        trimmed = trimmed[1:-1]
    return [item.strip() for item in trimmed.split(",") if item.strip()]


def parse_published_at(value: str) -> datetime:
    """Parse a front matter ``published_at`` into an aware datetime."""
    parsed = parse_datetime(value)
    if parsed is None:
        parsed_date = parse_date(value)
        if parsed_date is None:
            raise FrontMatterError(
                f"published_at is not a valid date or datetime: {value!r}"
            )
        parsed = datetime.combine(parsed_date, time.min)
    if timezone.is_naive(parsed):
        parsed = timezone.make_aware(parsed, timezone.get_default_timezone())
    return parsed


def derive_title(body: str, path: Path) -> str:
    """Fall back to the body's first heading, then the filename, for a title."""
    for line in body.splitlines():
        stripped = line.strip()
        if stripped.startswith("# "):
            return stripped[2:].strip()
    return path.stem.replace("_", " ").replace("-", " ").title()


def derive_excerpt(body: str) -> str:
    """Use the first non-heading paragraph of the body as the excerpt."""
    for line in body.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            return stripped[:500]
    return ""


class Command(BaseCommand):
    help = "Import Markdown blog posts from docs/blog-posts/ into the database."

    def add_arguments(self, parser) -> None:
        parser.add_argument(
            "--path",
            default=None,
            help=(
                "Directory of Markdown posts. Defaults to settings.BLOG_POSTS_DIR "
                "(docs/blog-posts/ in the repo checkout)."
            ),
        )
        parser.add_argument(
            "--publish",
            action="store_true",
            help="Publish newly created posts immediately instead of leaving drafts.",
        )
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Report what would change without writing to the database.",
        )

    def handle(self, *args, **options) -> None:
        directory = Path(options["path"] or settings.BLOG_POSTS_DIR)
        if not directory.is_dir():
            raise CommandError(f"blog posts directory not found: {directory}")

        paths = sorted(directory.glob("*.md"))
        if not paths:
            self.stdout.write(self.style.WARNING(f"no Markdown files in {directory}"))
            return

        created = 0
        updated = 0
        failed = 0

        for path in paths:
            try:
                with transaction.atomic():
                    was_created, slug = self.sync_post(
                        path, publish=options["publish"]
                    )
                    if options["dry_run"]:
                        transaction.set_rollback(True)
            except FrontMatterError as exc:
                failed += 1
                self.stderr.write(self.style.ERROR(f"{path.name}: {exc}"))
                continue

            # The slug is what decides create-vs-update, so print it: a --dry-run
            # shows whether a file maps onto the post you expect.
            if was_created:
                created += 1
                self.stdout.write(self.style.SUCCESS(f"created {path.name} -> {slug}"))
            else:
                updated += 1
                self.stdout.write(f"updated {path.name} -> {slug}")

        prefix = "would sync" if options["dry_run"] else "synced"
        summary = f"{prefix} {len(paths)} file(s): {created} created, {updated} updated"
        if failed:
            summary = f"{summary}, {failed} failed"
        self.stdout.write(self.style.SUCCESS(summary))

        if failed:
            raise CommandError(f"{failed} file(s) could not be imported")

    def sync_post(self, path: Path, publish: bool) -> tuple[bool, str]:
        """Upsert one Markdown file into a ``Post``.

        Returns whether the post was created and the slug it was matched on.
        """
        front_matter, body = split_front_matter(path.read_text(encoding="utf-8"))

        unknown = set(front_matter) - SCALAR_FIELDS - LIST_FIELDS
        if unknown:
            raise FrontMatterError(
                f"unsupported front matter key(s): {', '.join(sorted(unknown))}"
            )

        if not body.strip():
            raise FrontMatterError("post body is empty")

        title = front_matter.get("title") or derive_title(body, path)
        slug = front_matter.get("slug") or slugify(path.stem.replace("_", "-"))

        post = Post.objects.filter(slug=slug).first()
        is_created = post is None
        if post is None:
            post = Post(slug=slug)

        post.title = title
        post.excerpt = front_matter.get("excerpt") or derive_excerpt(body)
        post.body_markdown = body
        post.cover_image_url = front_matter.get("cover_image_url", post.cover_image_url)

        # published_at is only ever set here, never cleared: a post published in
        # the admin stays published when its Markdown source is re-imported.
        if "published_at" in front_matter and front_matter["published_at"]:
            post.published_at = parse_published_at(front_matter["published_at"])
        elif is_created and publish:
            post.published_at = timezone.now()

        post.save()
        self.set_taxonomy(post, front_matter)
        return is_created, slug

    def set_taxonomy(self, post: Post, front_matter: dict[str, str]) -> None:
        """Replace the post's categories and tags with the front matter's."""
        if "categories" in front_matter:
            post.categories.set(
                self.get_or_create_terms(Category, front_matter["categories"])
            )
        if "tags" in front_matter:
            post.tags.set(self.get_or_create_terms(Tag, front_matter["tags"]))

    def get_or_create_terms(self, model, raw_value: str) -> list:
        """Resolve a front matter list into ``Category`` or ``Tag`` instances."""
        terms = []
        for name in parse_list_value(raw_value):
            term, _ = model.objects.get_or_create(
                slug=slugify(name), defaults={"name": name}
            )
            terms.append(term)
        return terms
