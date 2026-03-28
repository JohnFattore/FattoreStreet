from django.shortcuts import get_object_or_404
from rest_framework import filters, generics
from rest_framework.pagination import PageNumberPagination
from rest_framework.permissions import AllowAny

from .models import Category, Post, Tag
from .serializers import (
    CategorySerializer,
    PostDetailSerializer,
    PostListSerializer,
    TagSerializer,
)


class BlogPagination(PageNumberPagination):
    page_size = 10
    page_size_query_param = "page_size"
    max_page_size = 50


class PostListView(generics.ListAPIView):
    permission_classes = [AllowAny]
    serializer_class = PostListSerializer
    pagination_class = BlogPagination
    filter_backends = [filters.SearchFilter]
    search_fields = ["title", "excerpt", "body_markdown"]

    def get_queryset(self):
        qs = (
            Post.objects.public()
            .select_related("author")
            .prefetch_related("categories", "tags")
        )

        category_slug = self.request.query_params.get("category")
        if category_slug:
            qs = qs.filter(categories__slug=category_slug)

        tag_slug = self.request.query_params.get("tag")
        if tag_slug:
            qs = qs.filter(tags__slug=tag_slug)

        return qs.distinct()


class PostDetailView(generics.RetrieveAPIView):
    permission_classes = [AllowAny]
    serializer_class = PostDetailSerializer
    lookup_field = "slug"

    def get_object(self):
        qs = (
            Post.objects.public()
            .select_related("author")
            .prefetch_related("categories", "tags")
        )
        return get_object_or_404(qs, slug=self.kwargs["slug"])


class CategoryListView(generics.ListAPIView):
    permission_classes = [AllowAny]
    serializer_class = CategorySerializer
    queryset = Category.objects.all()


class TagListView(generics.ListAPIView):
    permission_classes = [AllowAny]
    serializer_class = TagSerializer
    queryset = Tag.objects.all()

