from django.contrib.admin.sites import AdminSite
from django.contrib.auth.models import User
from django.urls import reverse
from rest_framework import status
from tests.base import BaseAPITestCase
from users.admin import (
    DeactivationFirstUserAdmin,
    deactivate_selected_users,
    reactivate_selected_users,
)
from users.services import deactivate_user
from users.views import UserCreateView


class UserTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('users')
        self.view = UserCreateView.as_view()

    def test_create_user(self):
        data = {'username': 'UnitTest', 'password': 'password', 'email': 'test@test.com'}
        request = self.factory.post(self.url, data, format='json')
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_create_user_client(self):
        data = {'username': 'UnitTest', 'password': 'password', 'email': 'test@test.com'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_create_duplicate_username(self):
        """BaseAPITestCase already creates 'testuser'; duplicating should fail."""
        data = {'username': 'testuser', 'password': 'password', 'email': 'dup@test.com'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_user_missing_password(self):
        data = {'username': 'NoPassword'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_register_login_integration(self):
        register_data = {'username': 'IntegrationTestUser', 'password': 'password', 'email': 'test@test.com'}
        response = self.client.post(reverse('users'), register_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

        login_data = {'username': 'IntegrationTestUser', 'password': 'password'}
        response = self.client.post(reverse('token_obtain_pair'), login_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access', response.data)
        self.assertIn('refresh', response.data)
        token = response.data['access']

        # Use the token to access a protected endpoint
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token}')
        response = self.client.get(reverse('accounts'))
        self.assertEqual(response.status_code, status.HTTP_200_OK)


class UserServiceTests(BaseAPITestCase):
    def test_deactivate_user_marks_inactive_and_returns_user(self):
        result = deactivate_user(self.user)

        self.assertIs(result, self.user)
        self.user.refresh_from_db()
        self.assertFalse(self.user.is_active)

    def test_deactivate_user_only_persists_is_active(self):
        """save(update_fields=["is_active"]) must not flush other dirty fields."""
        self.user.first_name = "NotSaved"
        deactivate_user(self.user)

        self.user.refresh_from_db()
        self.assertEqual(self.user.first_name, "")
        self.assertFalse(self.user.is_active)


class UserAdminTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.model_admin = DeactivationFirstUserAdmin(User, AdminSite())
        self.superuser = User.objects.create_superuser(username="admin", password="adminpassword")
        self.request = self.factory.get("/admin/auth/user/")
        self.request.user = self.superuser

    def test_deactivate_selected_users_action(self):
        deactivate_selected_users(self.model_admin, self.request, User.objects.filter(pk=self.user.pk))

        self.user.refresh_from_db()
        self.assertFalse(self.user.is_active)

    def test_reactivate_selected_users_action(self):
        deactivate_user(self.user)

        reactivate_selected_users(self.model_admin, self.request, User.objects.filter(pk=self.user.pk))

        self.user.refresh_from_db()
        self.assertTrue(self.user.is_active)

    def test_delete_selected_action_removed(self):
        actions = self.model_admin.get_actions(self.request)

        self.assertNotIn("delete_selected", actions)
        self.assertIn("deactivate_selected_users", actions)
        self.assertIn("reactivate_selected_users", actions)

    def test_has_delete_permission_always_false(self):
        self.assertFalse(self.model_admin.has_delete_permission(self.request))
        self.assertFalse(self.model_admin.has_delete_permission(self.request, obj=self.user))
