from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from django.contrib.auth.models import User

from .services import deactivate_user


@admin.action(description="Deactivate selected users")
def deactivate_selected_users(_modeladmin, _request, queryset):
    for user in queryset:
        deactivate_user(user)


@admin.action(description="Reactivate selected users")
def reactivate_selected_users(_modeladmin, _request, queryset):
    queryset.update(is_active=True)


admin.site.unregister(User)


@admin.register(User)
class DeactivationFirstUserAdmin(UserAdmin):
    actions = [deactivate_selected_users, reactivate_selected_users]

    def get_actions(self, request):
        actions = super().get_actions(request)
        actions.pop("delete_selected", None)
        return actions

    def has_delete_permission(self, request, obj=None):
        return False
