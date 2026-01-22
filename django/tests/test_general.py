from django.apps import apps
from tests.base import BaseAPITestCase
from django.urls import reverse

class GeneralHealthTest(BaseAPITestCase):
    def test_apps_installed(self):
        # Verify critical apps are installed
        expected_apps = ['users', 'portfolio', 'restaurants', 'indexes', 'chatbot']
        for app in expected_apps:
            self.assertTrue(apps.is_installed(app), f"{app} is not installed")

    def test_admin_accessible(self):
        # Basic check that admin URL is resolvable
        try:
            url = reverse('admin:index')
            response = self.client.get(url)
            # 302 redirect to login is success for unauthenticated
            self.assertIn(response.status_code, [200, 302])
        except Exception as e:
            self.fail(f"Admin URL failed: {e}")