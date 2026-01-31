from django.contrib import admin
from simple_history.admin import SimpleHistoryAdmin
from .models import ChangeRequest

@admin.register(ChangeRequest)
class ChangeRequestAdmin(SimpleHistoryAdmin):
    list_display = ('title', 'status', 'priority', 'created_at')
    list_filter = ('status', 'priority')
    search_fields = ('title', 'description')
