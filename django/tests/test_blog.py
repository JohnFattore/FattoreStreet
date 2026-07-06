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

