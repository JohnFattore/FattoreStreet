---
name: django-tests
description: Write Django tests using unittest, DRF APITestCase, and mock. Use when the user asks to create, add, or write tests for Django views, serializers, models, or backend API code in django/.
---

# Django Test Writing

## Stack

Django unittest + DRF `APITestCase` + `unittest.mock` (no pytest, no factory-boy)

## File Conventions

- Test files go in `django/tests/` named `test_<app_name>.py`
- Base class: `django/tests/base.py`
- Run: `cd django && python manage.py test`
- Run single file: `python manage.py test tests.test_portfolio`

## Base Test Class

Always extend `BaseAPITestCase` from `tests/base.py`:

```python
from tests.base import BaseAPITestCase

class MyViewTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        # self.user -- pre-created test user
        # self.factory -- APIRequestFactory instance
        # self.client -- APIClient instance (from APITestCase)
```

Helpers:

- `self.authenticate_client()` -- calls `self.client.force_authenticate(user=self.user)`
- `self.unauthenticate_client()` -- de-authenticates the client

## Test Patterns

### Unit Tests (RequestFactory)

Test views directly without full HTTP stack:

```python
from myapp.views import MyViewSet

def test_list_returns_data(self):
    request = self.factory.get("/api/endpoint/")
    force_authenticate(request, user=self.user)
    view = MyViewSet.as_view({"get": "list"})
    response = view(request)
    self.assertEqual(response.status_code, 200)
```

### Integration Tests (APIClient)

Test the full request cycle through URL routing:

```python
def test_list_endpoint(self):
    self.authenticate_client()
    response = self.client.get("/api/endpoint/")
    self.assertEqual(response.status_code, 200)
    self.assertIsInstance(response.json(), list)
```

### Mocking External APIs

Use `@patch()` for yfinance, FRED, Google AI, or any external service:

```python
from unittest.mock import patch, MagicMock

@patch("myapp.views.yf.download")
def test_with_mocked_yfinance(self, mock_download):
    mock_download.return_value = MagicMock()
    self.authenticate_client()
    response = self.client.get("/api/market-data/")
    self.assertEqual(response.status_code, 200)
    mock_download.assert_called_once()
```

### Auth Testing

Always test both authenticated and unauthenticated paths:

```python
def test_requires_auth(self):
    self.unauthenticate_client()
    response = self.client.get("/api/protected/")
    self.assertIn(response.status_code, [401, 403])

def test_authenticated_access(self):
    self.authenticate_client()
    response = self.client.get("/api/protected/")
    self.assertEqual(response.status_code, 200)
```

## Workflow

1. **Identify the target** -- view, serializer, or model to test
2. **Pick test type** -- unit (RequestFactory for isolated view logic) vs integration (APIClient for full routing)
3. **Set up data** -- create model instances directly in `setUp()` (no factories)
4. **Mock externals** -- `@patch()` any calls to yfinance, FRED, Google AI, or third-party APIs
5. **Test auth** -- every protected endpoint needs both authenticated and unauthenticated assertions
6. **Assert** -- check status codes, JSON structure, expected values, and side effects
7. **Run** -- `cd django && python manage.py test tests.test_<module>`
