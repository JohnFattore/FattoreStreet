from rest_framework import serializers

from .models import Category, Post, Tag


class CategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = Category
        fields = ["name", "slug"]


class TagSerializer(serializers.ModelSerializer):
    class Meta:
        model = Tag
        fields = ["name", "slug"]


class PostListSerializer(serializers.ModelSerializer):
    categories = CategorySerializer(many=True, read_only=True)
    tags = TagSerializer(many=True, read_only=True)
    author_username = serializers.SerializerMethodField()

    class Meta:
        model = Post
        fields = [
            "title",
            "slug",
            "excerpt",
            "cover_image_url",
            "published_at",
            "created_at",
            "updated_at",
            "author_username",
            "categories",
            "tags",
        ]

    def get_author_username(self, obj: Post) -> str | None:
        if not obj.author_id:
            return None
        return getattr(obj.author, "username", None)


class PostDetailSerializer(serializers.ModelSerializer):
    categories = CategorySerializer(many=True, read_only=True)
    tags = TagSerializer(many=True, read_only=True)
    author_username = serializers.SerializerMethodField()

    class Meta:
        model = Post
        fields = [
            "title",
            "slug",
            "excerpt",
            "body_markdown",
            "cover_image_url",
            "published_at",
            "created_at",
            "updated_at",
            "author_username",
            "categories",
            "tags",
        ]

    def get_author_username(self, obj: Post) -> str | None:
        if not obj.author_id:
            return None
        return getattr(obj.author, "username", None)

