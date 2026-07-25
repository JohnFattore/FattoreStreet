import tempfile
from io import StringIO
from pathlib import Path

from django.core.management import call_command
from django.core.management.base import CommandError
from django.test import TestCase
from django.utils import timezone
from rest_framework import status

from blog.models import Category, Post, Tag
from tests.base import BaseAPITestCase


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

