from django.contrib import admin

from .models import Recommendation


@admin.register(Recommendation)
class RecommendationAdmin(admin.ModelAdmin):
    list_display = ("title", "artist", "type", "year", "created_at")
    list_filter = ("type",)
    search_fields = ("title", "artist")
