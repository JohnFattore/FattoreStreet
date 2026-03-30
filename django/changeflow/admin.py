from django.contrib import admin
from django.contrib.admin import SimpleListFilter
from simple_history.admin import SimpleHistoryAdmin
from .models import ChangeRequest, Ticket


class ChangeRequestCompletionFilter(SimpleListFilter):
    title = "view"
    parameter_name = "completion"

    def lookups(self, request, model_admin):
        return (("active", "Active (hide completed)"),)

    def queryset(self, request, queryset):
        if self.value() == "active":
            return queryset.exclude(status="COMPLETED")
        return queryset


@admin.register(ChangeRequest)
class ChangeRequestAdmin(SimpleHistoryAdmin):
    list_display = ('id', 'title', 'status', 'priority', 'created_at')
    list_filter = (ChangeRequestCompletionFilter, 'status', 'priority')
    search_fields = ('title', 'description', 'solution')


@admin.register(Ticket)
class TicketAdmin(SimpleHistoryAdmin):
    list_display = ("id", "title", "status", "user", "created_at")
    list_filter = ("status",)
    search_fields = ("title", "description", "user__username")