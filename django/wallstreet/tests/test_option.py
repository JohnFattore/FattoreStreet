from rest_framework.test import APITestCase
from rest_framework import status
from wallstreet.models import Option
from wallstreet.views import OptionsAPI
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
from wallstreet.helperFunctions import getSunday

class OptionCreateTest(APITestCase):
    def setUp(self):
        self.lastSunday = getSunday(0)
        self.nextSunday = getSunday(1)
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('options')
        # self.url = '/wallstreet/api/options/'
        self.view = OptionsAPI.as_view()

    def test_create_option(self):
        data = {'ticker': 'SPY', 'sunday': self.nextSunday}
        response = self.view(self.factory.post(self.url, data, format='json'))
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_list_options(self):
        request = self.factory.get(self.url)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_list_options_client(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)