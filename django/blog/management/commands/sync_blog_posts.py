"""Import the Markdown posts shipped inside this app into blog ``Post`` rows.

Two directories, two kinds of post: ``journal/`` holds the author's own writing,
``learning-topics/`` holds the study topics the daily routine opens a PR for.
Both ship inside the image (see ``django/.dockerignore``), so the files are the
source of truth and ``deploy/deploy.sh`` runs this command on every deploy.

Matching is by slug, so re-running updates in place instead of duplicating. The
command only ever creates and updates: it never deletes a post, never clears or
moves ``published_at``, and never touches a post with no file behind it.
"""

import re
from dataclasses import dataclass
from datetime import datetime, time
from pathlib import Path

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

# blog/management/commands/<this file> -> blog/. Resolving against the package
# rather than a setting means the same code works in the container, in a dev
# checkout, and in CI with nothing to configure.
BLOG_APP_DIR = Path(__file__).resolve().parents[2]
LEARNING_TOPIC_CATEGORY = "LLM Notebook"

# Learning topic filenames lead with their GitHub issue number
# (111_THE_DJANGO_SPRING_BOOT_JWT_TRUST_BOUNDARY.md); that identifies the
# source, not the post, so it is stripped before the slug reaches a public URL.
ISSUE_NUMBER_PREFIX = re.compile(r"^\d+_")

# Every post carries a version stamp on the line below its title, written by the
# blog-editor skill:
#   _FattoreStreet @ [`f337c3fe`](https://github.com/.../tree/f337c3fe...) - 2026-07-19_
# Its date is the post's vintage, which is what gets published rather than the
# moment the deploy happened to run.
METADATA_LINE = re.compile(r"^_.*_$")
VERSION_STAMP_DATE = re.compile(r"^_.*?(\d{4}-\d{2}-\d{2})_$")


class FrontMatterError(Exception):
    """Raised when a Markdown file's front matter cannot be parsed."""


@dataclass(frozen=True)
class PostSource:
    """A directory of posts and the rules that apply to everything in it."""

    directory: Path
    category: str | None = None
    strip_issue_number: bool = False


@dataclass(frozen=True)
class ParsedPost:
    """One Markdown file, parsed and matched to the post it will write."""

    path: Path
    source: PostSource
    front_matter: dict[str, str]
    body: str
    slug: str


def default_sources() -> list[PostSource]:
    """The two directories of posts that ship inside this app."""
    return [
        PostSource(directory=BLOG_APP_DIR / "journal"),
        PostSource(
            directory=BLOG_APP_DIR / "learning-topics",
            category=LEARNING_TOPIC_CATEGORY,
            strip_issue_number=True,
        ),
    ]


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
    """Parse a date or datetime string into an aware datetime."""
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


def header_lines(body: str) -> list[str]:
    """Return the body's header block -- everything above the first section."""
    header = []
    for line in body.splitlines():
        if line.strip().startswith("## "):
            break
        header.append(line)
    return header


def derive_slug(path: Path, strip_issue_number: bool) -> str:
    """Build a slug from the filename, dropping any leading issue number."""
    stem = path.stem
    if strip_issue_number:
        stem = ISSUE_NUMBER_PREFIX.sub("", stem)
    return slugify(stem.replace("_", "-"))


def derive_title(body: str, path: Path) -> str:
    """Fall back to the body's first heading, then the filename, for a title."""
    for line in body.splitlines():
        stripped = line.strip()
        if stripped.startswith("# "):
            return stripped[2:].strip()
    return path.stem.replace("_", " ").replace("-", " ").title()


def derive_excerpt(body: str) -> str:
    """Use the first real paragraph as the excerpt.

    Skips the header block -- the title, the version stamp, and a learning
    topic's source line -- so a post's blurb reads as prose, not metadata.
    """
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or METADATA_LINE.match(stripped):
            continue
        return stripped[:500]
    return ""


def derive_stamp_date(body: str) -> str | None:
    """Read the publication date out of the post's version stamp."""
    for line in header_lines(body):
        match = VERSION_STAMP_DATE.match(line.strip())
        if match:
            return match.group(1)
    return None


