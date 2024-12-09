from rest_framework import permissions

class IsOwner(permissions.BasePermission):
    # permissions are only allowed to the owner of the asset.
    def has_object_permission(self, request, view, obj):
        return obj.user == request.user