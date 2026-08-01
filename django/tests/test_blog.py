import tempfile
from io import StringIO
from pathlib import Path
from unittest.mock import patch

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase
from django.utils import timezone
from rest_framework import status

from blog.management.commands.sync_blog_posts import (
    LEARNING_TOPIC_CATEGORY,
    PostSource,
    default_sources,
)
from blog.models import Category, Post, Tag
from tests.base import BaseAPITestCase

# The header block every post carries, written by the blog-editor skill: title,
# version stamp, and (for a learning topic) the source issue.
STAMP = (
    "_FattoreStreet @ [`f337c3fe`]"
    "(https://github.com/JohnFattore/FattoreStreet/tree/f337c3fe) — 2026-07-19_"
)
SOURCE_LINE = "_Source: [#111](https://github.com/JohnFattore/FattoreStreet/issues/111)_"


class BlogPublicApiTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.now = timezone.now()

        self.category = Category.objects.create(name="Investing", slug="investing")
        self.tag = Tag.objects.create(name="Bogleheads", slug="bogleheads")

        self.public_post = Post.objects.create(
            title="Hello World",
            slug="hello-world",
            excerpt="Intro",
            body_markdown="# Hello",
            published_at=self.now,
        )
        self.public_post.categories.add(self.category)
        self.public_post.tags.add(self.tag)

        self.future_post = Post.objects.create(
            title="Future Post",
            slug="future-post",
            excerpt="Not yet",
            body_markdown="Soon",
            published_at=self.now + timezone.timedelta(days=2),
        )

        self.unpublished_post = Post.objects.create(
            title="Unpublished Post",
            slug="unpublished-post",
            excerpt="Draft",
            body_markdown="Nope",
            published_at=None,
        )

    def test_list_posts_is_public_and_filters_visibility(self):
        resp = self.client.get("/blog/api/posts/")
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        slugs = [item["slug"] for item in resp.data["results"]]
        self.assertIn(self.public_post.slug, slugs)
        self.assertNotIn(self.future_post.slug, slugs)
        self.assertNotIn(self.unpublished_post.slug, slugs)

    def test_detail_post_is_public_and_404s_when_not_public(self):
        resp = self.client.get(f"/blog/api/posts/{self.public_post.slug}/")
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        self.assertEqual(resp.data["slug"], self.public_post.slug)
        self.assertIn("body_markdown", resp.data)

        resp = self.client.get(f"/blog/api/posts/{self.future_post.slug}/")
        self.assertEqual(resp.status_code, status.HTTP_404_NOT_FOUND)

        resp = self.client.get(f"/blog/api/posts/{self.unpublished_post.slug}/")
        self.assertEqual(resp.status_code, status.HTTP_404_NOT_FOUND)

    def test_list_posts_filters_by_category_and_tag(self):
        resp = self.client.get("/blog/api/posts/?category=investing")
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        slugs = [item["slug"] for item in resp.data["results"]]
        self.assertEqual(slugs, [self.public_post.slug])

        resp = self.client.get("/blog/api/posts/?tag=bogleheads")
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        slugs = [item["slug"] for item in resp.data["results"]]
        self.assertEqual(slugs, [self.public_post.slug])


