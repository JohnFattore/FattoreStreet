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
        # Create a user for authentication
        self.option = Option.objects.create(ticker='SPY', sunday='2024-03-03')
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('options')
        # self.url = '/wallstreet/api/options/'
        self.view = OptionListCreateView.as_view()

    # unit test
    def test_option_create(self):
        data = {'ticker': 'SPY', 'sunday': '2024-03-03'}
        response = self.view(self.factory.post(self.url, data, format='json'))
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
    # integration test
    def test_option_create_client(self):
        data = {'ticker': 'SPY', 'sunday': '2024-03-03'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)