from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from wallstreet.models import Selection, Option, Result
from wallstreet.tasks import endWeek, startWeek
from wallstreet.helperFunctions import getSunday
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
from rest_framework import status

class SelectionCreateTest(APITestCase):
    def setUp(self):
        self.sunday = getSunday(0)
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        startWeek(sunday=self.sunday)
        self.options = Option.objects.filter(sunday=self.sunday)
        Selection.objects.create(option=self.options[0], user=self.user)
        Selection.objects.create(option=self.options[1], user=self.user)
        Selection.objects.create(option=self.options[2], user=self.user)
        endWeek(sunday=self.sunday)


    def test_list_selections_no_params(self):
        data = { }
        request = self.factory.get(self.url, data)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_list_selections_sunday(self):
        data = {'sunday': self.nextSunday}
        request = self.factory.get(self.url, data)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_list_selections_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)