class SyncBlogPostsCommandTests(TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name)

    def write_post(self, name: str, contents: str) -> Path:
        path = self.directory / name
        path.write_text(contents, encoding="utf-8")
        return path

    def sync(self, **kwargs) -> str:
        out = StringIO()
        call_command(
            "sync_blog_posts", path=str(self.directory), stdout=out, stderr=out, **kwargs
        )
        return out.getvalue()

    def test_imports_post_with_front_matter_as_draft(self):
        self.write_post(
            "rtk_query.md",
            "---\n"
            "title: How RTK Query Refreshes JWTs\n"
            "slug: rtk-query-401-refresh\n"
            "excerpt: The baseQuery retries once after a 401.\n"
            "categories: [Engineering, React]\n"
            "tags: [rtk-query, jwt]\n"
            "---\n"
            "# How RTK Query Refreshes JWTs\n\nThe body.\n",
        )

        self.sync()

        post = Post.objects.get(slug="rtk-query-401-refresh")
        self.assertEqual(post.title, "How RTK Query Refreshes JWTs")
        self.assertEqual(post.excerpt, "The baseQuery retries once after a 401.")
        self.assertIn("The body.", post.body_markdown)
        self.assertNotIn("title:", post.body_markdown)
        self.assertIsNone(post.published_at)
        self.assertEqual(
            sorted(post.categories.values_list("slug", flat=True)),
            ["engineering", "react"],
        )
        self.assertEqual(
            sorted(post.tags.values_list("slug", flat=True)), ["jwt", "rtk-query"]
        )

    def test_publish_flag_and_front_matter_date_publish_the_post(self):
        self.write_post("flagged.md", "# Flagged\n\nBody.\n")
        self.write_post(
            "dated.md",
            "---\npublished_at: 2026-07-01\n---\n# Dated\n\nBody.\n",
        )

        self.sync(publish=True)

        self.assertIsNotNone(Post.objects.get(slug="flagged").published_at)
        dated = Post.objects.get(slug="dated")
        self.assertIsNotNone(dated.published_at)
        self.assertEqual(dated.published_at.date().isoformat(), "2026-07-01")

    def test_derives_title_and_slug_without_front_matter(self):
        self.write_post("INDEX_FUNDS_101.md", "# Index Funds 101\n\nFirst para.\n")

        self.sync()

        post = Post.objects.get(slug="index-funds-101")
        self.assertEqual(post.title, "Index Funds 101")
        self.assertEqual(post.excerpt, "First para.")

    def test_reimport_updates_in_place_and_keeps_published_at(self):
        path = self.write_post("post.md", "# Original\n\nOriginal body.\n")
        self.sync(publish=True)
        published_at = Post.objects.get(slug="post").published_at

        path.write_text("# Revised\n\nRevised body.\n", encoding="utf-8")
        output = self.sync()

        self.assertEqual(Post.objects.count(), 1)
        post = Post.objects.get(slug="post")
        self.assertEqual(post.title, "Revised")
        self.assertIn("Revised body.", post.body_markdown)
        self.assertEqual(post.published_at, published_at)
        self.assertIn("updated post.md -> post", output)

    def test_dry_run_writes_nothing(self):
        self.write_post("post.md", "# Draft\n\nBody.\n")

        output = self.sync(dry_run=True)

        self.assertFalse(Post.objects.exists())
        self.assertIn("created post.md -> post", output)
        self.assertIn("would sync", output)

    def test_invalid_front_matter_fails_the_command(self):
        self.write_post("broken.md", "---\ntitle: Broken\n# Body\n")

        with self.assertRaises(CommandError):
            self.sync()
        self.assertFalse(Post.objects.exists())

    def test_unsupported_front_matter_key_fails_the_command(self):
        self.write_post("odd.md", "---\nauthor: someone\n---\n# Odd\n\nBody.\n")

        with self.assertRaises(CommandError):
            self.sync()
        self.assertFalse(Post.objects.exists())

    def test_missing_directory_raises(self):
        with self.assertRaises(CommandError):
            call_command("sync_blog_posts", path=str(self.directory / "nope"))

    def test_excerpt_skips_the_version_stamp(self):
        self.write_post(
            "stamped.md",
            f"# Stamped\n\n{STAMP}\n\n{SOURCE_LINE}\n\n## Overview\n\nReal prose.\n",
        )

        self.sync()

        self.assertEqual(Post.objects.get(slug="stamped").excerpt, "Real prose.")

    def test_version_stamp_supplies_the_publication_date(self):
        self.write_post("stamped.md", f"# Stamped\n\n{STAMP}\n\n## Overview\n\nBody.\n")
        self.write_post("bare.md", "# Bare\n\nBody.\n")

        self.sync()

        self.assertEqual(
            Post.objects.get(slug="stamped").published_at.date().isoformat(),
            "2026-07-19",
        )
        # No stamp and no --publish leaves the post a draft rather than
        # publishing it by accident.
        self.assertIsNone(Post.objects.get(slug="bare").published_at)

    def test_stamp_never_moves_an_already_published_date(self):
        original = timezone.now()
        Post.objects.create(
            title="Live",
            slug="live",
            body_markdown="Live body.",
            published_at=original,
        )
        self.write_post("live.md", f"# Live\n\n{STAMP}\n\n## Overview\n\nBody.\n")

        self.sync()

        post = Post.objects.get(slug="live")
        self.assertEqual(post.published_at, original)
        self.assertIn("Body.", post.body_markdown)

    def test_leaves_posts_with_no_file_untouched(self):
        # The live "react" case: a post managed by hand, with no Markdown
        # source. An import must not delete or unpublish it.
        published_at = timezone.now()
        Post.objects.create(
            title="React",
            slug="react",
            body_markdown="Hand-written.",
            published_at=published_at,
        )
        self.write_post("other.md", "# Other\n\nBody.\n")

        self.sync()

        untouched = Post.objects.get(slug="react")
        self.assertEqual(untouched.title, "React")
        self.assertEqual(untouched.body_markdown, "Hand-written.")
        self.assertEqual(untouched.published_at, published_at)

    def test_duplicate_slugs_fail_before_anything_is_written(self):
        self.write_post("first.md", "---\nslug: same\n---\n# First\n\nBody.\n")
        self.write_post("second.md", "---\nslug: same\n---\n# Second\n\nBody.\n")

        with self.assertRaises(CommandError):
            self.sync()
        self.assertFalse(Post.objects.exists())

    def test_reimport_of_unchanged_files_changes_nothing(self):
        self.write_post("post.md", f"# Post\n\n{STAMP}\n\n## Overview\n\nBody.\n")
        self.sync()
        before = {
            (p.slug, p.title, p.excerpt, p.published_at, p.body_markdown)
            for p in Post.objects.all()
        }

        output = self.sync()

        after = {
            (p.slug, p.title, p.excerpt, p.published_at, p.body_markdown)
            for p in Post.objects.all()
        }
        self.assertEqual(before, after)
        self.assertIn("0 created, 1 updated", output)


