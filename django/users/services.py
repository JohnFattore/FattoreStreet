from django.contrib.auth.models import User


def deactivate_user(user: User) -> User:
    user.is_active = False
    user.save(update_fields=["is_active"])
    return user
