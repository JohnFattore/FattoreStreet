from rest_framework.test import APITestCase
from rest_framework import status
from wallstreet.models import Option
from wallstreet.views import OptionsAPI
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
from wallstreet.helperFunctions import getSunday

class OptionTest(APITestCase):
    def setUp(self):
        self.lastSunday = getSunday(0)
        self.nextSunday = getSunday(1)
        self.option = Option.objects.create(ticker="V", sunday=self.nextSunday, benchmark=False)
        self.option2 = Option.objects.create(ticker="VTI", sunday=self.nextSunday, benchmark=False)
        self.option3 = Option.objects.create(ticker="AAPL", sunday=self.nextSunday, benchmark=False)
        self.option4 = Option.objects.create(ticker="MSFT", sunday=self.nextSunday, benchmark=False)
        self.option5 = Option.objects.create(ticker="LLY", sunday=self.lastSunday, benchmark=False)
        self.option6 = Option.objects.create(ticker="SPY", sunday=self.nextSunday, benchmark=True)
        self.factory = APIRequestFactory()
        self.client = APIClient()
        self.url = reverse('options')
        # self.url = '/wallstreet/api/options/'
        self.view = OptionsAPI.as_view()

    def test_list_options_no_params(self):
        request = self.factory.get(self.url, format='json')
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        # response.render()
        self.assertEqual(len(response.data), 6)

    def test_list_options_sunday(self):
        data = {'sunday': self.nextSunday}
        request = self.factory.get(self.url, data)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 5)

    def test_list_options_benchmark(self):
        data = {'benchmark': False}
        request = self.factory.get(self.url, data)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 5)

    def test_list_options_benchmark_and_sunday(self):
        data = {'sunday': self.nextSunday, 'benchmark': False}
        request = self.factory.get(self.url, data)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 4)

    def test_list_options_client(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)