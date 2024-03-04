from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from wallstreet.models import Option
from wallstreet.views import OptionListCreateView
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse

class OptionCreateTest(APITestCase):
    def setUp(self):
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('options')
        # self.url = '/wallstreet/api/options/'
        self.view = OptionListCreateView.as_view()

    def test_create_option(self):
        data = {'ticker': 'SPY', 'sunday': '2024-03-03'}
        response = self.view(self.factory.post(self.url, data, format='json'))
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_list_options(self):
        request = self.factory.get(self.url)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_list_options_client(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)