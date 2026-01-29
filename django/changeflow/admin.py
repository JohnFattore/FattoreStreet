from django.contrib import admin
from .models import ChangeRequest

@admin.register(ChangeRequest)
class ChangeRequestAdmin(admin.ModelAdmin):
    list_display = ('title', 'status', 'priority', 'created_at')
    list_filter = ('status', 'priority')
    search_fields = ('title', 'description')
