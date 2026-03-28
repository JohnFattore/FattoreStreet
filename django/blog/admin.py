from django.contrib import admin

from .models import Category, Post, Tag


@admin.register(Category)
class CategoryAdmin(admin.ModelAdmin):
    list_display = ["name", "slug"]
    search_fields = ["name", "slug"]
    prepopulated_fields = {"slug": ("name",)}


@admin.register(Tag)
class TagAdmin(admin.ModelAdmin):
    list_display = ["name", "slug"]
    search_fields = ["name", "slug"]
    prepopulated_fields = {"slug": ("name",)}


@admin.register(Post)
class PostAdmin(admin.ModelAdmin):
    list_display = ["title", "published_at", "author"]
    list_filter = ["published_at", "categories", "tags"]
    search_fields = ["title", "slug", "excerpt", "body_markdown"]
    prepopulated_fields = {"slug": ("title",)}
    autocomplete_fields = ["author"]
    filter_horizontal = ["categories", "tags"]