class SyncBlogPostsSourceTests(TestCase):
    """The two built-in directories, and the rules that differ between them."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.journal = root / "journal"
        self.learning_topics = root / "learning-topics"
        self.journal.mkdir()
        self.learning_topics.mkdir()

        patcher = patch(
            "blog.management.commands.sync_blog_posts.default_sources",
            return_value=[
                PostSource(directory=self.journal),
                PostSource(
                    directory=self.learning_topics,
                    category=LEARNING_TOPIC_CATEGORY,
                    strip_issue_number=True,
                ),
            ],
        )
        patcher.start()
        self.addCleanup(patcher.stop)

    def sync(self, **kwargs) -> str:
        out = StringIO()
        call_command("sync_blog_posts", stdout=out, stderr=out, **kwargs)
        return out.getvalue()

    def test_slug_comes_from_the_filename_and_drops_the_issue_number(self):
        (self.journal / "CLAUDE_CODE.md").write_text(
            "# What is Claude Code?\n\nBody.\n", encoding="utf-8"
        )
        (self.learning_topics / "111_THE_JWT_TRUST_BOUNDARY.md").write_text(
            "# The JWT trust boundary\n\nBody.\n", encoding="utf-8"
        )

        self.sync()

        # The journal slug tracks the filename, not the title -- which is what
        # keeps CLAUDE_CODE.md landing on the live "claude-code" post.
        self.assertTrue(Post.objects.filter(slug="claude-code").exists())
        # The issue number identifies the source, not the post, so it is kept
        # out of the public URL.
        self.assertTrue(Post.objects.filter(slug="the-jwt-trust-boundary").exists())

    def test_front_matter_slug_overrides_the_filename(self):
        (self.journal / "INDEX_FUNDS_101.md").write_text(
            "---\nslug: index-funds-rock\n---\n# Index Funds 101\n\nBody.\n",
            encoding="utf-8",
        )

        self.sync()

        self.assertTrue(Post.objects.filter(slug="index-funds-rock").exists())
        self.assertFalse(Post.objects.filter(slug="index-funds-101").exists())

    def test_learning_topics_are_filed_under_llm_notebook(self):
        (self.journal / "PILOT.md").write_text("# Pilot\n\nBody.\n", encoding="utf-8")
        (self.learning_topics / "111_A_TOPIC.md").write_text(
            "# A topic\n\nBody.\n", encoding="utf-8"
        )

        self.sync()

        topic = Post.objects.get(slug="a-topic")
        self.assertEqual(
            list(topic.categories.values_list("name", flat=True)),
            [LEARNING_TOPIC_CATEGORY],
        )
        # A journal post gets no category unless its front matter asks for one.
        self.assertFalse(Post.objects.get(slug="pilot").categories.exists())

    def test_a_category_added_by_hand_survives_the_next_import(self):
        (self.learning_topics / "111_A_TOPIC.md").write_text(
            "# A topic\n\nBody.\n", encoding="utf-8"
        )
        self.sync()

        extra = Category.objects.create(name="Technology", slug="technology")
        Post.objects.get(slug="a-topic").categories.add(extra)

        self.sync()

        self.assertEqual(
            sorted(
                Post.objects.get(slug="a-topic").categories.values_list(
                    "name", flat=True
                )
            ),
            [LEARNING_TOPIC_CATEGORY, "Technology"],
        )

    def test_duplicate_slug_across_the_two_directories_is_rejected(self):
        (self.journal / "TOPIC.md").write_text("# Topic\n\nBody.\n", encoding="utf-8")
        (self.learning_topics / "111_TOPIC.md").write_text(
            "# Topic\n\nBody.\n", encoding="utf-8"
        )

        with self.assertRaises(CommandError):
            self.sync()
        self.assertFalse(Post.objects.exists())


class ShippedBlogPostsTests(TestCase):
    """The real Markdown files that ship inside the app must stay importable."""

    def test_every_shipped_post_imports_and_is_categorised(self):
        out = StringIO()
        call_command("sync_blog_posts", stdout=out, stderr=out)

        journal, learning_topics = default_sources()
        expected = len(list(journal.directory.glob("*.md"))) + len(
            list(learning_topics.directory.glob("*.md"))
        )
        self.assertEqual(Post.objects.count(), expected)

        topics = Post.objects.filter(categories__name=LEARNING_TOPIC_CATEGORY)
        self.assertEqual(
            topics.count(), len(list(learning_topics.directory.glob("*.md")))
        )
        # Every post gets a real blurb, never its version stamp.
        for post in Post.objects.all():
            self.assertTrue(post.excerpt)
            self.assertNotIn("FattoreStreet @", post.excerpt)