class Command(BaseCommand):
    help = (
        "Import the Markdown posts in blog/journal/ and blog/learning-topics/ "
        "into the database."
    )

    def add_arguments(self, parser) -> None:
        parser.add_argument(
            "--path",
            default=None,
            help=(
                "Import this single directory instead of the app's own "
                "journal/ and learning-topics/ directories."
            ),
        )
        parser.add_argument(
            "--category",
            default=None,
            help="Category applied to every post in a --path import.",
        )
        parser.add_argument(
            "--publish",
            action="store_true",
            help=(
                "Publish posts that have neither a published_at front matter "
                "key nor a version stamp to take a date from."
            ),
        )
        parser.add_argument(
            "--dry-run",
            action="store_true",
            help="Report what would change without writing to the database.",
        )

    def handle(self, *args, **options) -> None:
        sources = self.resolve_sources(options["path"], options["category"])

        parsed: list[ParsedPost] = []
        failed = 0
        total = 0

        for source in sources:
            if not source.directory.is_dir():
                raise CommandError(
                    f"blog posts directory not found: {source.directory}"
                )
            for path in sorted(source.directory.glob("*.md")):
                total += 1
                try:
                    parsed.append(self.parse_post(path, source))
                except FrontMatterError as exc:
                    failed += 1
                    self.stderr.write(self.style.ERROR(f"{path.name}: {exc}"))

        if not total:
            self.stdout.write(self.style.WARNING("no Markdown files found"))
            return

        # Two files landing on one slug would silently overwrite each other, so
        # this is checked across every source before anything is written.
        self.reject_duplicate_slugs(parsed)

        created = 0
        updated = 0

        for post in parsed:
            with transaction.atomic():
                was_created = self.sync_post(post, publish=options["publish"])
                if options["dry_run"]:
                    transaction.set_rollback(True)

            # The slug is what decides create-vs-update, so print it: a --dry-run
            # shows whether a file maps onto the post you expect.
            if was_created:
                created += 1
                self.stdout.write(
                    self.style.SUCCESS(f"created {post.path.name} -> {post.slug}")
                )
            else:
                updated += 1
                self.stdout.write(f"updated {post.path.name} -> {post.slug}")

        prefix = "would sync" if options["dry_run"] else "synced"
        summary = f"{prefix} {total} file(s): {created} created, {updated} updated"
        if failed:
            summary = f"{summary}, {failed} failed"
        self.stdout.write(self.style.SUCCESS(summary))

        if failed:
            raise CommandError(f"{failed} file(s) could not be imported")

    def resolve_sources(
        self, path: str | None, category: str | None
    ) -> list[PostSource]:
        """Pick the directories to import: ``--path``, or the app's own two."""
        if path:
            return [PostSource(directory=Path(path), category=category)]
        return default_sources()

    def reject_duplicate_slugs(self, parsed: list[ParsedPost]) -> None:
        """Fail before writing if two files resolve to the same slug."""
        seen: dict[str, Path] = {}
        for post in parsed:
            if post.slug in seen:
                raise CommandError(
                    f"duplicate slug {post.slug!r}: "
                    f"{seen[post.slug].name} and {post.path.name}"
                )
            seen[post.slug] = post.path

    def parse_post(self, path: Path, source: PostSource) -> ParsedPost:
        """Read one Markdown file and work out which post it maps onto."""
        front_matter, body = split_front_matter(path.read_text(encoding="utf-8"))

        unknown = set(front_matter) - SCALAR_FIELDS - LIST_FIELDS
        if unknown:
            raise FrontMatterError(
                f"unsupported front matter key(s): {', '.join(sorted(unknown))}"
            )

        if not body.strip():
            raise FrontMatterError("post body is empty")

        slug = front_matter.get("slug") or derive_slug(path, source.strip_issue_number)
        return ParsedPost(
            path=path,
            source=source,
            front_matter=front_matter,
            body=body,
            slug=slug,
        )

    def sync_post(self, parsed: ParsedPost, publish: bool) -> bool:
        """Upsert one parsed file into a ``Post``. Returns whether it was new."""
        front_matter = parsed.front_matter
        body = parsed.body

        post = Post.objects.filter(slug=parsed.slug).first()
        is_created = post is None
        if post is None:
            post = Post(slug=parsed.slug)

        post.title = front_matter.get("title") or derive_title(body, parsed.path)
        post.excerpt = front_matter.get("excerpt") or derive_excerpt(body)
        post.body_markdown = body
        post.cover_image_url = front_matter.get("cover_image_url", post.cover_image_url)

        # published_at is only ever set, never cleared or moved: a post already
        # live keeps the date it went out on, and re-importing its Markdown
        # never unpublishes it.
        if front_matter.get("published_at"):
            post.published_at = parse_published_at(front_matter["published_at"])
        elif post.published_at is None:
            stamp_date = derive_stamp_date(body)
            if stamp_date:
                post.published_at = parse_published_at(stamp_date)
            elif publish:
                post.published_at = timezone.now()

        post.save()
        self.set_taxonomy(post, front_matter, parsed.source.category)
        return is_created

    def set_taxonomy(
        self, post: Post, front_matter: dict[str, str], implied_category: str | None
    ) -> None:
        """Apply the file's categories and tags, plus the directory's category."""
        if "categories" in front_matter:
            post.categories.set(
                self.get_or_create_terms(Category, front_matter["categories"])
            )
        if implied_category:
            # Added, not set: a category applied by hand in the admin survives
            # the next deploy.
            post.categories.add(*self.get_or_create_terms(Category, implied_category))
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
