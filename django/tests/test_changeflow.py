from django.urls import reverse
from rest_framework import status

from changeflow.models import ChangeRequest, Ticket
from tests.base import BaseAPITestCase


class ChangeRequestAPITests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = "/changeflow/api/requests/"
        self.payload = {
            "title": "Test Request",
            "description": "This is a test",
            "priority": "HIGH",
            "data": {"key": "value"},
        }

    def test_create_change_request(self):
        self.authenticate_client()
        response = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(ChangeRequest.objects.count(), 1)
        self.assertEqual(ChangeRequest.objects.get().title, "Test Request")

    def test_create_change_request_unauthenticated(self):
        response = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_get_change_requests(self):
        self.authenticate_client()
        ChangeRequest.objects.create(title="Request 1")
        ChangeRequest.objects.create(title="Request 2")
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)


class TicketCreateTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse("tickets")
        self.payload = {
            "title": "Profile page bug",
            "description": "The submit button is disabled after opening the modal twice.",
        }

    def test_authenticated_user_can_create_ticket(self):
        self.authenticate_client()
        response = self.client.post(self.url, self.payload, format="json")

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(Ticket.objects.count(), 1)
        ticket = Ticket.objects.get()
        self.assertEqual(ticket.title, self.payload["title"])
        self.assertEqual(ticket.user, self.user)
        self.assertEqual(ticket.status, "OPEN")

    def test_unauthenticated_user_cannot_create_ticket(self):
        response = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class UserDeactivationPolicyTests(BaseAPITestCase):
    def test_deactivated_user_cannot_obtain_jwt_token(self):
        self.user.is_active = False
        self.user.save(update_fields=["is_active"])

        response = self.client.post(
            reverse("token_obtain_pair"),
            {"username": "testuser", "password": "testpassword"},
            format="json",
        )

        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        self.assertIn("No active account found", str(response.data))

    def test_ticket_remains_after_user_deactivation(self):
        ticket = Ticket.objects.create(
            user=self.user,
            title="Feature request",
            description="Add export to CSV from account page.",
        )
        self.user.is_active = False
        self.user.save(update_fields=["is_active"])

        self.assertTrue(Ticket.objects.filter(id=ticket.id).exists())
        ticket.refresh_from_db()
        self.assertFalse(ticket.user.is_active)
